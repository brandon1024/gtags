package ca.brandonrichardson.gtags

import org.gradle.api.provider.Property

interface GTagsPluginExtension {

    Property<Collection<String>> getIncludedModules()
    Property<Collection<String>> getExcludeModules()
    Property<Collection<String>> getExcludeProjects()
    Property<Collection<String>> getConfigs()
    Property<Integer> getConcurrency()
    Property<String> getOutputDir()
}
