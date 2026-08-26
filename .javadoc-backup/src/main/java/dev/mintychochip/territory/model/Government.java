package dev.mintychochip.territory.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Active government attachment: form + seat schema + holders.
 * <p>
 * Seat structure is form-specific (only mechanically distinct forms):
 * <ul>
 *   <li>{@link GovernmentForm#MONARCHY} — 1 {@link SeatRole#SOVEREIGN}</li>
 *   <li>{@link GovernmentForm#OLIGARCHY} — 2+ {@link SeatRole#COUNCILOR}</li>
 *   <li>{@link GovernmentForm#DEMOCRACY} — 1+ {@link SeatRole#REPRESENTATIVE}</li>
 *   <li>{@link GovernmentForm#ANARCHY} — no seats</li>
 * </ul>
 *
 * @param form government form
 * @param seats ordered government seats
 */
public record Government(GovernmentForm form, List<GovernmentSeat> seats) {
    /** Default oligarchy seat count. */
    public static final int DEFAULT_OLIGARCHY_SEATS = 3;
    /** Default democracy seat count. */
    public static final int DEFAULT_DEMOCRACY_SEATS = 5;
    /** Minimum oligarchy seat count. */
    public static final int MIN_OLIGARCHY_SEATS = 2;
    /** Minimum democracy seat count. */
    public static final int MIN_DEMOCRACY_SEATS = 1;

    /** Constructs and validates a government.
     * @param form government form
     * @param seats ordered seats
     * @throws NullPointerException if {@code form} or {@code seats} is {@code null}
     * @throws IllegalArgumentException if the seat structure is invalid
     */
    public Government(GovernmentForm form, List<GovernmentSeat> seats) {
        this.form = Objects.requireNonNull(form, "form");
        this.seats = List.copyOf(seats);
        validateStructure();
    }

    /** Creates an unassigned government.
     * @return anarchy government
     */
    public static Government anarchy() {
        return new Government(GovernmentForm.ANARCHY, List.of());
    }

    /** Creates a monarchy with one sovereign seat.
     * @param sovereignHolderId sovereign holder identifier
     * @return a monarchy
     */
    public static Government monarchy(String sovereignHolderId) {
        return singleSeat(GovernmentForm.MONARCHY, "sovereign", SeatRole.SOVEREIGN, sovereignHolderId);
    }

    private static Government singleSeat(
            GovernmentForm form, String seatId, SeatRole role, String holderId
    ) {
        return new Government(form, List.of(new GovernmentSeat(seatId, role, holderId)));
    }

    /** Creates an oligarchy using default seat sizing.
     * @param councilorHolderIds councilor holder identifiers
     * @return an oligarchy
     */
    public static Government oligarchy(Collection<String> councilorHolderIds) {
        List<String> holders = normalizeHolders(councilorHolderIds);
        int count = Math.max(MIN_OLIGARCHY_SEATS, Math.max(DEFAULT_OLIGARCHY_SEATS, holders.size()));
        return oligarchy(count, holders);
    }

    /** Creates an oligarchy with a requested seat count.
     * @param seatCount number of seats
     * @param councilorHolderIds councilor holder identifiers
     * @return an oligarchy
     * @throws IllegalArgumentException if {@code seatCount} is too small
     */
    public static Government oligarchy(int seatCount, Collection<String> councilorHolderIds) {
        return multiSeat(
                GovernmentForm.OLIGARCHY, SeatRole.COUNCILOR, "councilor",
                seatCount, MIN_OLIGARCHY_SEATS, councilorHolderIds
        );
    }

    /** Creates a democracy using default seat sizing.
     * @param representativeHolderIds representative holder identifiers
     * @return a democracy
     */
    public static Government democracy(Collection<String> representativeHolderIds) {
        List<String> holders = normalizeHolders(representativeHolderIds);
        int count = Math.max(MIN_DEMOCRACY_SEATS, Math.max(DEFAULT_DEMOCRACY_SEATS, holders.size()));
        return democracy(count, holders, null);
    }

    /** Creates a democracy with seats and optional terms.
     * @param seatCount number of seats
     * @param representativeHolderIds representative holder identifiers
     * @param termEndsAtEpochMs optional term end times
     * @return a democracy
     * @throws IllegalArgumentException if {@code seatCount} is too small
     */
    public static Government democracy(
            int seatCount,
            Collection<String> representativeHolderIds,
            Collection<Long> termEndsAtEpochMs
    ) {
        if (seatCount < MIN_DEMOCRACY_SEATS) {
            throw new IllegalArgumentException(
                    "democracy requires at least " + MIN_DEMOCRACY_SEATS + " seat(s), got " + seatCount
            );
        }
        List<String> holders = normalizeHolders(representativeHolderIds);
        List<Long> terms = termEndsAtEpochMs == null
                ? List.of()
                : new ArrayList<>(termEndsAtEpochMs);
        List<GovernmentSeat> seats = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            String holder = i < holders.size() ? holders.get(i) : null;
            Long term = i < terms.size() ? terms.get(i) : null;
            seats.add(new GovernmentSeat(
                    "representative-" + (i + 1),
                    SeatRole.REPRESENTATIVE,
                    holder,
                    term
            ));
        }
        return new Government(GovernmentForm.DEMOCRACY, seats);
    }

    private static Government multiSeat(
            GovernmentForm form,
            SeatRole role,
            String idPrefix,
            int seatCount,
            int minSeats,
            Collection<String> holderIds
    ) {
        if (seatCount < minSeats) {
            throw new IllegalArgumentException(
                    form.name().toLowerCase() + " requires at least " + minSeats + " seats, got " + seatCount
            );
        }
        List<String> holders = normalizeHolders(holderIds);
        List<GovernmentSeat> seats = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            String holder = i < holders.size() ? holders.get(i) : null;
            seats.add(new GovernmentSeat(idPrefix + "-" + (i + 1), role, holder));
        }
        return new Government(form, seats);
    }

    /**
     * Derive a government from a chosen form and the role-ordered authority ids.
     * <p>
     * Role mapping (the governance form IS the permission structure):
     * <ul>
     *   <li>{@code MONARCHY} — first id becomes the SOVEREIGN seat</li>
     *   <li>{@code OLIGARCHY} — all ids become COUNCILOR seats (min 2)</li>
     *   <li>{@code DEMOCRACY} — all ids become REPRESENTATIVE seats (min 1)</li>
     *   <li>{@code ANARCHY} — no seats</li>
     * </ul>
     * Used to derive guild (guild) and alliance (nation) governments from their
     * role holders (mayor/assistants/residents, king/ministers/guild mayors).
     *
     * @param form government form
     * @param authorityIds role-ordered authority identifiers
     * @return the derived government
     */
    public static Government fromRoles(GovernmentForm form, Collection<String> authorityIds) {
        Objects.requireNonNull(form, "form");
        List<String> ids = normalizeHolders(authorityIds);
        return switch (form) {
            case ANARCHY -> anarchy();
            case MONARCHY -> monarchy(ids.isEmpty() ? null : ids.get(0));
            case OLIGARCHY -> oligarchy(Math.max(MIN_OLIGARCHY_SEATS, ids.size()), ids);
            case DEMOCRACY -> democracy(Math.max(MIN_DEMOCRACY_SEATS, ids.size()), ids, null);
        };
    }

    /**
     * Reconstruct from persisted form and seats, validating the structure.
     *
     * @param form persisted government form
     * @param seats persisted government seats
     * @return the reconstructed government
     */
    public static Government of(GovernmentForm form, Collection<GovernmentSeat> seats) {
        if (form == null || form == GovernmentForm.ANARCHY) {
            if (seats != null && !seats.isEmpty()) {
                throw new IllegalArgumentException("ANARCHY government cannot have seats");
            }
            return anarchy();
        }
        return new Government(form, seats == null ? List.of() : List.copyOf(seats));
    }

    private void validateStructure() {
        switch (form) {
            case ANARCHY -> {
                if (!seats.isEmpty()) {
                    throw new IllegalArgumentException("ANARCHY government cannot have seats");
                }
            }
            case MONARCHY -> requireSingleRole(SeatRole.SOVEREIGN, "monarchy");
            case OLIGARCHY -> requireMultiRole(SeatRole.COUNCILOR, MIN_OLIGARCHY_SEATS, "oligarchy");
            case DEMOCRACY -> requireMultiRole(SeatRole.REPRESENTATIVE, MIN_DEMOCRACY_SEATS, "democracy");
        }
        long distinct = seats.stream().map(GovernmentSeat::seatId).distinct().count();
        if (distinct != seats.size()) {
            throw new IllegalArgumentException("duplicate seat ids in government");
        }
    }

    private void requireSingleRole(SeatRole role, String label) {
        if (seats.size() != 1) {
            throw new IllegalArgumentException(label + " requires exactly 1 seat, got " + seats.size());
        }
        if (seats.get(0).role() != role) {
            throw new IllegalArgumentException(label + " seat must be " + role);
        }
    }

    private void requireMultiRole(SeatRole role, int min, String label) {
        if (seats.size() < min) {
            throw new IllegalArgumentException(label + " requires at least " + min + " seats");
        }
        for (GovernmentSeat s : seats) {
            if (s.role() != role) {
                throw new IllegalArgumentException(
                        label + " seats must be " + role + ", got " + s.role() + " on " + s.seatId()
                );
            }
        }
    }

    private static List<String> normalizeHolders(Collection<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String h : raw) {
            if (h != null && !h.isBlank()) {
                out.add(h.trim());
            }
        }
        return out;
    }


    /** Reports whether this government has assigned authority.
     * @return whether this government has assigned authority
     */
    public boolean isAssigned() {
        return form.isAssigned();
    }


    /** Reports the number of seats in this government.
     * @return the number of seats
     */
    public int seatCount() {
        return seats.size();
    }

    /** Finds a seat by identifier.
     * @param seatId seat identifier
     * @return the matching seat, if present
     */
    public Optional<GovernmentSeat> seat(String seatId) {
        return seats.stream().filter(s -> s.seatId().equals(seatId)).findFirst();
    }

    /** Returns seats having a role.
     * @param role seat role
     * @return matching seats
     * @throws NullPointerException if {@code role} is {@code null}
     */
    public List<GovernmentSeat> seatsByRole(SeatRole role) {
        Objects.requireNonNull(role, "role");
        return seats.stream().filter(s -> s.role() == role).toList();
    }

    /** Reports holder identifiers for occupied seats.
     * @return holder identifiers for occupied seats
     */
    public List<String> holderIds() {
        return seats.stream()
                .map(GovernmentSeat::holderId)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    /** Reports the monarchy's sovereign holder, if assigned.
     * @return the monarchy's sovereign holder, if assigned
     */
    public Optional<String> sovereignHolderId() {
        if (form != GovernmentForm.MONARCHY) {
            return Optional.empty();
        }
        return seats.isEmpty() ? Optional.empty() : seats.get(0).holderId();
    }

    /** Reports the primary decree authority holder, if assigned.
     * @return the primary decree authority holder, if assigned
     */
    public Optional<String> primaryAuthorityHolderId() {
        if (!isAssigned() || form.decisionStyle() != GovernmentForm.DecisionStyle.DECREE) {
            return Optional.empty();
        }
        return seats.isEmpty() ? Optional.empty() : seats.get(0).holderId();
    }

    /** Replaces a seat holder.
     * @param seatId seat identifier
     * @param holderId new holder identifier
     * @return a government with the updated seat
     * @throws IllegalArgumentException if the seat is unknown
     */
    public Government withSeatHolder(String seatId, String holderId) {
        List<GovernmentSeat> next = new ArrayList<>(seats.size());
        boolean found = false;
        for (GovernmentSeat s : seats) {
            if (s.seatId().equals(seatId)) {
                next.add(s.withHolder(holderId));
                found = true;
            } else {
                next.add(s);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("unknown seat: " + seatId);
        }
        return new Government(form, next);
    }
    /** Returns a concise textual representation.
     * @return a description of this government
     */
    @Override
    public String toString() {
        return "Government{form=" + form + ", seats=" + seats.size() + '}';

    }
}
