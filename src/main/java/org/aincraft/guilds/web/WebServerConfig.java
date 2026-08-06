package org.aincraft.guilds.web;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the web server component of the tech tree system.
 */
public class WebServerConfig {

    private int port = 8080;
    private boolean enabled = true;
    private int sessionTimeoutMinutes = 30;

    public WebServerConfig() {
    }

    /**
     * Load web server config from the guilds config.
     * Reads from the "webServer" section if present.
     */
    public static WebServerConfig loadFromConfig(FileConfiguration config) {
        WebServerConfig webServerConfig = new WebServerConfig();
        if (config.contains("webServer")) {
            webServerConfig.setEnabled(config.getBoolean("webServer.enabled", true));
            webServerConfig.setPort(config.getInt("webServer.port", 8080));
            webServerConfig.setSessionTimeoutMinutes(config.getInt("webServer.sessionTimeoutMinutes", 30));
        }
        return webServerConfig;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = Math.max(1, Math.min(65535, port));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = Math.max(1, sessionTimeoutMinutes);
    }
}
