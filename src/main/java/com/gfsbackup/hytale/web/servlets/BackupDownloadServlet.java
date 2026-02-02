package com.gfsbackup.hytale.web.servlets;

import com.gfsbackup.hytale.backup.BackupManager;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BackupDownloadServlet extends HttpServlet {
    private final BackupManager backupManager;

    public BackupDownloadServlet(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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

        File backupFile = backupManager.getBackupFile(filename);

        if (!backupFile.exists() || !backupFile.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Backup not found");
            return;
        }

        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.setContentLengthLong(backupFile.length());

        try (FileInputStream fis = new FileInputStream(backupFile);
             OutputStream os = resp.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            os.flush();
        }
    }
}
