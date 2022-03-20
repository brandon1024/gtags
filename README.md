# gtags
`gtags` is a dependency decompiler plugin for Gradle projects. The plugin traverses the dependency graph for a particular project configuration and decompiles all class files for all libraries in that configuration. Output from the plugin can be used as a developer reference while working on Java-based projects, or can be ingested with external tools like [universal-ctags](https://github.com/universal-ctags/ctags) to generate tag files for enabling rudimentary completion and navigation within Vim.

`gtags` supports single- and multi-module Gradle projects. When collecting dependencies for decompilation, the plugin will recurse into subprojects. Projects that do not have the `java` (or `java-library`) plugin enabled are ignored.

## Usage
Usage is simple:
```groovy
plugins {
    id 'java'
    id 'ca.brandonrichardson.gtags' version '0.0.1'
}

gtags {
    /**
     * A list of dependency module patterns that should be included for decompilation.
     * If defined, `excludedModules` is ignored.
     * Optional. Default: []
     */
    includedModules = ['com.google.guava:guava', 'org.springframework:.*']
    
    /**
     * A list of dependency module patterns that should be excluded for decompilation.
     * Only respected if `includedModules` is unset.
     * Optional. Default: []
     */
    excludeModules = ['com.google.guava:guava', 'org.springframework:.*']
    
    /**
     * A list of project names that should be excluded for decompilation.
     * Optional. Default: []
     */
    excludeProjects = ['my-project-1', 'my-project-2']
    
    /**
     * A list of project configurations from which dependencies are collected.
     * Optional. Default: ['compileClasspath']
     */
    configs = ['runtimeClasspath', 'compileClasspath']
    
    /**
     * The number of threads to use for decompilation. Can improve performance when
     * the dependency graph large.
     * Optional. Default: 1
     */
    concurrency = 4
    
    /**
     * Output directory path. Directory structure is created if it does not exist.
     * Optional. Default: "${project.buildDir}/gtags"
     */
    outputDir = "${System.properties['user.home']}/gtags"
}
```

## Building
```
$ ./gradlew assemble
```

## Special Thanks
Special thanks to [fesh0r](https://github.com/fesh0r) for hosting a fork of the upstream [Fernflower](https://github.com/JetBrains/intellij-community/tree/master/plugins/java-decompiler/engine) project. The [intelliJ-community](https://github.com/universal-ctags/ctags) project, which encompasses the fernflower project, is _very_ large and including the whole project would be a tremendous pain to work with. Additionally, the library is not published anywhere useful, so I'm not able to add it as a dependency of this project.

This project uses [Fernflower](https://github.com/JetBrains/intellij-community/tree/master/plugins/java-decompiler/engine) for bytecode decompilation. The fernflower project is maintained under the umbrella of the JetBrains IntelliJ Community Edition IDE. The project is licensed under the Apache License 2.0.

## License
This project is licensed under the Apache License 2.0.
