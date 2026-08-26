package dev.mintychochip.guilds;
import dev.mintychochip.territory.permission.SovereignAction;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Effective territory permissions of one member of a governing guild, as
 * materialized by the guilds subsystem.
 * <p>
 * Mirrors the guilds guild-level permission hierarchy:
 * <ol>
 *   <li>global {@code bypass} — member passes every sovereign action</li>
 *   <li>explicit guild-context grants — add granted actions</li>
 *   <li>role default — all guild members get the basic build actions
 *       (break/place/switch/item-use) by default</li>
 * </ol>
 * Pure domain — no Bukkit.
 *
 * @param grantedActions explicitly granted sovereign actions
 * @param bypass whether all sovereign actions are allowed
 */
public record MemberPermissions(Set<SovereignAction> grantedActions, boolean bypass) {

    /** Creates an immutable defensive copy of the granted actions. */
    public MemberPermissions {
        Objects.requireNonNull(grantedActions, "grantedActions");
        EnumSet<SovereignAction> copy = EnumSet.noneOf(SovereignAction.class);
        copy.addAll(grantedActions);
        grantedActions = copy;
    }

    /** Returns permissions with no granted actions and no bypass.
     *
     * @return permissions with no granted actions and no bypass
     */
    public static MemberPermissions none() {
        return new MemberPermissions(Set.of(), false);
    }

    /**
     * Creates permissions granting the supplied actions without bypass.
     *
     * @param grantedActions actions to grant; {@code null} means none
     * @return permissions containing the supplied grants
     */
    public static MemberPermissions of(Collection<SovereignAction> grantedActions) {
        EnumSet<SovereignAction> copy = EnumSet.noneOf(SovereignAction.class);
        if (grantedActions != null) {
            copy.addAll(grantedActions);
        }
        return new MemberPermissions(copy, false);
    }

    /** Returns permissions that bypass all sovereign action checks.
     *
     * @return permissions that bypass all sovereign action checks
     */
    public static MemberPermissions fullBypass() {
        return new MemberPermissions(Set.of(), true);
    }
    /**
     * Whether this member may perform {@code action} on governed land.
     *
     * @param action action to check
     * @return whether the member may perform the action
     */
    public boolean allows(SovereignAction action) {
        Objects.requireNonNull(action, "action");
        return bypass || grantedActions.contains(action);
    }

    /**
     * Returns a copy with the additional actions granted.
     *
     * @param additional actions to add; may be {@code null}
     * @return permissions containing the existing and additional grants
     */
    public MemberPermissions withGranted(Collection<SovereignAction> additional) {
        EnumSet<SovereignAction> next = EnumSet.noneOf(SovereignAction.class);
        next.addAll(grantedActions);
        if (additional != null) {
            next.addAll(additional);
        }
        return new MemberPermissions(next, bypass);
    }
}
