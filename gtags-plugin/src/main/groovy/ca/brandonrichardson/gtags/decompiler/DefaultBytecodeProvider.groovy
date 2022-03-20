package ca.brandonrichardson.gtags.decompiler

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import org.jetbrains.java.decompiler.util.InterpreterUtil

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class DefaultBytecodeProvider implements IBytecodeProvider {

    @Override
    byte[] getBytecode(final String externalPath, final String internalPath) throws IOException {
        final File file = new File(externalPath)
        if (internalPath == null) {
            return InterpreterUtil.getBytes(file)
        }

        try (final ZipFile archive = new ZipFile(file)) {
            final ZipEntry entry = archive.getEntry(internalPath)
            if (entry == null) {
                throw new IOException("could not find entry at internal path '${internalPath}' in archive '${externalPath}'")
            }

            if (entry.getSize() > Integer.MAX_VALUE) {
                throw new IOException("entry in zip file is too large: ${entry.getSize()}")
            }

            try (final InputStream stream = archive.getInputStream(entry)) {
                return readBytes(stream, (int) entry.getSize())
            }
        }
    }

    private static byte[] readBytes(final InputStream stream, final int size) {
        byte[] bytecode = new byte[size]

        int n = 0, off = 0
        while (n < size) {
            int count = stream.read(bytecode, off + n, size - n)
            if (count < 0) {
                throw new IOException("reached end of input stream unexpectedly")
            }

            n += count
        }

        return bytecode
    }
}
