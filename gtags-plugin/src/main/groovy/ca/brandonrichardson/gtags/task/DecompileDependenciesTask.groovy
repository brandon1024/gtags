package ca.brandonrichardson.gtags.task

import ca.brandonrichardson.gtags.decompiler.JarDecompiler
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DecompileDependenciesTask extends DefaultTask {

    private static final Logger logger = Logging.getLogger(DecompileDependenciesTask)

    @Input
    Collection<String> includedModules

    @Input
    Collection<String> excludedModules

    @Input
    Collection<String> excludedProjects

    @Input
    Collection<String> configs

    @Input
    int concurrency

    @InputDirectory
    File taskOutputDir

    @TaskAction
    def decompile() {
        logger.info "listing dependencies for project '${project.name}' and any subprojects"

        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be a positive integer but was ${concurrency}")
        }

        int workCompleted = concurrency > 1 ? asynchronousDecompile() : synchronousDecompile()
        setDidWork(workCompleted > 0)
    }

    /**
     * Decompile project (and subproject) dependencies synchronously.
     */
    def synchronousDecompile() {
        def dependencies = new HashSet<File>()
        int dependencyCount = getJarDependenciesForProject(project, dependencies)

        logger.lifecycle "found ${dependencyCount} distinct jar dependencies for project '${project.name}'"

        dependencies.each { File jar ->
            try {
                decompileDependency(jar)
            } finally {
                dependencyCount--
                logger.debug "${dependencyCount} of ${dependencies.size()} decompilation tasks remain"
            }
        }

        return dependencies.size()
    }

    /**
     * Decompile project (and subproject) dependencies asynchronously across `concurrency - 1` threads in a fixed thread pool.
     *
     * While traversing the project tree (preorder), dependencies from the desired configuration are collected into a set. When a dependency
     * hasn't been seen before, an asynchronous task is kicked off to decompile classes in that jar. Once the entire project tree has been
     * traversed and all dependencies have been seen, the current thread will await for all tasks to complete.
     * */
    def asynchronousDecompile() {
        final ExecutorService threadPool = Executors.newFixedThreadPool(concurrency - 1)

        // a phaser is used to wait for all tasks to complete and report on the number of tasks remaining
        final Phaser phaser = new Phaser(1)
        int dependencyCount = getJarDependenciesForProject(project, Collections.synchronizedSet(new HashSet<File>())) { File jar ->
            phaser.register()
            threadPool.execute { ->
                try {
                    decompileDependency(jar)
                } finally {
                    phaser.arriveAndDeregister()
                }
            }
        }

        logger.debug "successfully submitted ${dependencyCount} decompilation tasks to thread pool for execution; awaiting completion"
        logger.lifecycle "found ${dependencyCount} distinct jar dependencies for project '${project.name}'"

        threadPool.shutdown()

        // wait for all decompilation tasks to complete, periodically reporting the number of tasks remaining
        final int phaseNumber = phaser.arrive()
        boolean arrived = false
        do {
            try {
                phaser.awaitAdvanceInterruptibly(phaseNumber, 1L, TimeUnit.SECONDS)
                arrived = true
            } catch (final TimeoutException ignored) {
                logger.debug "${phaser.getRegisteredParties()} of ${dependencyCount} decompilation tasks remain"
            } catch (final InterruptedException ignored) {
                threadPool.shutdownNow()
                Thread.currentThread().interrupt()
            }
        } while (!arrived)

        // shutdown the thread pool
        try {
            if (!threadPool.awaitTermination(60L, TimeUnit.SECONDS)) {
                threadPool.shutdownNow()
            }
        } catch (final InterruptedException ignored) {
            threadPool.shutdownNow()
            Thread.currentThread().interrupt()
        }

        return dependencyCount
    }

    /**
     * For the project `forProject`, collect into the `dependencies` set all Jar dependencies of the project and any subprojects
     * (recursively), invoking the provided callback when new dependencies are encountered.
     *
     * Returns the number of dependencies found.
     *
     * A project may be excluded through `excludedProjects` provided as task input. If excluded, returns immediately and does not traverse
     * through any subprojects. Individual dependencies may be included or excluded through the appropriate collection provided as task
     * input.
     *
     * If `forProject` does not have a plugin of type `JavaPlugin`, the project is skipped but this method will continue and traverse through
     * any subprojects.
     *
     * Dependencies are resolved from configurations `configs` provided as task input.
     */
    def getJarDependenciesForProject(final Project forProject, final Set<File> dependencies, final Closure callback = { File jar -> }) {
        if (projectExcluded(forProject)) {
            logger.debug "skipping excluded project '${forProject.name}' and it's subprojects"
            return dependencies.size()
        }

        if (forProject.plugins.hasPlugin(JavaPlugin)) {
            configs.each { String config ->
                forProject.configurations.getByName(config) { Configuration configuration ->
                    final Set<ResolvedDependency> selectedDependencies = configuration.resolvedConfiguration.firstLevelModuleDependencies
                            .findAll { ResolvedDependency dependency -> !dependencyModuleExcluded(dependency) }

                    final List<ResolvedArtifact> moduleArtifacts = selectedDependencies
                            .collectMany { ResolvedDependency dependency -> dependency.allModuleArtifacts }
                            .findAll { ResolvedArtifact artifact -> artifact.type == 'jar' }
                            .findAll { ResolvedArtifact artifact -> !dependencyModuleExcluded(artifact) }

                    moduleArtifacts.each { ResolvedArtifact artifact ->
                        logger.trace "found jar dependency '${artifact.file}' for project ${forProject.name}"

                        // if we haven't seen this file before, invoke callback
                        if (dependencies.add(artifact.file)) {
                            logger.trace "found new dependency '${artifact.file}' in project '${forProject.name}'"
                            callback(artifact.file)
                        }
                    }
                }
            }
        } else {
            logger.info "skipping project '${forProject.name}' which is not a Java project (does not have the Java plugin)"
        }

        logger.debug "recursing into subprojets of project '${forProject.name}', if they exist"

        forProject.subprojects { Project subproject ->
            logger.debug "recursing into subprojet '${subproject.name}' of project '${forProject.name}'"
            getJarDependenciesForProject(subproject, dependencies, callback)
        }

        return dependencies.size()
    }

    /**
     * Check if the given dependency module should be excluded.
     * A module is excluded if:
     * - a list of included modules is given as task input and the dependency `moduleGroup:name` does not match a string in that list, or
     * - a list of excluded modules is given as task input and the dependency `moduleGroup:name` matches a string in that list
     */
    def dependencyModuleExcluded(final ResolvedDependency dependency) {
        return dependencyModuleExcluded("${dependency.moduleGroup}:${dependency.moduleName}".toString())
    }

    /**
     * Check if the given artifact module should be excluded.
     * A module is excluded if:
     * - a list of included modules is given as task input and the dependency `moduleGroup:name` does not match a string in that list, or
     * - a list of excluded modules is given as task input and the dependency `moduleGroup:name` matches a string in that list
     * */
    def dependencyModuleExcluded(final ResolvedArtifact artifact) {
        final String moduleIdentifier = artifact.moduleVersion.toString()
        return dependencyModuleExcluded(moduleIdentifier.substring(0, moduleIdentifier.lastIndexOf(':')))
    }

    /**
     * Check if the given project should be excluded. A project is excluded if the project's name matches a string in `excludedProjects`
     * provided through task input.
     */
    def projectExcluded(final Project currentProject) {
        return excludedProjects.contains(currentProject.name)
    }

    def dependencyModuleExcluded(final String moduleIdentifier) {
        if (includedModules) {
            return !includedModules.stream()
                    .anyMatch { String pattern -> moduleIdentifier.matches(pattern) }
        }

        return excludedModules.stream()
                .anyMatch { String pattern -> moduleIdentifier.matches(pattern) }
    }

    def decompileDependency(final File jar) {
        logger.debug "decompiling classes in jar '${jar}' into directory '${taskOutputDir}'"

        try {
            def decompiler = new JarDecompiler(taskOutputDir)
            decompiler.addSource(jar)
            decompiler.decompileContext()
        } catch (final Exception e) {
            logger.error "failed to decompile '${jar}': ${e.getMessage()}; enable debug logs for details"
            logger.debug "failed to decompile '${jar}'", e
        }
    }
}
