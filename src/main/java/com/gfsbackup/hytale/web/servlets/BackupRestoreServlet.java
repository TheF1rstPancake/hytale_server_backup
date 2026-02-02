package com.gfsbackup.hytale.web.servlets;

import com.gfsbackup.hytale.backup.BackupManager;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class BackupRestoreServlet extends HttpServlet {
    private final BackupManager backupManager;
    private final boolean allowed;
    private final Gson gson = new Gson();

    public BackupRestoreServlet(BackupManager backupManager, boolean allowed) {
        this.backupManager = backupManager;
        this.allowed = allowed;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        if (!allowed) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> error = Map.of(
                    "success", false,
                    "error", "Restore from web UI is disabled. Set webServer.allowRestore to true in config.json to enable."
            );
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Filename required");
            return;
        }

        String filename = pathInfo.substring(1);

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid filename");
            return;
        }

        try {
            backupManager.restoreBackup(filename);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Backup restored to temp-restore folder. Manual server restart required."
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

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
