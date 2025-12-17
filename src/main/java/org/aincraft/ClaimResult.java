package org.aincraft;

/**
 * Represents the result of attempting to claim a chunk.
 */
public class ClaimResult {
    private final Status status;
    private final String reason;

    public enum Status {
        SUCCESS,
        NO_PERMISSION,
        NOT_ADJACENT,
        ALREADY_CLAIMED,
        ALREADY_OWNED,
        LIMIT_EXCEEDED,
        TOO_CLOSE_TO_GUILD,
        FAILURE
    }

    private ClaimResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static ClaimResult success() {
        return new ClaimResult(Status.SUCCESS, null);
    }

    public static ClaimResult noPermission() {
        return new ClaimResult(Status.NO_PERMISSION, "You don't have CLAIM permission");
    }

    public static ClaimResult notAdjacent() {
        return new ClaimResult(Status.NOT_ADJACENT, "This chunk is not adjacent to your guild territory (you must expand contiguously)");
    }

    public static ClaimResult alreadyClaimed(String guildName) {
        return new ClaimResult(Status.ALREADY_CLAIMED, "This chunk is already claimed by " + guildName);
    }

    public static ClaimResult alreadyOwned() {
        return new ClaimResult(Status.ALREADY_OWNED, "This chunk is already owned by your guild");
    }

    public static ClaimResult limitExceeded(int maxChunks) {
        return new ClaimResult(Status.LIMIT_EXCEEDED, "Your guild has reached the claim limit of " + maxChunks + " chunks");
    }

    public static ClaimResult failure(String reason) {
        return new ClaimResult(Status.FAILURE, reason);
    }

    public static ClaimResult tooCloseToGuild(String guildName, int bufferDistance, int actualDistance) {
        return new ClaimResult(Status.TOO_CLOSE_TO_GUILD,
            String.format("Cannot claim: too close to guild '%s' (buffer: %d chunks, distance: %d chunks)",
                guildName, bufferDistance, actualDistance));
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
