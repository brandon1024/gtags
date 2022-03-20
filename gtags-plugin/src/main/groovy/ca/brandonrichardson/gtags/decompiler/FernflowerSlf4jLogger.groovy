package ca.brandonrichardson.gtags.decompiler

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger

class FernflowerSlf4jLogger extends IFernflowerLogger {

    private static final Logger logger = Logging.getLogger(FernflowerSlf4jLogger)

    @Override
    void writeMessage(final String message, final Severity severity) {
        switch (severity) {
            case Severity.TRACE:
                logger.trace(message)
                break
            case Severity.INFO:
                logger.info(message)
                break
            case Severity.WARN:
                logger.warn(message)
                break
            case Severity.ERROR:
                logger.error(message)
                break
        }
    }

    @Override
    void writeMessage(final String message, final Severity severity, final Throwable t) {
        switch (severity) {
            case Severity.TRACE:
                logger.trace(message, t)
                break
            case Severity.INFO:
                logger.info(message, t)
                break
            case Severity.WARN:
                logger.warn(message, t)
                break
            case Severity.ERROR:
                logger.error(message, t)
                break
        }
    }
}
