package com.gfsbackup.hytale.retention;

import com.gfsbackup.hytale.config.BackupConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RetentionPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RetentionPolicy.class);

    private final BackupConfig config;
    private final BackupIndex index;
    private final File backupFolder;

    public RetentionPolicy(BackupConfig config, BackupIndex index, File backupFolder) {
        this.config = config;
        this.index = index;
        this.backupFolder = backupFolder;
    }

    public void apply() {
        logger.info("Applying GFS retention policy...");

        long now = System.currentTimeMillis();

        List<BackupMetadata> sons = new ArrayList<>(index.getBackupsByTier(BackupTier.SON));
        List<BackupMetadata> fathers = new ArrayList<>(index.getBackupsByTier(BackupTier.FATHER));
        List<BackupMetadata> grandfathers = new ArrayList<>(index.getBackupsByTier(BackupTier.GRANDFATHER));

        sons.sort(Comparator.comparingLong(BackupMetadata::getCreatedAt).reversed());
        fathers.sort(Comparator.comparingLong(BackupMetadata::getCreatedAt).reversed());
        grandfathers.sort(Comparator.comparingLong(BackupMetadata::getCreatedAt).reversed());

        logger.info("Current backup counts - Sons: {}, Fathers: {}, Grandfathers: {}",
                sons.size(), fathers.size(), grandfathers.size());

        promoteBackups(now, sons, fathers, grandfathers);

        cleanupExcessBackups(sons, fathers, grandfathers);

        logger.info("Retention policy applied - Sons: {}, Fathers: {}, Grandfathers: {}",
                sons.size(), fathers.size(), grandfathers.size());
    }

    private void promoteBackups(long now, List<BackupMetadata> sons, List<BackupMetadata> fathers, List<BackupMetadata> grandfathers) {
        // Promote SON to FATHER if we have excess SONs
        if (config.getTiers().getFather().isEnabled() && sons.size() > config.getTiers().getSon().getRetentionCount()) {
            BackupMetadata oldestSon = findOldestBackup(sons);

            if (oldestSon != null) {
                logger.info("Promoting SON backup {} to FATHER (SON retention limit reached)", oldestSon.getFilename());
                oldestSon.promote(BackupTier.FATHER);
                index.updateBackup(oldestSon);
                sons.remove(oldestSon);
                fathers.add(0, oldestSon);
            }
        }

        // Promote FATHER to GRANDFATHER if we have excess FATHERs
        if (config.getTiers().getGrandfather().isEnabled() && fathers.size() > config.getTiers().getFather().getRetentionCount()) {
            BackupMetadata oldestFather = findOldestBackup(fathers);

            if (oldestFather != null) {
                logger.info("Promoting FATHER backup {} to GRANDFATHER (FATHER retention limit reached)", oldestFather.getFilename());
                oldestFather.promote(BackupTier.GRANDFATHER);
                index.updateBackup(oldestFather);
                fathers.remove(oldestFather);
                grandfathers.add(0, oldestFather);
            }
        }
    }

    private void cleanupExcessBackups(List<BackupMetadata> sons, List<BackupMetadata> fathers, List<BackupMetadata> grandfathers) {
        cleanupTier(sons, config.getTiers().getSon().getRetentionCount(), "SON");
        cleanupTier(fathers, config.getTiers().getFather().getRetentionCount(), "FATHER");
        cleanupTier(grandfathers, config.getTiers().getGrandfather().getRetentionCount(), "GRANDFATHER");
    }

    private void cleanupTier(List<BackupMetadata> backups, int retentionCount, String tierName) {
        if (backups.size() <= retentionCount) {
            return;
        }

        List<BackupMetadata> toDelete = backups.subList(retentionCount, backups.size());
        logger.info("Deleting {} excess {} backups", toDelete.size(), tierName);

        for (BackupMetadata backup : new ArrayList<>(toDelete)) {
            deleteBackup(backup);
        }
    }

    private void deleteBackup(BackupMetadata backup) {
        File backupFile = new File(backupFolder, backup.getFilename());
        if (backupFile.exists()) {
            if (backupFile.delete()) {
                logger.info("Deleted backup: {}", backup.getFilename());
                index.removeBackup(backup);
            } else {
                logger.error("Failed to delete backup: {}", backup.getFilename());
            }
        } else {
            logger.warn("Backup file not found, removing from index: {}", backup.getFilename());
            index.removeBackup(backup);
        }
    }

    private BackupMetadata findOldestBackup(List<BackupMetadata> backups) {
        if (backups.isEmpty()) {
            return null;
        }

        BackupMetadata oldest = backups.get(0);
        for (BackupMetadata backup : backups) {
            if (backup.getCreatedAt() < oldest.getCreatedAt()) {
                oldest = backup;
            }
        }
        return oldest;
    }
}
