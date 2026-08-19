package org.aincraft.guilds;

import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.GovernmentForm;
import org.aincraft.guilds.territory.permission.AllianceBody;
import org.aincraft.guilds.territory.permission.GovernanceSource;
import org.aincraft.guilds.territory.permission.GuildBody;
import org.aincraft.guilds.territory.permission.MemberPermissions;
import org.aincraft.guilds.territory.permission.SovereignAction;
import org.aincraft.guilds.territory.permission.GuildToggles;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link GovernanceSource} backed by the guilds database: guilds materialize as
 * {@link GuildBody} (local government entities), alliances as {@link AllianceBody}
 * (alliance entities).
 * <p>
 * Government derivation: each entity picks a governance form (stored in the
 * {@code governance_form} column, default {@code MONARCHY}); seats are derived
 * from role holders — guild mayor/assistants/residents, alliance
 * king/ministers/member-guild mayors — via {@link Government#fromRoles}.
 * <p>
 * Member permissions mirror the guilds guild-level hierarchy:
 * <ol>
 *   <li>global {@code bypass} grants every sovereign action;</li>
 *   <li>explicit guild-context grants (permissions table) add actions;</li>
 *   <li>role default — every guild member gets the basic build actions
 *       (break/place/switch/item-use) by default, matching the guilds
 *       {@code hasRoleBasedGuildPermission} guild semantics.</li>
 * </ol>
 */
public final class GuildsGovernanceSource implements GovernanceSource {

    private static final String DEFAULT_FORM = "MONARCHY";

    private final DatabaseManager databaseManager;
    private final GuildService guildService;
    private final AllianceService allianceService;
    private final Logger logger;

    public GuildsGovernanceSource(
            DatabaseManager databaseManager,
            GuildService guildService,
            AllianceService allianceService,
            Logger logger
    ) {
        this.databaseManager = databaseManager;
        this.guildService = guildService;
        this.allianceService = allianceService;
        this.logger = logger;
    }

    @Override
    public Optional<GuildBody> guild(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        return guildService.getGuildById(guildId.trim()).map(this::toGuildBody);
    }

    @Override
    public List<GuildBody> guildsForMember(String holderId) {
        if (holderId == null || holderId.isBlank()) {
            return List.of();
        }
        UUID uuid = parseUuid(holderId.trim());
        if (uuid == null) {
            return List.of();
        }
        List<GuildBody> matches = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            if (guild.isResident(uuid)) {
                matches.add(toGuildBody(guild));
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    @Override
    public Optional<AllianceBody> allianceContainingGuild(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        String id = guildId.trim();
        for (Alliance alliance : allianceService.getAllAlliances()) {
            if (alliance.hasGuild(id)) {
                return Optional.of(toAllianceBody(alliance));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<GuildBody> allGuilds() {
        List<GuildBody> bodies = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            bodies.add(toGuildBody(guild));
        }
        return List.copyOf(bodies);
    }

    @Override
    public List<AllianceBody> allAlliances() {
        List<AllianceBody> bodies = new ArrayList<>();
        for (Alliance alliance : allianceService.getAllAlliances()) {
            bodies.add(toAllianceBody(alliance));
        }
        return List.copyOf(bodies);
    }

    /**
     * Set the governance form for a guild (guild).
     *
     * @return true if the guild exists and the form was persisted
     */
    public boolean setGuildForm(String guildId, GovernmentForm form) {
        if (guildId == null || guildId.isBlank() || form == null) {
            return false;
        }
        return guildService.getGuildById(guildId.trim())
                .map(guild -> setForm("guilds", "id", guild.getId(), form))
                .orElse(false);
    }

    /**
     * Set the governance form for an alliance (alliance).
     *
     * @return true if the alliance exists and the form was persisted
     */
    public boolean setAllianceForm(String allianceId, GovernmentForm form) {
        if (allianceId == null || allianceId.isBlank() || form == null) {
            return false;
        }
        return allianceService.getAllianceById(allianceId.trim())
                .map(alliance -> setForm("alliances", "id", alliance.getId(), form))
                .orElse(false);
    }

    // ---- materialization -------------------------------------------------

    private GuildBody toGuildBody(Guild guild) {
        GovernmentForm form = readForm("guilds", "id", guild.getId());
        List<String> authorityIds = guildAuthorityIds(guild, form);
        Government government = Government.fromRoles(form, authorityIds);

        List<String> memberIds = new ArrayList<>();
        for (UUID resident : guild.getResidents()) {
            memberIds.add(resident.toString());
        }
        memberIds.sort(String::compareTo);

        Map<String, MemberPermissions> permissions = new java.util.HashMap<>();
        for (UUID resident : guild.getResidents()) {
            permissions.put(resident.toString(), memberPermissions(guild, resident));
        }

        GuildToggles toggles = new GuildToggles(
                guild.isPvpEnabled(),
                guild.isFireEnabled(),
                guild.isExplosionsEnabled(),
                guild.isMobsEnabled(),
                guild.isPublicEnabled()
        );
        return new GuildBody(guild.getId(), guild.getName(), government, memberIds, toggles, permissions);
    }

    private List<String> guildAuthorityIds(Guild guild, GovernmentForm form) {
        return switch (form) {
            case MONARCHY -> List.of(guild.getMayorUuid().toString());
            case OLIGARCHY -> {
                Set<String> ids = new LinkedHashSet<>();
                ids.add(guild.getMayorUuid().toString());
                for (UUID assistant : sorted(guild.getAssistants())) {
                    ids.add(assistant.toString());
                }
                yield new ArrayList<>(ids);
            }
            case DEMOCRACY -> {
                List<String> ids = new ArrayList<>();
                for (UUID resident : sorted(guild.getResidents())) {
                    ids.add(resident.toString());
                }
                yield ids;
            }
            case ANARCHY -> List.of();
        };
    }

    private AllianceBody toAllianceBody(Alliance alliance) {
        GovernmentForm form = readForm("alliances", "id", alliance.getId());
        List<String> authorityIds = allianceAuthorityIds(alliance, form);
        Government government = Government.fromRoles(form, authorityIds);

        List<String> memberGuildIds = new ArrayList<>(alliance.getMemberGuildIds());
        memberGuildIds.sort(String::compareTo);
        return new AllianceBody(alliance.getId(), alliance.getName(), government, memberGuildIds);
    }

    private List<String> allianceAuthorityIds(Alliance alliance, GovernmentForm form) {
        return switch (form) {
            case MONARCHY -> alliance.getKingUuid() == null
                    ? List.of()
                    : List.of(alliance.getKingUuid().toString());
            case OLIGARCHY -> {
                Set<String> ids = new LinkedHashSet<>();
                if (alliance.getKingUuid() != null) {
                    ids.add(alliance.getKingUuid().toString());
                }
                for (UUID minister : sorted(alliance.getMinisters())) {
                    ids.add(minister.toString());
                }
                yield new ArrayList<>(ids);
            }
            case DEMOCRACY -> {
                // Every member-guild mayor is a representative.
                List<String> ids = new ArrayList<>();
                for (String guildId : alliance.getMemberGuildIds()) {
                    guildService.getGuildById(guildId)
                            .map(Guild::getMayorUuid)
                            .map(UUID::toString)
                            .ifPresent(ids::add);
                }
                yield ids;
            }
            case ANARCHY -> List.of();
        };
    }

    /**
     * Effective territory permissions for one member, mirroring the guilds
     * guild-level hierarchy (bypass → explicit guild grants → role default).
     */
    private MemberPermissions memberPermissions(Guild guild, UUID resident) {
        Set<SovereignAction> granted = EnumSet.noneOf(SovereignAction.class);
        boolean bypass = false;

        // 1. Global bypass rows (context='global', target all or this resident)
        List<Integer> globalFlags = queryFlags(
                "SELECT permissions_flags FROM permissions WHERE context = 'global' "
                        + "AND (target_type = 'all' OR (target_type = 'resident' AND target_id = ?))",
                resident.toString()
        );
        for (int flags : globalFlags) {
            if (hasFlag(flags, GuildPermissionBit.BYPASS)) {
                bypass = true;
            }
            addMappedActions(granted, flags);
        }

        // 2. Explicit guild-context grants
        List<Integer> guildFlags = queryFlags(
                "SELECT permissions_flags FROM permissions WHERE context = 'town' AND context_id = ? "
                        + "AND (target_type = 'all' OR (target_type = 'resident' AND target_id = ?))",
                guild.getName(), resident.toString()
        );
        for (int flags : guildFlags) {
            addMappedActions(granted, flags);
        }

        // 3. Role default: all guild members get the basic build actions
        //    (matches hasRoleBasedGuildPermission: build/destroy/switch/item_use).
        granted.add(SovereignAction.BREAK_BLOCK);
        granted.add(SovereignAction.PLACE_BLOCK);
        granted.add(SovereignAction.INTERACT);

        return new MemberPermissions(granted, bypass);
    }

    private void addMappedActions(Set<SovereignAction> granted, int flags) {
        for (Map.Entry<Integer, SovereignAction> e : FLAG_TO_ACTION.entrySet()) {
            if (hasFlag(flags, e.getKey())) {
                granted.add(e.getValue());
            }
        }
    }

    private boolean hasFlag(int flags, int bit) {
        return (flags & bit) != 0;
    }

    private List<Integer> queryFlags(String sql, String... params) {
        List<Integer> flags = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    flags.add(rs.getInt("permissions_flags"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read governance permission flags", e);
        }
        return flags;
    }

    private GovernmentForm readForm(String table, String idColumn, String id) {
        String sql = "SELECT governance_form FROM " + table + " WHERE " + idColumn + " = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return GovernmentForm.fromString(rs.getString("governance_form"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read governance form for " + table + " " + id, e);
        }
        return GovernmentForm.fromString(DEFAULT_FORM);
    }

    private boolean setForm(String table, String idColumn, String id, GovernmentForm form) {
        String sql = "UPDATE " + table + " SET governance_form = ? WHERE " + idColumn + " = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, form.name());
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to set governance form for " + table + " " + id, e);
            return false;
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<UUID> sorted(Set<UUID> uuids) {
        List<UUID> list = new ArrayList<>(uuids);
        list.sort(UUID::compareTo);
        return list;
    }

    /**
     * GuildPermission legacy bit values for the flags the territory layer maps
     * to sovereign actions (build/destroy/switch/item_use + bypass).
     */
    private static final class GuildPermissionBit {
        static final int BUILD = org.aincraft.guilds.models.GuildPermission.BUILD.getLegacyBitwiseValue();
        static final int DESTROY = org.aincraft.guilds.models.GuildPermission.DESTROY.getLegacyBitwiseValue();
        static final int SWITCH = org.aincraft.guilds.models.GuildPermission.SWITCH.getLegacyBitwiseValue();
        static final int ITEM_USE = org.aincraft.guilds.models.GuildPermission.ITEM_USE.getLegacyBitwiseValue();
        static final int BYPASS = org.aincraft.guilds.models.GuildPermission.BYPASS.getLegacyBitwiseValue();
    }

    private static final Map<Integer, SovereignAction> FLAG_TO_ACTION = buildFlagMapping();

    private static Map<Integer, SovereignAction> buildFlagMapping() {
        Map<Integer, SovereignAction> map = new java.util.HashMap<>();
        map.put(GuildPermissionBit.BUILD, SovereignAction.PLACE_BLOCK);
        map.put(GuildPermissionBit.DESTROY, SovereignAction.BREAK_BLOCK);
        map.put(GuildPermissionBit.SWITCH, SovereignAction.INTERACT);
        map.put(GuildPermissionBit.ITEM_USE, SovereignAction.INTERACT);
        return Map.copyOf(map);
    }
}
