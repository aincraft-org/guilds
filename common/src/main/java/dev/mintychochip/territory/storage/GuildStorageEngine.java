package dev.mintychochip.territory.storage;

import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facility-bound guild item bank. Mutations fail closed and keep one viewer
 * per guild.
 */
public final class GuildStorageEngine implements GuildStorageService {
    private final TerritoryRegistry territories;
    private final FacilityRegistry facilities;
    private final GuildStorageAccess access;
    private final GuildStorageStore store;
    private final ConcurrentHashMap<String, UUID> sessions = new ConcurrentHashMap<>();

    /**
     * Creates an engine.
     *
     * @param territories territory registry
     * @param facilities facility directory
     * @param access rank checks
     * @param store durable bank documents
     */
    public GuildStorageEngine(TerritoryRegistry territories, FacilityRegistry facilities,
                              GuildStorageAccess access, GuildStorageStore store) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.access = Objects.requireNonNull(access, "access");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public synchronized StorageResult open(UUID actor, String worldId, int x, int y, int z) {
        return resolve(actor, worldId, x, y, z, false, false).map(context -> {
            StorageStatus session = acquire(actor, context.guildId);
            if (session != null) {
                return StorageResult.denied(session);
            }
            return opened(context, actor);
        }).orElseGet(() -> resolveDenial(actor, worldId, x, y, z));
    }

    @Override
    public synchronized StorageResult save(UUID actor, String guildId, int expectedRevision,
                                           List<StorageSlot> slots) {
        Objects.requireNonNull(actor, "actor");
        if (guildId == null || guildId.isBlank()) {
            return StorageResult.denied(StorageStatus.UNAVAILABLE);
        }
        if (!ownsSession(actor, guildId)) {
            return StorageResult.denied(StorageStatus.DENIED_IN_USE);
        }
        if (!access.canDeposit(actor, guildId) && !access.canWithdraw(actor, guildId)) {
            return StorageResult.denied(StorageStatus.DENIED_NO_PERMISSION);
        }
        try {
            GuildStorageDocument current = loadOrEmpty(guildId);
            if (current.revision() != expectedRevision) {
                return StorageResult.denied(StorageStatus.CONFLICT);
            }
            List<StorageSlot> normalized = normalize(slots, current.capacitySlots());
            if (normalized == null) {
                return StorageResult.denied(StorageStatus.DENIED_CAPACITY);
            }
            GuildStorageDocument next = new GuildStorageDocument(
                    guildId, current.capacitySlots(), current.revision() + 1, normalized);
            store.save(next);
            return new StorageResult(StorageStatus.SAVED, view(guildId, "saved", next, actor), null);
        } catch (IOException e) {
            return StorageResult.denied(StorageStatus.UNAVAILABLE);
        }
    }

    @Override
    public synchronized StorageResult close(UUID actor, String guildId) {
        Objects.requireNonNull(actor, "actor");
        if (guildId == null || guildId.isBlank() || !ownsSession(actor, guildId)) {
            return StorageResult.denied(StorageStatus.DENIED_IN_USE);
        }
        sessions.remove(guildId);
        return new StorageResult(StorageStatus.CLOSED, null, null);
    }

    @Override
    public synchronized StorageResult deposit(UUID actor, String worldId, int x, int y, int z,
                                              int slotIndex, OpaqueItemPayload item) {
        Objects.requireNonNull(item, "item");
        return mutate(actor, worldId, x, y, z, true, false, context -> {
            if (slotIndex < 0 || slotIndex >= context.document.capacitySlots()) {
                return StorageResult.denied(StorageStatus.DENIED_CAPACITY);
            }
            Map<Integer, StorageSlot> slots = index(context.document.slots());
            slots.put(slotIndex, new StorageSlot(slotIndex, item));
            return persist(context, slots, StorageStatus.DEPOSITED, null);
        });
    }

    @Override
    public synchronized StorageResult withdraw(UUID actor, String worldId, int x, int y, int z,
                                               int slotIndex) {
        return mutate(actor, worldId, x, y, z, false, true, context -> {
            Map<Integer, StorageSlot> slots = index(context.document.slots());
            StorageSlot existing = slots.remove(slotIndex);
            if (existing == null) {
                return StorageResult.denied(StorageStatus.DENIED_EMPTY_SLOT);
            }
            return persist(context, slots, StorageStatus.WITHDRAWN, existing.item());
        });
    }

    /**
     * Releases a session when the viewer disconnects.
     *
     * @param actor player
     */
    public synchronized void release(UUID actor) {
        sessions.entrySet().removeIf(entry -> entry.getValue().equals(actor));
    }

    private StorageResult mutate(UUID actor, String worldId, int x, int y, int z,
                                 boolean needDeposit, boolean needWithdraw, Mutator mutator) {
        Optional<Context> resolved = resolve(actor, worldId, x, y, z, needDeposit, needWithdraw);
        if (resolved.isEmpty()) {
            return resolveDenial(actor, worldId, x, y, z, needDeposit, needWithdraw);
        }
        Context context = resolved.get();
        UUID holder = sessions.get(context.guildId);
        if (holder != null && !holder.equals(actor)) {
            return StorageResult.denied(StorageStatus.DENIED_IN_USE);
        }
        return mutator.apply(context);
    }

    private Optional<Context> resolve(UUID actor, String worldId, int x, int y, int z,
                                      boolean needDeposit, boolean needWithdraw) {
        Objects.requireNonNull(actor, "actor");
        Optional<SettlementFacility> facility = facilities.resolve(worldId, x, y, z);
        if (facility.isEmpty() || facility.get().type() != FacilityType.STORAGE) {
            return Optional.empty();
        }
        Optional<Territory> territory = territories.get(facility.get().territoryId());
        if (territory.isEmpty() || territory.get().governedByGuildId().isEmpty()) {
            return Optional.empty();
        }
        String guildId = territory.get().governedByGuildId().orElseThrow();
        if (!access.isResident(actor, guildId)) {
            return Optional.empty();
        }
        if (needDeposit && !access.canDeposit(actor, guildId)) {
            return Optional.empty();
        }
        if (needWithdraw && !access.canWithdraw(actor, guildId)) {
            return Optional.empty();
        }
        if (!needDeposit && !needWithdraw
                && !access.canDeposit(actor, guildId) && !access.canWithdraw(actor, guildId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Context(guildId, facility.get().id(), loadOrEmpty(guildId)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private StorageResult resolveDenial(UUID actor, String worldId, int x, int y, int z) {
        return resolveDenial(actor, worldId, x, y, z, false, false);
    }

    private StorageResult resolveDenial(UUID actor, String worldId, int x, int y, int z,
                                        boolean needDeposit, boolean needWithdraw) {
        Optional<SettlementFacility> facility = facilities.resolve(worldId, x, y, z);
        if (facility.isEmpty()) {
            return StorageResult.denied(StorageStatus.DENIED_NO_FACILITY);
        }
        if (facility.get().type() != FacilityType.STORAGE) {
            return StorageResult.denied(StorageStatus.DENIED_WRONG_TYPE);
        }
        Optional<Territory> territory = territories.get(facility.get().territoryId());
        if (territory.isEmpty() || territory.get().governedByGuildId().isEmpty()) {
            return StorageResult.denied(StorageStatus.DENIED_NO_GOVERNMENT);
        }
        String guildId = territory.get().governedByGuildId().orElseThrow();
        if (!access.isResident(actor, guildId)) {
            return StorageResult.denied(StorageStatus.DENIED_NOT_RESIDENT);
        }
        if ((needDeposit && !access.canDeposit(actor, guildId))
                || (needWithdraw && !access.canWithdraw(actor, guildId))
                || (!needDeposit && !needWithdraw
                && !access.canDeposit(actor, guildId) && !access.canWithdraw(actor, guildId))) {
            return StorageResult.denied(StorageStatus.DENIED_NO_PERMISSION);
        }
        return StorageResult.denied(StorageStatus.UNAVAILABLE);
    }

    private StorageStatus acquire(UUID actor, String guildId) {
        UUID holder = sessions.putIfAbsent(guildId, actor);
        if (holder != null && !holder.equals(actor)) {
            return StorageStatus.DENIED_IN_USE;
        }
        sessions.put(guildId, actor);
        return null;
    }

    private boolean ownsSession(UUID actor, String guildId) {
        return actor.equals(sessions.get(guildId));
    }

    private StorageResult opened(Context context, UUID actor) {
        return new StorageResult(StorageStatus.OPENED,
                view(context.guildId, context.facilityId, context.document, actor), null);
    }

    private StorageResult persist(Context context, Map<Integer, StorageSlot> slots,
                                  StorageStatus status, OpaqueItemPayload item) {
        try {
            GuildStorageDocument next = new GuildStorageDocument(
                    context.guildId, context.document.capacitySlots(),
                    context.document.revision() + 1, List.copyOf(slots.values()));
            store.save(next);
            return new StorageResult(status, view(context.guildId, context.facilityId, next, null), item);
        } catch (IOException e) {
            return StorageResult.denied(StorageStatus.UNAVAILABLE);
        }
    }

    private GuildStorageDocument loadOrEmpty(String guildId) throws IOException {
        return store.load(guildId).orElseGet(() -> GuildStorageDocument.empty(guildId));
    }

    private StorageSnapshot view(String guildId, String facilityId, GuildStorageDocument document,
                                 UUID actor) {
        boolean deposit = actor != null && access.canDeposit(actor, guildId);
        boolean withdraw = actor != null && access.canWithdraw(actor, guildId);
        if (actor == null) {
            deposit = true;
            withdraw = true;
        }
        return new StorageSnapshot(guildId, facilityId, document.capacitySlots(), document.revision(),
                document.slots(), deposit, withdraw);
    }

    private static Map<Integer, StorageSlot> index(List<StorageSlot> slots) {
        Map<Integer, StorageSlot> indexed = new LinkedHashMap<>();
        for (StorageSlot slot : slots) {
            indexed.put(slot.index(), slot);
        }
        return indexed;
    }

    private static List<StorageSlot> normalize(List<StorageSlot> slots, int capacity) {
        if (slots == null) {
            return List.of();
        }
        Map<Integer, StorageSlot> unique = new LinkedHashMap<>();
        for (StorageSlot slot : slots) {
            if (slot.index() >= capacity) {
                return null;
            }
            unique.put(slot.index(), slot);
        }
        return new ArrayList<>(unique.values());
    }

    private record Context(String guildId, String facilityId, GuildStorageDocument document) {
    }

    @FunctionalInterface
    private interface Mutator {
        StorageResult apply(Context context);
    }
}
