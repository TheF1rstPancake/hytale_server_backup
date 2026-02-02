package com.gfsbackup.hytale;

import com.gfsbackup.hytale.backup.BackupManager;
import com.gfsbackup.hytale.config.BackupConfig;
import com.gfsbackup.hytale.config.ConfigManager;
import com.gfsbackup.hytale.scheduler.BackupScheduler;
import com.gfsbackup.hytale.web.WebServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class GFSBackupPlugin extends JavaPlugin {
    private static final Logger logger = LoggerFactory.getLogger(GFSBackupPlugin.class);

    private ConfigManager configManager;
    private BackupConfig config;
    private BackupManager backupManager;
    private BackupScheduler scheduler;
    private WebServer webServer;

    public GFSBackupPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        logger.info("WorldKeeper v1.0.0 - preLoad");

        File dataFolder = getDataDirectory().toAbsolutePath().toFile();
        dataFolder.mkdirs();

        configManager = new ConfigManager(dataFolder);

        try {
            config = configManager.loadConfig();
            logger.info("Configuration loaded from: {}", dataFolder.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to load configuration, using defaults", e);
            config = new BackupConfig();
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void setup() {
        logger.info("WorldKeeper - setup");

        try {
            // Resolve to absolute path first, then navigate up:
            // mods/com.gfsbackup_GFSBackup/ -> mods/ -> Server/
            File dataDir = getDataDirectory().toAbsolutePath().toFile();
            File serverDirectory = dataDir.getParentFile().getParentFile();
            logger.info("Data directory: {}", dataDir.getAbsolutePath());
            logger.info("Server directory: {}", serverDirectory.getAbsolutePath());
            backupManager = new BackupManager(config, serverDirectory);
            logger.info("Backup manager initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize backup manager", e);
            throw new RuntimeException("Backup manager initialization failed", e);
        }
    }

    @Override
    protected void start() {
        logger.info("WorldKeeper - start");

        try {
            scheduler = new BackupScheduler(backupManager, config);
            scheduler.start();

            webServer = new WebServer(backupManager, config);
            webServer.start();

            logger.info("WorldKeeper started successfully");
            logger.info("Retention policy:");
            logger.info("  Sons: every {} min, keep {}",
                    config.getTiers().getSon().getIntervalMinutes(),
                    config.getTiers().getSon().getRetentionCount());
            logger.info("  Fathers: every {} min, keep {}",
                    config.getTiers().getFather().getIntervalMinutes(),
                    config.getTiers().getFather().getRetentionCount());
            logger.info("  Grandfathers: every {} min, keep {}",
                    config.getTiers().getGrandfather().getIntervalMinutes(),
                    config.getTiers().getGrandfather().getRetentionCount());

            if (config.getWebServer().isEnabled()) {
                logger.info("Web UI: http://localhost:{}/", config.getWebServer().getPort());
            }

            warnIfConflictingBackups();

        } catch (Exception e) {
            logger.error("Failed to start WorldKeeper", e);
            throw new RuntimeException("Plugin start failed", e);
        }
    }

    @Override
    protected void shutdown() {
        logger.info("Shutting down WorldKeeper");

        try {
            if (scheduler != null) {
                scheduler.stop();
            }
            if (webServer != null) {
                webServer.stop();
            }
        } catch (Exception e) {
            logger.error("Error during plugin shutdown", e);
        }

        logger.info("WorldKeeper shut down");
    }

    private void warnIfConflictingBackups() {
        try {
            File adminUIBackupConfig = getDataDirectory().toAbsolutePath()
                    .getParent()                       // mods/
                    .getParent()                       // Server/
                    .resolve("AdminUI")
                    .resolve("Backup.json")
                    .toFile();
            if (adminUIBackupConfig.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(adminUIBackupConfig.toPath()));
                if (content.contains("\"enabled\":true") || content.contains("\"enabled\": true")) {
                    logger.warn("========================================================================");
                    logger.warn("AdminUI backup is still ENABLED and will run alongside WorldKeeper.");
                    logger.warn("This means two backup systems are writing to the same backups/ folder.");
                    logger.warn("To avoid conflicts, disable AdminUI backups in:");
                    logger.warn("  {}", adminUIBackupConfig.getAbsolutePath());
                    logger.warn("  Set \"enabled\": false");
                    logger.warn("========================================================================");
                }
            }
        } catch (Exception e) {
            // Don't fail startup over this check
        }
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public BackupScheduler getScheduler() {
        return scheduler;
    }
}
