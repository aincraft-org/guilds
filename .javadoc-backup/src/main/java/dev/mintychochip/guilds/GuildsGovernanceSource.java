package dev.mintychochip.guilds;

import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.guilds.GovernanceSource;
import dev.mintychochip.guilds.MemberPermissions;
import dev.mintychochip.territory.permission.SovereignAction;
import dev.mintychochip.guilds.GuildToggles;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Alliance;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;

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
 * {@link dev.mintychochip.guilds.Guild} (local government entities), alliances as
 * {@link dev.mintychochip.guilds.alliances.Alliance} (alliance entities).
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
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The default form constant. */
    private static final String DEFAULT_FORM = "MONARCHY";

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The guild service. */
    private final GuildService guildService;
    /** The alliance service. */
    private final AllianceService allianceService;
    /** The logger. */
    private final Logger logger;

    /**
     * Creates a new guilds governance source instance.
     * @param databaseManager the database manager
     * @param guildService the guild service
     * @param allianceService the alliance service
     * @param logger the logger
     */
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

    /**
     * Performs the guild operation.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public Optional<dev.mintychochip.guilds.Guild> guild(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        return guildService.getGuildById(guildId.trim()).map(this::toGuildBody);
    }

    /**
     * Performs the guilds for member operation.
     * @param holderId the holder id
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.Guild> guildsForMember(String holderId) {
        if (holderId == null || holderId.isBlank()) {
            return List.of();
        }
        UUID uuid = parseUuid(holderId.trim());
        if (uuid == null) {
            return List.of();
        }
        List<dev.mintychochip.guilds.Guild> matches = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            if (guild.isResident(uuid)) {
                matches.add(toGuildBody(guild));
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    /**
     * Performs the alliance containing guild operation.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public Optional<dev.mintychochip.guilds.alliances.Alliance> allianceContainingGuild(String guildId) {
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

    /**
     * Performs the all guilds operation.
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.Guild> allGuilds() {
        List<dev.mintychochip.guilds.Guild> bodies = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            bodies.add(toGuildBody(guild));
        }
        return List.copyOf(bodies);
    }

    /**
     * Performs the all alliances operation.
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.alliances.Alliance> allAlliances() {
        List<dev.mintychochip.guilds.alliances.Alliance> bodies = new ArrayList<>();
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

    /**
     * Performs the to guild body operation.
     * @param guild the guild
     * @return the result
     */
    private dev.mintychochip.guilds.Guild toGuildBody(Guild guild) {
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
        return new dev.mintychochip.guilds.Guild(
                guild.getId(), guild.getName(), government, memberIds, toggles, permissions
        );
    }

    /**
     * Performs the guild authority ids operation.
     * @param guild the guild
     * @param form the form
     * @return the result
     */
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

    /**
     * Performs the to alliance body operation.
     * @param alliance the alliance
     * @return the result
     */
    private dev.mintychochip.guilds.alliances.Alliance toAllianceBody(Alliance alliance) {
        GovernmentForm form = readForm("alliances", "id", alliance.getId());
        List<String> authorityIds = allianceAuthorityIds(alliance, form);
        Government government = Government.fromRoles(form, authorityIds);

        List<String> memberGuildIds = new ArrayList<>(alliance.getMemberGuildIds());
        memberGuildIds.sort(String::compareTo);
        return new dev.mintychochip.guilds.alliances.Alliance(
                alliance.getId(), alliance.getName(), government, memberGuildIds
        );
    }

    /**
     * Performs the alliance authority ids operation.
     * @param alliance the alliance
     * @param form the form
     * @return the result
     */
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
                "governance/select-global-flags.sql",
                Map.of("target_id", resident.toString())
        );
        for (int flags : globalFlags) {
            if (hasFlag(flags, GuildPermissionBit.BYPASS)) {
                bypass = true;
            }
            addMappedActions(granted, flags);
        }

        // 2. Explicit guild-context grants
        List<Integer> guildFlags = queryFlags(
                "governance/select-guild-flags.sql",
                Map.of("context_id", guild.getName(), "target_id", resident.toString())
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

    /**
     * Adds the mapped actions.
     * @param granted the granted
     * @param flags the flags
     */
    private void addMappedActions(Set<SovereignAction> granted, int flags) {
        for (Map.Entry<Integer, SovereignAction> e : FLAG_TO_ACTION.entrySet()) {
            if (hasFlag(flags, e.getKey())) {
                granted.add(e.getValue());
            }
        }
    }

    /**
     * Returns whether flag.
     * @param flags the flags
     * @param bit the bit
     * @return the result
     */
    private boolean hasFlag(int flags, int bit) {
        return (flags & bit) != 0;
    }

    /**
     * Performs the query flags operation.
     * @param relativePath the relative path
     * @param params the params
     * @return the result
     */
    private List<Integer> queryFlags(String relativePath, Map<String, ?> params) {
        List<Integer> flags = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = SQL.prepare(connection, relativePath, params)) {
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

    /**
     * Performs the read form operation.
     * @param table the table
     * @param idColumn the id column
     * @param id the id
     * @return the result
     */
    private GovernmentForm readForm(String table, String idColumn, String id) {
        var parsed = SQL.sql("governance/select-form.sql")
                .withIdentifiers(Map.of("table", table, "id_column", idColumn));
        Map<String, Object> params = Map.of("id", id);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(parsed.jdbcSql(params))) {
            parsed.bind(statement, params);
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

    /**
     * Sets the form.
     * @param table the table
     * @param idColumn the id column
     * @param id the id
     * @param form the form
     * @return the result
     */
    private boolean setForm(String table, String idColumn, String id, GovernmentForm form) {
        var parsed = SQL.sql("governance/update-form.sql")
                .withIdentifiers(Map.of("table", table, "id_column", idColumn));
        Map<String, Object> params = Map.of("form", form.name(), "id", id);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(parsed.jdbcSql(params))) {
            parsed.bind(statement, params);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to set governance form for " + table + " " + id, e);
            return false;
        }
    }

    /**
     * Parses the uuid.
     * @param raw the raw
     * @return the result
     */
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Performs the sorted operation.
     * @param uuids the uuids
     * @return the result
     */
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
        /** The build constant. */
        static final int BUILD = dev.mintychochip.guilds.models.GuildPermission.BUILD.getLegacyBitwiseValue();
        /** The destroy constant. */
        static final int DESTROY = dev.mintychochip.guilds.models.GuildPermission.DESTROY.getLegacyBitwiseValue();
        /** The switch constant. */
        static final int SWITCH = dev.mintychochip.guilds.models.GuildPermission.SWITCH.getLegacyBitwiseValue();
        /** The item use constant. */
        static final int ITEM_USE = dev.mintychochip.guilds.models.GuildPermission.ITEM_USE.getLegacyBitwiseValue();
        /** The bypass constant. */
        static final int BYPASS = dev.mintychochip.guilds.models.GuildPermission.BYPASS.getLegacyBitwiseValue();
    }

    /** The flag to action constant. */
    private static final Map<Integer, SovereignAction> FLAG_TO_ACTION = buildFlagMapping();

    /**
     * Builds the flag mapping.
     * @return the result
     */
    private static Map<Integer, SovereignAction> buildFlagMapping() {
        Map<Integer, SovereignAction> map = new java.util.HashMap<>();
        map.put(GuildPermissionBit.BUILD, SovereignAction.PLACE_BLOCK);
        map.put(GuildPermissionBit.DESTROY, SovereignAction.BREAK_BLOCK);
        map.put(GuildPermissionBit.SWITCH, SovereignAction.INTERACT);
        map.put(GuildPermissionBit.ITEM_USE, SovereignAction.INTERACT);
        return Map.copyOf(map);
    }
}
