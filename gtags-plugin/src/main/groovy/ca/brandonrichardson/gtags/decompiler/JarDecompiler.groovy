package ca.brandonrichardson.gtags.decompiler

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler

class JarDecompiler extends BaseDecompiler {

    JarDecompiler(final File destination) {
        super(new DefaultBytecodeProvider(), new DefaultResultSaver(destination),
                new HashMap<String, Object>(), new FernflowerSlf4jLogger())
    }
}
