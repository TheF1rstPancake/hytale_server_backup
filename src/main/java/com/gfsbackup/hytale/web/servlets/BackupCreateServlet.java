package com.gfsbackup.hytale.web.servlets;

import com.gfsbackup.hytale.backup.BackupManager;
import com.gfsbackup.hytale.retention.BackupMetadata;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class BackupCreateServlet extends HttpServlet {
    private final BackupManager backupManager;
    private final Gson gson = new Gson();

    public BackupCreateServlet(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        try {
            BackupMetadata metadata = backupManager.createBackup();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "filename", metadata.getFilename(),
                    "message", "Backup created successfully"
            );

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
