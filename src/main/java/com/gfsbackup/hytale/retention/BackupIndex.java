package com.gfsbackup.hytale.retention;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BackupIndex {
    private List<BackupMetadata> backups = new ArrayList<>();
    private long lastBackup = 0;
    private int totalBackups = 0;
    private long totalSizeBytes = 0;

    private transient final File indexFile;
    private transient final Gson gson;

    public BackupIndex(File indexFile) {
        this.indexFile = indexFile;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void load() throws IOException {
        if (!indexFile.exists()) {
            backups = new ArrayList<>();
            lastBackup = 0;
            totalBackups = 0;
            totalSizeBytes = 0;
            return;
        }

        try (Reader reader = new FileReader(indexFile)) {
            BackupIndex loaded = gson.fromJson(reader, BackupIndex.class);
            if (loaded != null) {
                this.backups = loaded.backups != null ? loaded.backups : new ArrayList<>();
                this.lastBackup = loaded.lastBackup;
                this.totalBackups = loaded.totalBackups;
                this.totalSizeBytes = loaded.totalSizeBytes;
            }
        }
    }

    public void save() throws IOException {
        indexFile.getParentFile().mkdirs();

        try (Writer writer = new FileWriter(indexFile)) {
            gson.toJson(this, writer);
        }
    }

    public void addBackup(BackupMetadata metadata) {
        backups.add(metadata);
        lastBackup = metadata.getCreatedAt();
        totalBackups = backups.size();
        recalculateTotalSize();
    }

    public void removeBackup(BackupMetadata metadata) {
        backups.remove(metadata);
        totalBackups = backups.size();
        recalculateTotalSize();
    }

    public void updateBackup(BackupMetadata metadata) {
        for (int i = 0; i < backups.size(); i++) {
            if (backups.get(i).getFilename().equals(metadata.getFilename())) {
                backups.set(i, metadata);
                break;
            }
        }
        recalculateTotalSize();
    }

    public List<BackupMetadata> getBackupsByTier(BackupTier tier) {
        return backups.stream()
                .filter(b -> b.getTier() == tier)
                .sorted(Comparator.comparingLong(BackupMetadata::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<BackupMetadata> getAllBackups() {
        return new ArrayList<>(backups);
    }

    public BackupMetadata getBackupByFilename(String filename) {
        return backups.stream()
                .filter(b -> b.getFilename().equals(filename))
                .findFirst()
                .orElse(null);
    }

    private void recalculateTotalSize() {
        totalSizeBytes = backups.stream()
                .mapToLong(BackupMetadata::getSizeBytes)
                .sum();
    }

    public List<BackupMetadata> getBackups() {
        return backups;
    }

    public void setBackups(List<BackupMetadata> backups) {
        this.backups = backups;
    }

    public long getLastBackup() {
        return lastBackup;
    }

    public void setLastBackup(long lastBackup) {
        this.lastBackup = lastBackup;
    }

    public int getTotalBackups() {
        return totalBackups;
    }

    public void setTotalBackups(int totalBackups) {
        this.totalBackups = totalBackups;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }
}
