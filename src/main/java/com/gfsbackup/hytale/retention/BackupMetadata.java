package com.gfsbackup.hytale.retention;

public class BackupMetadata {
    private String filename;
    private BackupTier tier;
    private long createdAt;
    private long sizeBytes;
    private String checksum;
    private boolean promoted;
    private BackupTier promotedFrom;
    private Long promotedAt;

    public BackupMetadata() {
    }

    public BackupMetadata(String filename, BackupTier tier, long createdAt, long sizeBytes, String checksum) {
        this.filename = filename;
        this.tier = tier;
        this.createdAt = createdAt;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.promoted = false;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public BackupTier getTier() {
        return tier;
    }

    public void setTier(BackupTier tier) {
        this.tier = tier;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public BackupTier getPromotedFrom() {
        return promotedFrom;
    }

    public void setPromotedFrom(BackupTier promotedFrom) {
        this.promotedFrom = promotedFrom;
    }

    public Long getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Long promotedAt) {
        this.promotedAt = promotedAt;
    }

    public void promote(BackupTier newTier) {
        this.promotedFrom = this.tier;
        this.tier = newTier;
        this.promoted = true;
        this.promotedAt = System.currentTimeMillis();
    }
}
