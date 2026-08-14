package com.azoth.territory.invasion;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class InvasionMobTags {
    private final NamespacedKey invasionKey;
    private final NamespacedKey guildKey;

    public InvasionMobTags(Plugin plugin) {
        this.invasionKey = new NamespacedKey(plugin, "invasion_id");
        this.guildKey = new NamespacedKey(plugin, "invasion_guild_id");
    }

    public static void tag(Entity entity, UUID invasionId, String guildId, Plugin plugin) {
        new InvasionMobTags(plugin).tag(entity, invasionId, guildId);
    }

    public void tag(Entity entity, UUID invasionId, String guildId) {
        if (entity == null || invasionId == null || guildId == null || guildId.isBlank()) throw new IllegalArgumentException();
        tag(entity.getPersistentDataContainer(), invasionId, guildId);
    }

    public void tag(PersistentDataContainer pdc, UUID invasionId, String guildId) {
        pdc.set(invasionKey, PersistentDataType.STRING, invasionId.toString());
        pdc.set(guildKey, PersistentDataType.STRING, guildId);
    }
    public static Optional<UUID> invasionId(PersistentDataContainer pdc) { return parseUuid(find(pdc, "invasion_id")); }
    public static Optional<String> guildId(PersistentDataContainer pdc) {
        String value = find(pdc, "invasion_guild_id");
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
    public static boolean belongsTo(PersistentDataContainer pdc, UUID invasionId, String guildId) {
        return invasionId != null && guildId != null && invasionId(pdc).filter(invasionId::equals).isPresent()
                && guildId(pdc).filter(guildId::equals).isPresent();
    }
    private static String find(PersistentDataContainer pdc, String key) {
        for (var namespaced : new String[]{"invasion_id", "invasion_guild_id"}) {
            for (var namespace : new String[]{"azothterritory", "territory"}) {
                String value = pdc.get(new NamespacedKey(namespace, key), PersistentDataType.STRING);
                if (value != null) return value;
            }
        }
        return null;
    }
    public Optional<UUID> invasionId(Entity entity) { return invasionId(entity.getPersistentDataContainer()); }
    public Optional<String> guildId(Entity entity) { return guildId(entity.getPersistentDataContainer()); }
    public boolean belongsTo(Entity entity, UUID invasionId, String guildId) { return belongsTo(entity.getPersistentDataContainer(), invasionId, guildId); }
    private static Optional<UUID> parseUuid(String value) { try { return value == null ? Optional.empty() : Optional.of(UUID.fromString(value)); } catch (IllegalArgumentException e) { return Optional.empty(); } }
}
