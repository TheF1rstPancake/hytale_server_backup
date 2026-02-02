package com.gfsbackup.hytale.backup;

import com.gfsbackup.hytale.config.BackupConfig;
import com.gfsbackup.hytale.retention.BackupIndex;
import com.gfsbackup.hytale.retention.BackupMetadata;
import com.gfsbackup.hytale.retention.BackupTier;
import com.gfsbackup.hytale.retention.RetentionPolicy;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupManager {
    private static final Logger logger = LoggerFactory.getLogger(BackupManager.class);
    private static final SimpleDateFormat FILENAME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final BackupConfig config;
    private final File serverDirectory;
    private final File backupFolder;
    private final File worldFolder;
    private final BackupIndex index;
    private final RetentionPolicy retentionPolicy;
    private final HookExecutor hookExecutor;

    private final Object backupLock = new Object();

    public BackupManager(BackupConfig config, File serverDirectory) throws IOException {
        this.config = config;
        this.serverDirectory = serverDirectory;
        this.backupFolder = new File(serverDirectory, config.getBackupFolder());
        this.worldFolder = new File(serverDirectory, config.getWorldFolder());

        this.backupFolder.mkdirs();

        File indexFile = new File(backupFolder, "backup-index.json");
        this.index = new BackupIndex(indexFile);
        this.index.load();

        this.retentionPolicy = new RetentionPolicy(config, index, backupFolder);
        this.hookExecutor = new HookExecutor(serverDirectory);
    }

    public BackupMetadata createBackup() throws Exception {
        synchronized (backupLock) {
            logger.info("Starting backup creation...");

            hookExecutor.executePreBackupHooks(config.getHooks().getPreBackup());

            if (config.getAdvanced().isServerSaveBeforeBackup()) {
                try {
                    logger.info("Triggering server save before backup...");
                    CommandManager.get().handleCommand(ConsoleSender.INSTANCE, "save");
                    Thread.sleep(2000);
                } catch (Exception e) {
                    logger.warn("Server save command failed, proceeding with backup anyway", e);
                }
            }

            String filename = FILENAME_FORMAT.format(new Date()) + ".zip";
            File backupFile = new File(backupFolder, filename);

            logger.info("Creating backup: {}", filename);
            ZipUtility.createZip(worldFolder, backupFile);

            if (config.getAdvanced().isDeleteEmptyBackups() && backupFile.length() < 1024) {
                logger.warn("Backup is too small ({}  bytes), deleting", backupFile.length());
                backupFile.delete();
                throw new IOException("Backup file is empty or too small");
            }

            String checksum = ZipUtility.calculateChecksum(backupFile);

            BackupMetadata metadata = new BackupMetadata(
                    filename,
                    BackupTier.SON,
                    System.currentTimeMillis(),
                    backupFile.length(),
                    checksum
            );

            index.addBackup(metadata);
            index.save();

            logger.info("Backup created successfully: {} ({} bytes)", filename, backupFile.length());

            retentionPolicy.apply();
            index.save();

            hookExecutor.executePostBackupHooks(
                    config.getHooks().getPostBackup(),
                    backupFile.getAbsolutePath(),
                    filename,
                    BackupTier.SON.name(),
                    backupFile.length()
            );

            return metadata;
        }
    }

    public void restoreBackup(String filename) throws Exception {
        synchronized (backupLock) {
            logger.info("Restoring backup: {}", filename);

            BackupMetadata metadata = index.getBackupByFilename(filename);
            if (metadata == null) {
                throw new IOException("Backup not found: " + filename);
            }

            File backupFile = new File(backupFolder, filename);
            if (!backupFile.exists()) {
                throw new IOException("Backup file does not exist: " + filename);
            }

            // TODO: Implement server stop/start when Hytale API is available
            // For now, just extract to a temporary location
            File tempRestoreFolder = new File(serverDirectory, "temp-restore");
            if (tempRestoreFolder.exists()) {
                deleteDirectory(tempRestoreFolder);
            }

            ZipUtility.extractZip(backupFile, tempRestoreFolder);

            logger.info("Backup extracted to: {}", tempRestoreFolder.getAbsolutePath());
            logger.warn("Manual intervention required: Stop server, replace world folder, and restart");
        }
    }

    public void deleteBackup(String filename) throws IOException {
        synchronized (backupLock) {
            logger.info("Deleting backup: {}", filename);

            BackupMetadata metadata = index.getBackupByFilename(filename);
            if (metadata == null) {
                throw new IOException("Backup not found in index: " + filename);
            }

            File backupFile = new File(backupFolder, filename);
            if (backupFile.exists()) {
                if (backupFile.delete()) {
                    logger.info("Backup file deleted: {}", filename);
                } else {
                    throw new IOException("Failed to delete backup file: " + filename);
                }
            }

            index.removeBackup(metadata);
            index.save();
        }
    }

    public List<BackupMetadata> getAllBackups() {
        return index.getAllBackups();
    }

    public BackupMetadata getBackupByFilename(String filename) {
        return index.getBackupByFilename(filename);
    }

    public Map<String, Object> getBackupStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBackups", index.getTotalBackups());
        stats.put("totalSizeBytes", index.getTotalSizeBytes());
        stats.put("lastBackup", index.getLastBackup());

        stats.put("sonCount", index.getBackupsByTier(BackupTier.SON).size());
        stats.put("fatherCount", index.getBackupsByTier(BackupTier.FATHER).size());
        stats.put("grandfatherCount", index.getBackupsByTier(BackupTier.GRANDFATHER).size());

        return stats;
    }

    public Map<String, Object> getConfigSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("backupFolder", config.getBackupFolder());
        summary.put("worldFolder", config.getWorldFolder());

        BackupConfig.TierSettings son = config.getTiers().getSon();
        BackupConfig.TierSettings father = config.getTiers().getFather();
        BackupConfig.TierSettings grandfather = config.getTiers().getGrandfather();

        Map<String, Object> snapshots = new HashMap<>();
        snapshots.put("enabled", son.isEnabled());
        snapshots.put("intervalMinutes", son.getIntervalMinutes());
        snapshots.put("retentionCount", son.getRetentionCount());
        summary.put("snapshots", snapshots);

        Map<String, Object> dailies = new HashMap<>();
        dailies.put("enabled", father.isEnabled());
        dailies.put("intervalMinutes", father.getIntervalMinutes());
        dailies.put("retentionCount", father.getRetentionCount());
        summary.put("dailies", dailies);

        Map<String, Object> archives = new HashMap<>();
        archives.put("enabled", grandfather.isEnabled());
        archives.put("intervalMinutes", grandfather.getIntervalMinutes());
        archives.put("retentionCount", grandfather.getRetentionCount());
        summary.put("archives", archives);

        Map<String, Object> advanced = new HashMap<>();
        advanced.put("serverSaveBeforeBackup", config.getAdvanced().isServerSaveBeforeBackup());
        advanced.put("asyncBackup", config.getAdvanced().isAsyncBackup());
        summary.put("advanced", advanced);

        return summary;
    }

    public File getBackupFile(String filename) {
        return new File(backupFolder, filename);
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
