package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.PolicyRules;

import java.util.Objects;
import java.util.Set;

/**
 * Form-based formal authority grants for sovereign actions.
 * <p>
 * Pure domain — no Bukkit. Uses filled authority seats from {@link PolicyRules#electorate}.
 * <ul>
 *   <li>{@code ANARCHY} — no formal grants for anyone</li>
 *   <li>{@code MONARCHY} — only the filled sovereign seat</li>
 *   <li>{@code OLIGARCHY} / {@code DEMOCRACY} — each filled authority-role seat holder</li>
 * </ul>
 */
public final class PermissionRules {
    private PermissionRules() {
    }

    /**
     * Whether {@code actorId} has formal authority under {@code government} for {@code action}.
     */
    public static boolean allows(Government government, String actorId, SovereignAction action) {
        Objects.requireNonNull(action, "action");
        if (government == null || !government.isAssigned()) {
            return false;
        }
        if (actorId == null || actorId.isBlank()) {
            return false;
        }
        Set<String> authority = PolicyRules.electorate(government);
        return authority.contains(actorId.trim());
    }
}
