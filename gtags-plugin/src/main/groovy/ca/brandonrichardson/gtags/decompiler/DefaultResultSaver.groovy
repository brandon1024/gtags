package ca.brandonrichardson.gtags.decompiler

import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.jetbrains.java.decompiler.main.extern.IResultSaver

import java.nio.charset.StandardCharsets
import java.util.jar.Manifest

class DefaultResultSaver implements IResultSaver {

    private static final Logger logger = Logging.getLogger(DefaultResultSaver)

    private final File destination

    DefaultResultSaver(final File destination) {
        this.destination = destination
    }

    @Override
    void saveFolder(final String path) {
        final File dir = getAbsolutePath(destination, path)

        // try to create the directory, throwing an exception if it couldn't be created and `dir` exists and is a file
        if (!(dir.mkdirs() || dir.isDirectory())) {
            throw new RuntimeException("cannot create directory '${dir}'")
        }

        logger.debug "created directory '${dir}'"
    }

    @Override
    void copyFile(final String source, final String path, final String entryName) {
        // not required
    }

    @Override
    void saveClassFile(final String path, final String qualifiedName, final String entryName, final String content, final int[] mapping) {
        if (content == null) {
            return
        }

        final File dest = new File(getAbsolutePath(destination, path), entryName)
        final File parentDir = dest.getParentFile()
        if (!parentDir.mkdirs() && !parentDir.isDirectory()) {
            throw new RuntimeException("failed to create directory structure for '${dest}'")
        }

        try {
            Files.asCharSink(dest, StandardCharsets.UTF_8)
                    .write(content)
        } catch (final IOException ex) {
            throw new RuntimeException("cannot copy class file with entry name '${entryName}' to '${dest}'", ex)
        }

        logger.debug "saved class file content to '${dest}'"
    }

    @Override
    void createArchive(final String path, final String archiveName, final Manifest manifest) {
        // do not create archives
    }

    @Override
    void saveDirEntry(final String path, final String archiveName, final String entryName) {
        saveClassEntry(path, archiveName, null, entryName, null)
    }

    @Override
    void copyEntry(final String source, final String path, final String archiveName, final String entryName) {
        copyFile(source, path, entryName)
    }

    @Override
    void saveClassEntry(final String path, final String archiveName, final String qualifiedName, final String entryName, final String content) {
        saveClassFile(path, qualifiedName, entryName, content, new int[0])
    }

    @Override
    void closeArchive(final String path, final String archiveName) {
        // do not create archives
    }

    private static File getAbsolutePath(final File base, final String internalPath) {
        return new File(new File(base, internalPath).getAbsolutePath())
    }
}
