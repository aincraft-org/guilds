package org.aincraft;

/**
 * Result of attempting to accept a guild invite.
 */
public class AcceptInviteResult {

    public enum Status {
        SUCCESS,
        EXPIRED,
        NOT_FOUND,
        ALREADY_IN_GUILD,
        GUILD_FULL,
        GUILD_NOT_FOUND,
        FAILURE
    }

    private final Status status;
    private final String reason;

    private AcceptInviteResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public static AcceptInviteResult success() {
        return new AcceptInviteResult(Status.SUCCESS, "Successfully joined guild");
    }

    public static AcceptInviteResult expired() {
        return new AcceptInviteResult(Status.EXPIRED, "Invite has expired");
    }

    public static AcceptInviteResult notFound() {
        return new AcceptInviteResult(Status.NOT_FOUND, "No pending invite found for this guild");
    }

    public static AcceptInviteResult alreadyInGuild() {
        return new AcceptInviteResult(Status.ALREADY_IN_GUILD, "You are already in a guild");
    }

    public static AcceptInviteResult guildFull() {
        return new AcceptInviteResult(Status.GUILD_FULL, "Guild is full");
    }

    public static AcceptInviteResult guildNotFound() {
        return new AcceptInviteResult(Status.GUILD_NOT_FOUND, "Guild no longer exists");
    }

    public static AcceptInviteResult failure(String reason) {
        return new AcceptInviteResult(Status.FAILURE, reason);
    }
}
