package PasswordGenerator.Logging;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.ConsoleHandler;
import java.util.Properties;
import java.util.logging.SimpleFormatter;

public class UnifiedLogger {
	private static final String GLOBAL_LOGGER_NAME = UnifiedLogger.class.getName();
	
	public Logger writeLogs(String dir) {
		Logger logger = Logger.getLogger(GLOBAL_LOGGER_NAME);
		try{
			FileHandler fh = new FileHandler(dir+"_logs.log");
			ConsoleHandler ch = new ConsoleHandler();
			Properties properties = new Properties();
			String root_path = Thread.currentThread().getContextClassLoader().getResource("").getPath();
			String properties_path = root_path + "config.properties";
			FileInputStream fis = new FileInputStream(properties_path);
			properties.load(fis);
			System.setProperty(properties.getProperty("simple_formatter"), properties.getProperty("format"));
			fh.setFormatter(new SimpleFormatter());
			ch.setFormatter(new SimpleFormatter());
			fh.setLevel(Level.ALL);
			ch.setLevel(Level.ALL);
			logger.addHandler(fh);
			logger.addHandler(ch);
		}
		catch(FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		}
		catch(IOException ioe) {
			ioe.printStackTrace();
		}
		return logger;
	}
}
