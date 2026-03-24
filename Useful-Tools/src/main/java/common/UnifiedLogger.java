package common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Canonical UnifiedLogger — lives in the "common" package.
 *
 * FIX: The project previously had two identical copies of this class:
 *   - common.UnifiedLogger
 *   - PasswordGenerator.Logging.UnifiedLogger
 * The PasswordGenerator copy has been deleted. All classes that previously
 * imported PasswordGenerator.Logging.UnifiedLogger must now import common.UnifiedLogger.
 *
 * FIX: Logger is no longer re-initialised on every call to writeLogs().
 * Calling addHandler() repeatedly on the same Logger instance would attach
 * duplicate handlers, causing every log message to be written multiple times.
 * The new implementation checks whether handlers have already been attached.
 */
public class UnifiedLogger {

    private static final String GLOBAL_LOGGER_NAME = UnifiedLogger.class.getName();

    public Logger writeLogs(String dir) {
        Logger logger = Logger.getLogger(GLOBAL_LOGGER_NAME);

        // Only attach handlers the first time.
        if (logger.getHandlers().length > 0) {
            return logger;
        }

        try {
            Properties properties = new Properties();
            try (InputStream is = DatabaseUtils.class
                    .getClassLoader()
                    .getResourceAsStream("passwordgenerator/properties/config.properties")) {

                if (is == null) {
                    throw new RuntimeException("config.properties not found in classpath");
                }

                properties.load(is);
            }

            System.setProperty(
                    properties.getProperty("simple_formatter"),
                    properties.getProperty("format"));

            FileHandler fh = new FileHandler(dir + "_logs.log");
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);

            ConsoleHandler ch = new ConsoleHandler();
            ch.setFormatter(new SimpleFormatter());
            ch.setLevel(Level.ALL);

            logger.addHandler(fh);
            logger.addHandler(ch);

        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return logger;
    }
}
