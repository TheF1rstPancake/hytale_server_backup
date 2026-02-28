package com.gfsbackup.hytale.retention;

import com.gfsbackup.hytale.config.BackupConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class RetentionPolicyTest {

    @TempDir
    File tempDir;

    /**
     * This test simulates the bug where multiple backups from the same day
     * all get promoted to FATHER tier, resulting in 7 FATHERs from the same day.
     *
     * Expected behavior: Only ONE FATHER per day should exist.
     */
    @Test
    public void testOnlyOnePromotionPerDay() throws IOException {
        // Setup config: 12 SONs (30 min), 7 FATHERs (daily), 4 GRANDFATHERs (weekly)
        BackupConfig config = new BackupConfig();
        config.getTiers().getSon().setRetentionCount(12);
        config.getTiers().getFather().setRetentionCount(7);
        config.getTiers().getGrandfather().setRetentionCount(4);

        File indexFile = new File(tempDir, "backup-index.json");
        BackupIndex index = new BackupIndex(indexFile);
        RetentionPolicy policy = new RetentionPolicy(config, index, tempDir);

        // Create 15 backups all from the SAME DAY (every 30 minutes)
        // This simulates a server that's been running for 7.5 hours
        LocalDate today = LocalDate.now();
        Instant baseTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (int i = 0; i < 15; i++) {
            Instant backupTime = baseTime.plus(i * 30, ChronoUnit.MINUTES);
            BackupMetadata backup = new BackupMetadata(
                String.format("backup-%02d.zip", i),
                BackupTier.SON,
                backupTime.toEpochMilli(),
                1000000L,
                "checksum-" + i
            );
            index.addBackup(backup);

            // Create dummy file so deletion doesn't fail
            new File(tempDir, backup.getFilename()).createNewFile();
        }

        System.out.println("=== BEFORE RETENTION POLICY ===");
        System.out.println("SONs: " + index.getBackupsByTier(BackupTier.SON).size());
        System.out.println("FATHERs: " + index.getBackupsByTier(BackupTier.FATHER).size());
        System.out.println("GRANDFATHERs: " + index.getBackupsByTier(BackupTier.GRANDFATHER).size());

        // Apply retention policy
        // This should:
        // 1. Keep the 12 newest SONs
        // 2. Delete the 3 oldest SONs (no promotion since they're same day)
        // Result: 12 SONs, 0 FATHERs
        policy.apply();

        System.out.println("\n=== AFTER FIRST RETENTION POLICY ===");
        System.out.println("SONs: " + index.getBackupsByTier(BackupTier.SON).size());
        System.out.println("FATHERs: " + index.getBackupsByTier(BackupTier.FATHER).size());

        // We should have 12 SONs and 1 FATHER (promoted to represent that day)
        assertEquals(12, index.getBackupsByTier(BackupTier.SON).size(),
            "Should keep exactly 12 SONs");
        assertEquals(1, index.getBackupsByTier(BackupTier.FATHER).size(),
            "Should have 1 FATHER to represent the first day (promoted when SON limit was reached)");

        // Now simulate the NEXT DAY - add 13 more backups from tomorrow
        LocalDate tomorrow = today.plusDays(1);
        Instant tomorrowBase = tomorrow.atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (int i = 0; i < 13; i++) {
            Instant backupTime = tomorrowBase.plus(i * 30, ChronoUnit.MINUTES);
            BackupMetadata backup = new BackupMetadata(
                String.format("backup-day2-%02d.zip", i),
                BackupTier.SON,
                backupTime.toEpochMilli(),
                1000000L,
                "checksum-day2-" + i
            );
            index.addBackup(backup);
            new File(tempDir, backup.getFilename()).createNewFile();
        }

        System.out.println("\n=== AFTER ADDING TOMORROW'S BACKUPS ===");
        System.out.println("Total SONs: " + index.getBackupsByTier(BackupTier.SON).size());

        // Apply retention again
        // This should:
        // 1. Promote the oldest SON from day 1 to FATHER (first time we have >12 SONs from different days)
        // 2. Keep 12 newest SONs (all from day 2)
        // Result: 12 SONs from day 2, 1 FATHER from day 1
        policy.apply();

        System.out.println("\n=== AFTER SECOND RETENTION POLICY ===");
        System.out.println("SONs: " + index.getBackupsByTier(BackupTier.SON).size());
        System.out.println("FATHERs: " + index.getBackupsByTier(BackupTier.FATHER).size());

        var fathers = index.getBackupsByTier(BackupTier.FATHER);
        for (BackupMetadata father : fathers) {
            Instant fatherTime = Instant.ofEpochMilli(father.getCreatedAt());
            LocalDate fatherDate = fatherTime.atZone(ZoneId.systemDefault()).toLocalDate();
            System.out.println("  FATHER: " + father.getFilename() + " from " + fatherDate);
        }

        // KEY ASSERTION: We should have exactly ONE FATHER, not multiple from the same day
        assertEquals(1, fathers.size(),
            "Should have exactly ONE FATHER after promotions from 2 days of backups");

        // The FATHER should be from day 1 (yesterday)
        Instant fatherTime = Instant.ofEpochMilli(fathers.get(0).getCreatedAt());
        LocalDate fatherDate = fatherTime.atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(today, fatherDate,
            "The FATHER should be from the first day (today in test)");
    }

    /**
     * Test that promotions work correctly across multiple days
     */
    @Test
    public void testMultipleDayPromotions() throws IOException {
        BackupConfig config = new BackupConfig();
        config.getTiers().getSon().setRetentionCount(3);  // Small for faster test
        config.getTiers().getFather().setRetentionCount(3);
        config.getTiers().getGrandfather().setRetentionCount(2);

        File indexFile = new File(tempDir, "backup-index.json");
        BackupIndex index = new BackupIndex(indexFile);
        RetentionPolicy policy = new RetentionPolicy(config, index, tempDir);

        LocalDate baseDate = LocalDate.now();

        // Simulate 3 days of backups, 5 backups per day
        for (int day = 0; day < 3; day++) {
            LocalDate currentDay = baseDate.plusDays(day);
            Instant dayStart = currentDay.atStartOfDay(ZoneId.systemDefault()).toInstant();

            for (int i = 0; i < 5; i++) {
                Instant backupTime = dayStart.plus(i * 30, ChronoUnit.MINUTES);
                BackupMetadata backup = new BackupMetadata(
                    String.format("backup-day%d-%02d.zip", day, i),
                    BackupTier.SON,
                    backupTime.toEpochMilli(),
                    1000000L,
                    "checksum-" + day + "-" + i
                );
                index.addBackup(backup);
                new File(tempDir, backup.getFilename()).createNewFile();

                // Run retention after each backup
                policy.apply();
            }
        }

        System.out.println("\n=== FINAL STATE AFTER 3 DAYS ===");
        System.out.println("SONs: " + index.getBackupsByTier(BackupTier.SON).size());
        System.out.println("FATHERs: " + index.getBackupsByTier(BackupTier.FATHER).size());

        var fathers = index.getBackupsByTier(BackupTier.FATHER);
        for (BackupMetadata father : fathers) {
            Instant fatherTime = Instant.ofEpochMilli(father.getCreatedAt());
            LocalDate fatherDate = fatherTime.atZone(ZoneId.systemDefault()).toLocalDate();
            System.out.println("  FATHER: " + father.getFilename() + " from " + fatherDate);
        }

        // We should have 3 SONs (from day 2) and 3 FATHERs (one from each day)
        assertEquals(3, index.getBackupsByTier(BackupTier.SON).size(),
            "Should have 3 SONs from most recent day");
        assertEquals(3, fathers.size(),
            "Should have exactly 3 FATHERs, one from each of the 3 days");

        // Verify each FATHER is from a different day
        LocalDate day0 = Instant.ofEpochMilli(fathers.get(0).getCreatedAt())
            .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate day1 = Instant.ofEpochMilli(fathers.get(1).getCreatedAt())
            .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate day2 = Instant.ofEpochMilli(fathers.get(2).getCreatedAt())
            .atZone(ZoneId.systemDefault()).toLocalDate();

        assertNotEquals(day0, day1, "Each FATHER should be from a different day");
        assertNotEquals(day1, day2, "Each FATHER should be from a different day");
        assertNotEquals(day0, day2, "Each FATHER should be from a different day");
    }
}
