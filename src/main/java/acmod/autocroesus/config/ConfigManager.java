package acmod.autocroesus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import acmod.autocroesus.model.ConfigData;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private ConfigData config;

    public ConfigManager(Path dataDir) {
        this.configPath = dataDir.resolve("config.json");
        this.config = new ConfigData();
    }

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            ConfigData loaded = gson.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                config = loaded;
            }
        } catch (IOException ignored) {
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public ConfigData config() {
        return config;
    }
}
