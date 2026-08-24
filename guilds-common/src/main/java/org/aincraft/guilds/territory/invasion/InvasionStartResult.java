package org.aincraft.guilds.territory.invasion;

import java.util.UUID;

public record InvasionStartResult(InvasionStartStatus status, UUID invasionId) {
    public InvasionStartResult(InvasionStartStatus status) { this(status, null); }
}
