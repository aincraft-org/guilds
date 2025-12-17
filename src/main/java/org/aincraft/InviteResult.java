package org.aincraft;

/**
 * Result of attempting to send a guild invite.
 */
public class InviteResult {

    public enum Status {
        SUCCESS,
        NO_PERMISSION,
        ALREADY_INVITED,
        ALREADY_IN_GUILD,
        INVITEE_IN_GUILD,
        GUILD_FULL,
        INVITE_LIMIT_REACHED,
        TARGET_NOT_FOUND,
        FAILURE
    }

    private final Status status;
    private final String reason;

    private InviteResult(Status status, String reason) {
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

    public static InviteResult success() {
        return new InviteResult(Status.SUCCESS, "Invite sent successfully");
    }

    public static InviteResult noPermission() {
        return new InviteResult(Status.NO_PERMISSION, "You don't have permission to invite players");
    }

    public static InviteResult alreadyInvited() {
        return new InviteResult(Status.ALREADY_INVITED, "Player already has a pending invite to this guild");
    }

    public static InviteResult alreadyInGuild() {
        return new InviteResult(Status.ALREADY_IN_GUILD, "You are already in a guild");
    }

    public static InviteResult inviteeInGuild() {
        return new InviteResult(Status.INVITEE_IN_GUILD, "Player is already in a guild");
    }

    public static InviteResult guildFull() {
        return new InviteResult(Status.GUILD_FULL, "Guild is full");
    }

    public static InviteResult inviteLimitReached() {
        return new InviteResult(Status.INVITE_LIMIT_REACHED, "Guild has reached maximum pending invites (10)");
    }

    public static InviteResult targetNotFound() {
        return new InviteResult(Status.TARGET_NOT_FOUND, "Player not found");
    }

    public static InviteResult failure(String reason) {
        return new InviteResult(Status.FAILURE, reason);
    }
}
