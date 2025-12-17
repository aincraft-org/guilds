package org.aincraft;

/**
 * Represents the result of attempting to leave a guild.
 */
public class LeaveResult {
    private final Status status;
    private final String reason;

    public enum Status {
        SUCCESS,
        NOT_IN_GUILD,
        OWNER_CANNOT_LEAVE,
        FAILURE
    }

    private LeaveResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static LeaveResult success() {
        return new LeaveResult(Status.SUCCESS, null);
    }

    public static LeaveResult notInGuild() {
        return new LeaveResult(Status.NOT_IN_GUILD, "You are not a member of this guild");
    }

    public static LeaveResult ownerCannotLeave() {
        return new LeaveResult(Status.OWNER_CANNOT_LEAVE, "Guild owners cannot leave. Delete the guild or transfer ownership first");
    }

    public static LeaveResult failure(String reason) {
        return new LeaveResult(Status.FAILURE, reason);
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
