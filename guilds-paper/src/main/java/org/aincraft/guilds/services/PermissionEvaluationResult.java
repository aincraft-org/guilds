package org.aincraft.guilds.services;

/**
 * Result of a permission evaluation showing where the permission came from
 */
public class PermissionEvaluationResult {
    private final boolean hasPermission;
    private final String source; // "global", "guild", "plot", "owner", "admin"
    private final String reason;

    public PermissionEvaluationResult(boolean hasPermission, String source, String reason) {
        this.hasPermission = hasPermission;
        this.source = source;
        this.reason = reason;
    }

    public boolean hasPermission() {
        return hasPermission;
    }

    public String getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("PermissionEvaluationResult{hasPermission=%s, source='%s', reason='%s'}",
                           hasPermission, source, reason);
    }
}