package com.azoth.territory.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Region-level guild entity: formed with an assigned {@link Government}.
 * <p>
 * Governments live at the region-guild layer (not on raw territories alone).
 * Member ids are opaque holder strings (player UUID, company id, …).
 */
public final class RegionGuild {
    private final String id;
    private final String name;
    private final Government government;
    private final List<String> memberIds;

    private RegionGuild(String id, String name, Government government, Collection<String> memberIds) {
        this.id = requireId(id);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
        this.government = requireAssignedGovernment(government);
        this.memberIds = List.copyOf(normalizeIds(memberIds));
    }

    /**
     * Form a region guild with a chosen government and no members yet.
     */
    public static RegionGuild form(String id, String name, Government government) {
        return form(id, name, government, List.of());
    }

    /**
     * Form a region guild with a chosen government and initial members.
     */
    public static RegionGuild form(
            String id,
            String name,
            Government government,
            Collection<String> memberIds
    ) {
        return new RegionGuild(id, name, government, memberIds);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return id.trim();
    }

    private static Government requireAssignedGovernment(Government government) {
        if (government == null || !government.isAssigned()) {
            throw new IllegalArgumentException(
                    "region guild must pick an assigned government at formation (not ANARCHY)"
            );
        }
        return government;
    }

    private static List<String> normalizeIds(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String h : raw) {
            if (h != null && !h.isBlank()) {
                seen.add(h.trim());
            }
        }
        return new ArrayList<>(seen);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Government government() {
        return government;
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public List<String> memberIds() {
        return memberIds;
    }

    public boolean containsMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }
        return memberIds.contains(memberId.trim());
    }

    public RegionGuild withGovernment(Government next) {
        return new RegionGuild(id, name, next, memberIds);
    }

    public RegionGuild withMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return this;
        }
        String trimmed = memberId.trim();
        if (memberIds.contains(trimmed)) {
            return this;
        }
        List<String> next = new ArrayList<>(memberIds);
        next.add(trimmed);
        return new RegionGuild(id, name, government, next);
    }

    public RegionGuild withoutMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return this;
        }
        String trimmed = memberId.trim();
        if (!memberIds.contains(trimmed)) {
            return this;
        }
        List<String> next = new ArrayList<>(memberIds);
        next.remove(trimmed);
        return new RegionGuild(id, name, government, next);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegionGuild that)) {
            return false;
        }
        return id.equals(that.id)
                && name.equals(that.name)
                && government.equals(that.government)
                && memberIds.equals(that.memberIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, government, memberIds);
    }

    @Override
    public String toString() {
        return "RegionGuild{id='" + id + "', government=" + government.form()
                + ", members=" + memberIds.size() + '}';
    }
}
