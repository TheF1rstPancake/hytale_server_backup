package com.gfsbackup.hytale.web;

import com.gfsbackup.hytale.backup.BackupManager;
import com.gfsbackup.hytale.config.BackupConfig;
import com.gfsbackup.hytale.web.servlets.*;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.EnumSet;
import java.util.List;

public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private final BackupManager backupManager;
    private final BackupConfig config;
    private Server server;

    public WebServer(BackupManager backupManager, BackupConfig config) {
        this.backupManager = backupManager;
        this.config = config;
    }

    public void start() throws Exception {
        if (!config.getWebServer().isEnabled()) {
            logger.info("Web server is disabled");
            return;
        }

        int port = config.getWebServer().getPort();
        server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // IP whitelist filter
        List<String> allowedIPs = config.getWebServer().getAllowedIPs();
        context.addFilter(new FilterHolder(new IPFilter(allowedIPs)), "/*",
                EnumSet.of(DispatcherType.REQUEST));
        logger.info("IP whitelist active: {}", allowedIPs);

        // API servlets
        boolean allowRestore = config.getWebServer().isAllowRestore();
        context.addServlet(new ServletHolder(new BackupListServlet(backupManager, allowRestore)), "/api/backups");
        context.addServlet(new ServletHolder(new BackupCreateServlet(backupManager)), "/api/backups/create");
        context.addServlet(new ServletHolder(new BackupDownloadServlet(backupManager)), "/api/backups/download/*");
        context.addServlet(new ServletHolder(new BackupRestoreServlet(backupManager, allowRestore)), "/api/backups/restore/*");
        context.addServlet(new ServletHolder(new BackupDeleteServlet(backupManager)), "/api/backups/delete/*");

        // Static resources (webapp/) served via DefaultServlet
        URL webappUrl = getClass().getClassLoader().getResource("webapp");
        if (webappUrl != null) {
            context.setBaseResource(ResourceFactory.of(context).newResource(webappUrl.toURI()));
        } else {
            logger.warn("webapp resources not found in classpath");
        }

        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        defaultHolder.setInitParameter("welcomeServlets", "false");
        defaultHolder.setInitParameter("redirectWelcome", "false");
        context.addServlet(defaultHolder, "/");

        context.setWelcomeFiles(new String[]{"index.html"});

        // Minimal error handler to avoid Jetty trying to load resources from shaded JAR
        ErrorHandler errorHandler = new ErrorHandler();
        errorHandler.setShowStacks(false);
        server.setErrorHandler(errorHandler);

        server.setHandler(context);
        server.start();

        logger.info("WorldKeeper web server started on port {}", port);
        logger.info("Access the web UI at: http://localhost:{}/", port);
    }

    public void stop() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
            logger.info("Web server stopped");
        }
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }
}
