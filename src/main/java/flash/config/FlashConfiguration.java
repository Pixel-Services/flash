package flash.config;

import org.simpleyaml.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * The FlashConfiguration class provides utility methods for loading, saving, and managing YAML configuration files.
 */
public class FlashConfiguration extends YamlConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(FlashConfiguration.class);
    private final File file;

    /**
     * Constructs a FlashConfiguration instance of the default configuration file.
     */
    public FlashConfiguration() {
        this("config.yml");
    }

    /**
     * Constructs a FlashConfiguration instance with a specified file path.
     *
     * @param path the path to the configuration file.
     */
    public FlashConfiguration(String path) {
        this(getFile(path));
    }

    /**
     * Constructs a FlashConfiguration instance with a specified file.
     *
     * @param file the configuration file.
     */
    private FlashConfiguration(File file) {
        super(loadConfig(file));
        this.file = file;
    }

    /**
     * Loads a YamlConfiguration from a file.
     *
     * @param file the configuration file to load
     * @return the loaded YamlConfiguration
     */
    private static YamlConfiguration loadConfig(File file) {
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (IOException e) {
            logger.error("Failed to load configuration file: {}", file.getPath(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the configuration file.
     *
     * @param path the path to the configuration file.
     * @return the configuration file
     */
    private static File getFile(String path) {
        File file = new File(path);
        try {
            if (!file.exists()) {
                try (InputStream resourceStream = FlashConfiguration.class.getClassLoader().getResourceAsStream(file.getName())) {
                    if (resourceStream != null) {
                        Files.copy(resourceStream, file.toPath());
                    } else {
                        file.createNewFile();
                    }
                }
            }
            return file;
        } catch (IOException e) {
            logger.error("Failed to create configuration file: {}", path, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves the configuration file to disk.
     */
    public void save() {
        try {
            save(file);
        } catch (IOException e) {
            logger.error("Failed to save configuration file: {}", file.getPath(), e);
        }
    }
}
