package com.telcobright.scheduler.ui;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Jetty server for hosting the scheduler UI and REST API
 */
public class SchedulerUIServer {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerUIServer.class);

    private final Server server;
    private final int port;
    private final Scheduler scheduler;
    private final javax.sql.DataSource dataSource;

    public SchedulerUIServer(Scheduler scheduler, javax.sql.DataSource dataSource, int port) {
        this.scheduler = scheduler;
        this.dataSource = dataSource;
        this.port = port;
        this.server = new Server(port);
        configureServer();
    }

    private void configureServer() {
        try {
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            // API servlet
            JobApiServlet apiServlet = new JobApiServlet(scheduler, dataSource);
            ServletHolder apiHolder = new ServletHolder(apiServlet);
            context.addServlet(apiHolder, "/api/*");

            // Static resources (HTML/CSS/JS)
            ResourceHandler resourceHandler = new ResourceHandler();
            resourceHandler.setDirectoriesListed(false);
            resourceHandler.setWelcomeFiles(new String[]{"index.html"});

            // Try to load from classpath resources
            Resource webResource = Resource.newClassPathResource("/web");
            if (webResource != null && webResource.exists()) {
                resourceHandler.setBaseResource(webResource);
                logger.info("UI resources loaded from classpath: /web");
            } else {
                logger.warn("UI resources not found in classpath, UI may not work properly");
            }

            context.insertHandler(resourceHandler);

            server.setHandler(context);

            logger.info("Scheduler UI server configured on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to configure UI server", e);
            throw new RuntimeException("Failed to configure UI server", e);
        }
    }

    public void start() {
        try {
            server.start();
            logger.info("========================================================================");
            logger.info("✅ Scheduler UI server started successfully");
            logger.info("========================================================================");
            logger.info("📊 Access the UI at: http://localhost:{}", port);
            logger.info("🔌 API endpoint: http://localhost:{}/api", port);
            logger.info("========================================================================");
        } catch (Exception e) {
            logger.error("Failed to start UI server on port {}", port, e);
            throw new RuntimeException("Failed to start UI server", e);
        }
    }

    public void stop() {
        try {
            if (server != null && server.isRunning()) {
                server.stop();
                logger.info("Scheduler UI server stopped");
            }
        } catch (Exception e) {
            logger.error("Error stopping UI server", e);
        }
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public int getPort() {
        return port;
    }
}
