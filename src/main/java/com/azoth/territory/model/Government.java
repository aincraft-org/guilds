package com.azoth.territory.model;

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
 */
public final class Government {
    public static final int DEFAULT_OLIGARCHY_SEATS = 3;
    public static final int DEFAULT_DEMOCRACY_SEATS = 5;
    public static final int MIN_OLIGARCHY_SEATS = 2;
    public static final int MIN_DEMOCRACY_SEATS = 1;

    private final GovernmentForm form;
    private final List<GovernmentSeat> seats;

    private Government(GovernmentForm form, List<GovernmentSeat> seats) {
        this.form = Objects.requireNonNull(form, "form");
        this.seats = List.copyOf(seats);
        validateStructure();
    }

    public static Government anarchy() {
        return new Government(GovernmentForm.ANARCHY, List.of());
    }

    public static Government monarchy(String sovereignHolderId) {
        return singleSeat(GovernmentForm.MONARCHY, "sovereign", SeatRole.SOVEREIGN, sovereignHolderId);
    }

    private static Government singleSeat(
            GovernmentForm form, String seatId, SeatRole role, String holderId
    ) {
        return new Government(form, List.of(new GovernmentSeat(seatId, role, holderId)));
    }

    public static Government oligarchy(Collection<String> councilorHolderIds) {
        List<String> holders = normalizeHolders(councilorHolderIds);
        int count = Math.max(MIN_OLIGARCHY_SEATS, Math.max(DEFAULT_OLIGARCHY_SEATS, holders.size()));
        return oligarchy(count, holders);
    }

    public static Government oligarchy(int seatCount, Collection<String> councilorHolderIds) {
        return multiSeat(
                GovernmentForm.OLIGARCHY, SeatRole.COUNCILOR, "councilor",
                seatCount, MIN_OLIGARCHY_SEATS, councilorHolderIds
        );
    }

    public static Government democracy(Collection<String> representativeHolderIds) {
        List<String> holders = normalizeHolders(representativeHolderIds);
        int count = Math.max(MIN_DEMOCRACY_SEATS, Math.max(DEFAULT_DEMOCRACY_SEATS, holders.size()));
        return democracy(count, holders, null);
    }

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
     * Reconstruct from persisted form + seats (validates structure).
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

    public GovernmentForm form() {
        return form;
    }

    public boolean isAssigned() {
        return form.isAssigned();
    }

    public List<GovernmentSeat> seats() {
        return seats;
    }

    public int seatCount() {
        return seats.size();
    }

    public Optional<GovernmentSeat> seat(String seatId) {
        return seats.stream().filter(s -> s.seatId().equals(seatId)).findFirst();
    }

    public List<GovernmentSeat> seatsByRole(SeatRole role) {
        Objects.requireNonNull(role, "role");
        return seats.stream().filter(s -> s.role() == role).toList();
    }

    public List<String> holderIds() {
        return seats.stream()
                .map(GovernmentSeat::holderId)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    public Optional<String> sovereignHolderId() {
        if (form != GovernmentForm.MONARCHY) {
            return Optional.empty();
        }
        return seats.isEmpty() ? Optional.empty() : seats.get(0).holderId();
    }

    public Optional<String> primaryAuthorityHolderId() {
        if (!isAssigned() || form.decisionStyle() != GovernmentForm.DecisionStyle.DECREE) {
            return Optional.empty();
        }
        return seats.isEmpty() ? Optional.empty() : seats.get(0).holderId();
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Government that)) {
            return false;
        }
        return form == that.form && seats.equals(that.seats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(form, seats);
    }

    @Override
    public String toString() {
        return "Government{form=" + form + ", seats=" + seats.size() + '}';
    }
}
