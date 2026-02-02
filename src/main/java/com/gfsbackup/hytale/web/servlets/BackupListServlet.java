package com.gfsbackup.hytale.web.servlets;

import com.gfsbackup.hytale.backup.BackupManager;
import com.gfsbackup.hytale.retention.BackupMetadata;
import com.gfsbackup.hytale.retention.BackupTier;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BackupListServlet extends HttpServlet {
    private final BackupManager backupManager;
    private final boolean allowRestore;
    private final Gson gson = new Gson();

    public BackupListServlet(BackupManager backupManager, boolean allowRestore) {
        this.backupManager = backupManager;
        this.allowRestore = allowRestore;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        try {
            List<BackupMetadata> backups = backupManager.getAllBackups();

            Map<String, List<BackupMetadata>> grouped = new HashMap<>();
            grouped.put("SON", backups.stream()
                    .filter(b -> b.getTier() == BackupTier.SON)
                    .collect(Collectors.toList()));
            grouped.put("FATHER", backups.stream()
                    .filter(b -> b.getTier() == BackupTier.FATHER)
                    .collect(Collectors.toList()));
            grouped.put("GRANDFATHER", backups.stream()
                    .filter(b -> b.getTier() == BackupTier.GRANDFATHER)
                    .collect(Collectors.toList()));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("backups", backups);
            response.put("grouped", grouped);
            response.put("stats", backupManager.getBackupStats());
            response.put("allowRestore", allowRestore);
            response.put("config", backupManager.getConfigSummary());

            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
            resp.getWriter().write(gson.toJson(error));
        }
    }
}
