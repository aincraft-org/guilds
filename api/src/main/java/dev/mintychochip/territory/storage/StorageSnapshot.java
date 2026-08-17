package dev.mintychochip.territory.storage;

import java.util.List;
import java.util.Objects;

/**
 * Viewer-facing guild bank contents.
 *
 * @param guildId owning guild
 * @param facilityId storage facility used for this view
 * @param capacitySlots logical slot count
 * @param revision optimistic concurrency token
 * @param slots occupied slots
 * @param canDeposit whether the viewer may add items
 * @param canWithdraw whether the viewer may take items
 */
public record StorageSnapshot(
        String guildId,
        String facilityId,
        int capacitySlots,
        int revision,
        List<StorageSlot> slots,
        boolean canDeposit,
        boolean canWithdraw
) {
    /** Default virtual chest size. */
    public static final int DEFAULT_CAPACITY = 54;

    /**
     * Validates and copies the snapshot.
     *
     * @throws IllegalArgumentException if identifiers are blank or capacity is not positive
     */
    public StorageSnapshot {
        guildId = requireText(guildId, "guildId");
        facilityId = requireText(facilityId, "facilityId");
        if (capacitySlots <= 0) {
            throw new IllegalArgumentException("capacitySlots must be positive");
        }
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
