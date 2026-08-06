package com.azoth.territory.permission;

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
 */
public record MemberPermissions(Set<SovereignAction> grantedActions, boolean bypass) {

    public MemberPermissions {
        Objects.requireNonNull(grantedActions, "grantedActions");
        EnumSet<SovereignAction> copy = EnumSet.noneOf(SovereignAction.class);
        copy.addAll(grantedActions);
        grantedActions = copy;
    }

    public static MemberPermissions none() {
        return new MemberPermissions(Set.of(), false);
    }

    public static MemberPermissions of(Collection<SovereignAction> grantedActions) {
        EnumSet<SovereignAction> copy = EnumSet.noneOf(SovereignAction.class);
        if (grantedActions != null) {
            copy.addAll(grantedActions);
        }
        return new MemberPermissions(copy, false);
    }

    public static MemberPermissions fullBypass() {
        return new MemberPermissions(Set.of(), true);
    }

    /**
     * Whether this member may perform {@code action} on governed land.
     */
    public boolean allows(SovereignAction action) {
        Objects.requireNonNull(action, "action");
        return bypass || grantedActions.contains(action);
    }

    public MemberPermissions withGranted(Collection<SovereignAction> additional) {
        EnumSet<SovereignAction> next = EnumSet.noneOf(SovereignAction.class);
        next.addAll(grantedActions);
        if (additional != null) {
            next.addAll(additional);
        }
        return new MemberPermissions(next, bypass);
    }
}
