package ca.brandonrichardson.gtags

import ca.brandonrichardson.gtags.task.DecompileDependenciesTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class GTagsPlugin implements Plugin<Project> {

    @Override
    void apply(final Project project) {
        final GTagsPluginExtension extension = project.extensions.create('gtags', GTagsPluginExtension)

        project.tasks.register('decompileDependencies', DecompileDependenciesTask) {
            final File outputDirectory = new File(extension.outputDir.getOrElse("${project.buildDir}/gtags/"))
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }

            includedModules = extension.includedModules.getOrElse([])
            excludedModules = extension.excludeModules.getOrElse([])
            excludedProjects = extension.excludeProjects.getOrElse([])
            configs = extension.configs.getOrElse(['compileClasspath'])
            concurrency = extension.concurrency.getOrElse(1)
            taskOutputDir = outputDirectory
        }
    }
}
