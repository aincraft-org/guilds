package dev.mintychochip.guilds.services;

/**
 * Result of a permission evaluation showing where the permission came from
 */
public class PermissionEvaluationResult {
    /** The has permission. */
    private final boolean hasPermission;
    /** The source. */
    private final String source; // "global", "guild", "plot", "owner", "admin"
    /** The reason. */
    private final String reason;

    /**
     * Creates a new permission evaluation result instance.
     * @param hasPermission the has permission
     * @param source the source
     * @param reason the reason
     */
    public PermissionEvaluationResult(boolean hasPermission, String source, String reason) {
        this.hasPermission = hasPermission;
        this.source = source;
        this.reason = reason;
    }

    /**
     * Returns whether permission.
     * @return the result
     */
    public boolean hasPermission() {
        return hasPermission;
    }

    /**
     * Returns the source.
     * @return the result
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the reason.
     * @return the result
     */
    public String getReason() {
        return reason;
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return String.format("PermissionEvaluationResult{hasPermission=%s, source='%s', reason='%s'}",
                           hasPermission, source, reason);
    }
}