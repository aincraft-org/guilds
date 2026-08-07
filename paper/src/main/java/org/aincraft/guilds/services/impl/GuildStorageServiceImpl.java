package org.aincraft.guilds.services.impl;

import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.persist.PostgresGuildStorageStore;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.storage.GuildStoragePolicy;
import com.azoth.territory.storage.GuildStorageSnapshot;
import com.azoth.territory.storage.OpaqueItemPayload;
import com.azoth.territory.storage.StorageAddress;
import com.azoth.territory.storage.StorageOpenResult;
import com.azoth.territory.storage.StorageRank;
import com.azoth.territory.storage.StorageResult;
import com.azoth.territory.storage.StorageStatus;
import com.azoth.territory.storage.StorageWithdrawResult;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildStorageService;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorizes guild storage access against the facility registry and the
 * governance registry, then delegates persistence to
 * {@link PostgresGuildStorageStore} without duplicating its status contract.
 *
 * <p>Access rules (no second rule set, {@code BlockProtection} is never
 * consulted): the block must resolve to a registered {@code STORAGE}
 * facility whose governing guild (via
 * {@link GovernanceRegistry#governingGuildForTerritory(String)}) is the
 * actor's resident guild (via {@link GuildService#getAllGuilds()} and
 * {@link Guild#isResident(UUID)}). Mutations additionally require the
 * actor's storage rank to meet the guild's current policy threshold.
 * Persistence failures surface as {@link StorageStatus#STORAGE_ERROR} with
 * no retry and no partial mutation.</p>
 */
public final class GuildStorageServiceImpl implements GuildStorageService {

    /** User-safe denial message shared by every facility-identity failure. */
    private static final String WRONG_FACILITY_MESSAGE = "That is not the facility you are acting on";

    private final GuildService guildService;
    private final FacilityRegistry facilityRegistry;
    private final GovernanceRegistry governanceRegistry;
    private final PostgresGuildStorageStore store;

    public GuildStorageServiceImpl(
            GuildService guildService,
            FacilityRegistry facilityRegistry,
            GovernanceRegistry governanceRegistry,
            PostgresGuildStorageStore store) {
        this.guildService = Objects.requireNonNull(guildService, "guildService");
        this.facilityRegistry = Objects.requireNonNull(facilityRegistry, "facilityRegistry");
        this.governanceRegistry = Objects.requireNonNull(governanceRegistry, "governanceRegistry");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public StorageOpenResult open(UUID actor, String world, int blockX, int blockY, int blockZ) {
        try {
            AuthorizedAccess access = authorize(actor, null, world, blockX, blockY, blockZ);
            GuildStorageSnapshot snapshot = store.ensureBank(access.guildId());
            return new StorageOpenResult(
                    StorageStatus.SUCCESS, "Storage opened", Optional.of(snapshot));
        } catch (DeniedException denied) {
            return new StorageOpenResult(denied.status(), denied.getMessage(), Optional.empty());
        } catch (IOException e) {
            return new StorageOpenResult(
                    StorageStatus.STORAGE_ERROR, "Storage is temporarily unavailable", Optional.empty());
        }
    }

    @Override
    public StorageResult deposit(UUID actor, StorageAddress address, OpaqueItemPayload payload,
                                 String facilityId, String world, int blockX, int blockY, int blockZ) {
        try {
            requireFacilityId(facilityId);
            AuthorizedAccess access = authorize(actor, facilityId, world, blockX, blockY, blockZ);
            requireRank(loadPolicy(access.guildId()).depositRank(), access.rank(), "deposit");
            return store.put(access.guildId(), address, payload, actor, facilityId.trim());
        } catch (DeniedException denied) {
            return new StorageResult(denied.status(), denied.getMessage());
        } catch (IOException e) {
            return new StorageResult(StorageStatus.STORAGE_ERROR, "Storage is temporarily unavailable");
        }
    }

    @Override
    public StorageWithdrawResult withdraw(UUID actor, StorageAddress address,
                                          String facilityId, String world,
                                          int blockX, int blockY, int blockZ) {
        try {
            requireFacilityId(facilityId);
            AuthorizedAccess access = authorize(actor, facilityId, world, blockX, blockY, blockZ);
            requireRank(loadPolicy(access.guildId()).withdrawRank(), access.rank(), "withdraw");
            return store.remove(access.guildId(), address, actor, facilityId.trim());
        } catch (DeniedException denied) {
            return new StorageWithdrawResult(denied.status(), denied.getMessage(), Optional.empty());
        } catch (IOException e) {
            return new StorageWithdrawResult(
                    StorageStatus.STORAGE_ERROR, "Storage is temporarily unavailable", Optional.empty());
        }
    }

    @Override
    public StorageResult setPolicy(UUID actor, String guildId, GuildStoragePolicy policy,
                                   String facilityId, String world,
                                   int blockX, int blockY, int blockZ) {
        try {
            requireFacilityId(facilityId);
            AuthorizedAccess access = authorize(actor, facilityId, world, blockX, blockY, blockZ);
            requireTargetGuild(access, guildId);
            requireRank(loadPolicy(access.guildId()).manageRank(), access.rank(), "manage");
            return store.setPolicy(access.guildId(), policy, actor, facilityId.trim());
        } catch (DeniedException denied) {
            return new StorageResult(denied.status(), denied.getMessage());
        } catch (IOException e) {
            return new StorageResult(StorageStatus.STORAGE_ERROR, "Storage is temporarily unavailable");
        }
    }

    @Override
    public StorageResult unlockTab(UUID actor, String guildId, String tabId, String displayName,
                                   int ordinal, int capacitySlots, String facilityId,
                                   String world, int blockX, int blockY, int blockZ) {
        try {
            requireFacilityId(facilityId);
            AuthorizedAccess access = authorize(actor, facilityId, world, blockX, blockY, blockZ);
            requireTargetGuild(access, guildId);
            requireRank(loadPolicy(access.guildId()).manageRank(), access.rank(), "manage");
            return store.unlockTab(access.guildId(), tabId, displayName,
                    ordinal, capacitySlots, actor, facilityId.trim());
        } catch (DeniedException denied) {
            return new StorageResult(denied.status(), denied.getMessage());
        } catch (IOException e) {
            return new StorageResult(StorageStatus.STORAGE_ERROR, "Storage is temporarily unavailable");
        }
    }

    /**
     * Rejects null or blank facility identity before any registry, policy, or
     * store interaction. Mutations must always name the exact facility the
     * actor is acting on; the resolved-facility equality check below is only
     * meaningful for non-blank ids.
     */
    private static void requireFacilityId(String facilityId) {
        if (facilityId == null || facilityId.trim().isEmpty()) {
            throw new DeniedException(StorageStatus.WRONG_FACILITY, WRONG_FACILITY_MESSAGE);
        }
    }

    /**
     * Resolves the exact facility at the given world/block coordinates and
     * derives the actor's resident guild and storage rank. Returns a denial
     * via {@link DeniedException} when any access rule fails; never touches
     * the persistence store.
     */
    private AuthorizedAccess authorize(UUID actor, String expectedFacilityId,
                                       String world, int blockX, int blockY, int blockZ) {
        SettlementFacility facility = facilityRegistry.resolve(world, blockX, blockY, blockZ)
                .orElseThrow(() -> new DeniedException(
                        StorageStatus.WRONG_FACILITY, "No storage facility at that location"));
        if (facility.type() != FacilityType.STORAGE) {
            throw new DeniedException(
                    StorageStatus.WRONG_FACILITY, "That facility is not guild storage");
        }
        if (expectedFacilityId != null && !facility.id().equals(expectedFacilityId.trim())) {
            throw new DeniedException(StorageStatus.WRONG_FACILITY, WRONG_FACILITY_MESSAGE);
        }
        GuildBody governing = governanceRegistry.governingGuildForTerritory(facility.territoryId())
                .orElseThrow(() -> new DeniedException(
                        StorageStatus.WRONG_GUILD, "This storage is not governed by a guild"));
        Guild residentGuild = null;
        for (Guild guild : guildService.getAllGuilds()) {
            if (guild.isResident(actor)) {
                residentGuild = guild;
                break;
            }
        }
        if (residentGuild == null) {
            throw new DeniedException(
                    StorageStatus.NOT_RESIDENT, "You are not a resident of a guild");
        }
        if (!residentGuild.getId().equals(governing.id())) {
            throw new DeniedException(
                    StorageStatus.WRONG_GUILD, "This storage belongs to another guild");
        }
        return new AuthorizedAccess(residentGuild.getId(), rankOf(residentGuild, actor));
    }

    private static StorageRank rankOf(Guild guild, UUID actor) {
        if (guild.isMayor(actor)) {
            return StorageRank.MAYOR;
        }
        if (guild.isAssistant(actor)) {
            return StorageRank.ASSISTANT;
        }
        return StorageRank.MEMBER;
    }

    private GuildStoragePolicy loadPolicy(String guildId) throws IOException {
        return store.load(guildId).policy();
    }

    private static void requireRank(StorageRank required, StorageRank actual, String action) {
        if (actual.ordinal() < required.ordinal()) {
            throw new DeniedException(StorageStatus.INSUFFICIENT_RANK,
                    "Your rank is too low to " + action + " in guild storage");
        }
    }

    private static void requireTargetGuild(AuthorizedAccess access, String guildId) {
        if (guildId == null || !access.guildId().equals(guildId.trim())) {
            throw new DeniedException(
                    StorageStatus.WRONG_GUILD, "This storage belongs to another guild");
        }
    }

    /** Guild and storage rank of an authorized actor. */
    private record AuthorizedAccess(String guildId, StorageRank rank) {
    }

    /** Control-flow carrier for an authorization denial with its stable status. */
    private static final class DeniedException extends RuntimeException {
        private final StorageStatus status;

        private DeniedException(StorageStatus status, String message) {
            super(message);
            this.status = status;
        }

        private StorageStatus status() {
            return status;
        }
    }
}
