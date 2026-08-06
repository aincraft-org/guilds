package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a guild blueprint containing serialized block data for building templates.
 */
public class Blueprint {
    private String id;
    private String name;
    private UUID authorUuid;
    private String guildId;
    private byte[] schematicData;
    private LocalDateTime createdAt;

    public Blueprint(String id, String name, UUID authorUuid, String guildId, byte[] schematicData, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.authorUuid = authorUuid;
        this.guildId = guildId;
        this.schematicData = schematicData;
        this.createdAt = createdAt;
    }

    public Blueprint(String id, String name, UUID authorUuid, String guildId) {
        this.id = id;
        this.name = name;
        this.authorUuid = authorUuid;
        this.guildId = guildId;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public void setAuthorUuid(UUID authorUuid) {
        this.authorUuid = authorUuid;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public byte[] getSchematicData() {
        return schematicData;
    }

    public void setSchematicData(byte[] schematicData) {
        this.schematicData = schematicData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}