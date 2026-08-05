package org.aincraft.towny.web;

import org.bukkit.configuration.file.FileConfiguration;
import org.aincraft.towny.TownyPlugin;

import java.util.List;

/**
 * Configuration for the web server component of the tech tree system.
 */
public class WebServerConfig {
    
    private int port = 8080;
    private boolean enabled = true;
    private List<String> corsOrigins = List.of("https://guilds-techtree.vercel.app");
    private int sessionTimeoutMinutes = 30;
    
    public WebServerConfig() {
    }

    /**
     * Load web server config from plugin's config.yml.
     * Reads from the "webServer" section if present.
     */
    public static WebServerConfig loadFromConfig(TownyPlugin plugin) {
        WebServerConfig config = new WebServerConfig();
        FileConfiguration fc = plugin.getConfig();
        if (fc.contains("webServer")) {
            config.setEnabled(fc.getBoolean("webServer.enabled", true));
            config.setPort(fc.getInt("webServer.port", 8080));
            config.setSessionTimeoutMinutes(fc.getInt("webServer.sessionTimeoutMinutes", 30));
            if (fc.contains("webServer.corsOrigins")) {
                config.setCorsOrigins(fc.getStringList("webServer.corsOrigins"));
            }
        }
        return config;
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
    
    public List<String> getCorsOrigins() {
        return corsOrigins;
    }
    
    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
    
    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
    
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = Math.max(1, sessionTimeoutMinutes);
    }
}