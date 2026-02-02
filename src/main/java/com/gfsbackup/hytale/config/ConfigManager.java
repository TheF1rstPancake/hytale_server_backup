package com.gfsbackup.hytale.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigManager {
    private final File configFile;
    private final Gson gson;

    public ConfigManager(File dataFolder) {
        this.configFile = new File(dataFolder, "config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public BackupConfig loadConfig() throws IOException {
        if (!configFile.exists()) {
            return createDefaultConfig();
        }

        try (Reader reader = new FileReader(configFile)) {
            BackupConfig config = gson.fromJson(reader, BackupConfig.class);
            if (config == null) {
                return createDefaultConfig();
            }
            return config;
        } catch (Exception e) {
            createBackup();
            return createDefaultConfig();
        }
    }

    public void saveConfig(BackupConfig config) throws IOException {
        configFile.getParentFile().mkdirs();

        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        }
    }

    private BackupConfig createDefaultConfig() throws IOException {
        BackupConfig defaultConfig = new BackupConfig();

        configFile.getParentFile().mkdirs();

        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(defaultConfig, writer);
        }

        return defaultConfig;
    }

    private void createBackup() {
        if (configFile.exists()) {
            File backupFile = new File(configFile.getParentFile(), "config.json.bak");
            try {
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Ignore backup failure
            }
        }
    }
}
