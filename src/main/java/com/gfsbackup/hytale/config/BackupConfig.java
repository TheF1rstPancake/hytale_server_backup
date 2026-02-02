package com.gfsbackup.hytale.config;

import java.util.List;

public class BackupConfig {
    private boolean enabled = true;
    private String backupFolder = "backups";
    private String worldFolder = "universe";
    private TierConfig tiers = new TierConfig();
    private HookConfig hooks = new HookConfig();
    private WebServerConfig webServer = new WebServerConfig();
    private AdvancedConfig advanced = new AdvancedConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(String backupFolder) {
        this.backupFolder = backupFolder;
    }

    public String getWorldFolder() {
        return worldFolder;
    }

    public void setWorldFolder(String worldFolder) {
        this.worldFolder = worldFolder;
    }

    public TierConfig getTiers() {
        return tiers;
    }

    public void setTiers(TierConfig tiers) {
        this.tiers = tiers;
    }

    public HookConfig getHooks() {
        return hooks;
    }

    public void setHooks(HookConfig hooks) {
        this.hooks = hooks;
    }

    public WebServerConfig getWebServer() {
        return webServer;
    }

    public void setWebServer(WebServerConfig webServer) {
        this.webServer = webServer;
    }

    public AdvancedConfig getAdvanced() {
        return advanced;
    }

    public void setAdvanced(AdvancedConfig advanced) {
        this.advanced = advanced;
    }

    public static class TierConfig {
        private TierSettings son = new TierSettings(true, 30, 12, "30-minute backups for 6 hours");
        private TierSettings father = new TierSettings(true, 1440, 7, "Daily backups for 7 days");
        private TierSettings grandfather = new TierSettings(true, 10080, 4, "Weekly backups for 4 weeks");

        public TierSettings getSon() {
            return son;
        }

        public void setSon(TierSettings son) {
            this.son = son;
        }

        public TierSettings getFather() {
            return father;
        }

        public void setFather(TierSettings father) {
            this.father = father;
        }

        public TierSettings getGrandfather() {
            return grandfather;
        }

        public void setGrandfather(TierSettings grandfather) {
            this.grandfather = grandfather;
        }
    }

    public static class TierSettings {
        private boolean enabled = true;
        private int intervalMinutes = 30;
        private int retentionCount = 12;
        private String description = "";

        public TierSettings() {
        }

        public TierSettings(boolean enabled, int intervalMinutes, int retentionCount, String description) {
            this.enabled = enabled;
            this.intervalMinutes = intervalMinutes;
            this.retentionCount = retentionCount;
            this.description = description;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(int intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public int getRetentionCount() {
            return retentionCount;
        }

        public void setRetentionCount(int retentionCount) {
            this.retentionCount = retentionCount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public long getIntervalMillis() {
            return intervalMinutes * 60L * 1000L;
        }
    }

    public static class HookConfig {
        private List<String> preBackup = List.of("say [WorldKeeper] Starting backup...");
        private List<String> postBackup = List.of("say [WorldKeeper] Backup complete!");

        public List<String> getPreBackup() {
            return preBackup;
        }

        public void setPreBackup(List<String> preBackup) {
            this.preBackup = preBackup;
        }

        public List<String> getPostBackup() {
            return postBackup;
        }

        public void setPostBackup(List<String> postBackup) {
            this.postBackup = postBackup;
        }
    }

    public static class WebServerConfig {
        private boolean enabled = true;
        private int port = 8081;
        private boolean allowRestore = false;
        private List<String> allowedIPs = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isAllowRestore() {
            return allowRestore;
        }

        public void setAllowRestore(boolean allowRestore) {
            this.allowRestore = allowRestore;
        }

        public List<String> getAllowedIPs() {
            return allowedIPs;
        }

        public void setAllowedIPs(List<String> allowedIPs) {
            this.allowedIPs = allowedIPs;
        }
    }

    public static class AdvancedConfig {
        private boolean serverSaveBeforeBackup = true;
        private boolean deleteEmptyBackups = true;
        private boolean asyncBackup = true;

        public boolean isServerSaveBeforeBackup() {
            return serverSaveBeforeBackup;
        }

        public void setServerSaveBeforeBackup(boolean serverSaveBeforeBackup) {
            this.serverSaveBeforeBackup = serverSaveBeforeBackup;
        }

        public boolean isDeleteEmptyBackups() {
            return deleteEmptyBackups;
        }

        public void setDeleteEmptyBackups(boolean deleteEmptyBackups) {
            this.deleteEmptyBackups = deleteEmptyBackups;
        }

        public boolean isAsyncBackup() {
            return asyncBackup;
        }

        public void setAsyncBackup(boolean asyncBackup) {
            this.asyncBackup = asyncBackup;
        }
    }
}
