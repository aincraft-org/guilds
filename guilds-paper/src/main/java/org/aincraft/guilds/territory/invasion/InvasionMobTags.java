package org.aincraft.guilds.territory.invasion;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class InvasionMobTags {
    static final String NAMESPACE = "guilds";
    static final String LEGACY_NAMESPACE = "azothterritory";

    private final NamespacedKey invasionKey;
    private final NamespacedKey guildKey;

    public InvasionMobTags(Plugin plugin) {
        this.invasionKey = new NamespacedKey(NAMESPACE, "invasion_id");
        this.guildKey = new NamespacedKey(NAMESPACE, "invasion_guild_id");
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
    public static Optional<UUID> invasionId(PersistentDataContainer pdc) {
        return parseUuid(first(pdc, "invasion_id"));
    }
    public static Optional<String> guildId(PersistentDataContainer pdc) {
        String value = first(pdc, "invasion_guild_id");
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String first(PersistentDataContainer pdc, String key) {
        String current = pdc.get(new NamespacedKey(NAMESPACE, key), PersistentDataType.STRING);
        if (current != null) {
            return current;
        }
        return pdc.get(new NamespacedKey(LEGACY_NAMESPACE, key), PersistentDataType.STRING);
    }
    public static boolean belongsTo(PersistentDataContainer pdc, UUID invasionId, String guildId) {
        return invasionId != null && guildId != null && invasionId(pdc).filter(invasionId::equals).isPresent()
                && guildId(pdc).filter(guildId::equals).isPresent();
    }
    public Optional<UUID> invasionId(Entity entity) { return invasionId(entity.getPersistentDataContainer()); }
    public Optional<String> guildId(Entity entity) { return guildId(entity.getPersistentDataContainer()); }
    public boolean belongsTo(Entity entity, UUID invasionId, String guildId) { return belongsTo(entity.getPersistentDataContainer(), invasionId, guildId); }
    private static Optional<UUID> parseUuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return Optional.empty();
        }
        try { return Optional.of(UUID.fromString(value)); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
