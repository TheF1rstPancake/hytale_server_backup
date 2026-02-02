package com.gfsbackup.hytale.scheduler;

import com.gfsbackup.hytale.backup.BackupManager;
import com.gfsbackup.hytale.config.BackupConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackupScheduler {
    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupManager backupManager;
    private final BackupConfig config;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    public BackupScheduler(BackupManager backupManager, BackupConfig config) {
        this.backupManager = backupManager;
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("gfs-backup-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    public void start() {
        if (!config.isEnabled()) {
            logger.info("GFS Backup is disabled, scheduler not started");
            return;
        }

        long intervalMillis = config.getTiers().getSon().getIntervalMillis();
        long intervalMinutes = config.getTiers().getSon().getIntervalMinutes();

        scheduledTask = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        logger.info("Scheduled backup starting...");
                        backupManager.createBackup();
                        logger.info("Scheduled backup completed successfully");
                    } catch (Exception e) {
                        logger.error("Scheduled backup failed", e);
                    }
                },
                intervalMillis,  // Initial delay
                intervalMillis,  // Period
                TimeUnit.MILLISECONDS
        );

        logger.info("GFS Backup scheduler started with {} minute intervals", intervalMinutes);
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            logger.info("Backup scheduler stopped");
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void triggerManualBackup() {
        logger.info("Manual backup triggered");
        scheduler.submit(() -> {
            try {
                backupManager.createBackup();
                logger.info("Manual backup completed successfully");
            } catch (Exception e) {
                logger.error("Manual backup failed", e);
            }
        });
    }
}
