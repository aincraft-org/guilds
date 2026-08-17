package com.azoth.territory.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Region-level guild entity formed with an assigned government.
 *
 * @param id guild identifier
 * @param name display name
 * @param government assigned government
 * @param memberIds opaque member identifiers
 */
public record RegionGuild(String id, String name, Government government, List<String> memberIds) {
    /**
     * Validates and normalizes a region guild.
     *
     * @throws IllegalArgumentException if an identifier or government is invalid
     */
    public RegionGuild {
        id = requireId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        government = requireAssignedGovernment(government);
        memberIds = List.copyOf(normalizeIds(memberIds));
    }

    /**
     * Form a region guild with no members.
     *
     * @param id guild identifier
     * @param name display name
     * @param government assigned government
     * @return formed guild
     */
    public static RegionGuild form(String id, String name, Government government) {
        return form(id, name, government, List.of());
    }
    /**
     * Form a region guild with initial members.
     *
     * @param id guild identifier
     * @param name display name
     * @param government assigned government
     * @param memberIds initial member identifiers
     * @return formed guild
     */
    public static RegionGuild form(
            String id,
            String name,
            Government government,
            Collection<String> memberIds
    ) {
        return new RegionGuild(id, name, government, memberIds == null ? List.of() : new ArrayList<>(memberIds));
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

    /**
     * Returns the assigned government form.
     *
     * @return government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }

    /**
     * Tests whether a member is present.
     *
     * @param memberId member identifier
     * @return {@code true} when the member is present
     */
    public boolean containsMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }
        return memberIds.contains(memberId.trim());
    }

    /**
     * Returns a copy using another government.
     *
     * @param next assigned replacement government
     * @return updated guild
     */
    public RegionGuild withGovernment(Government next) {
        return new RegionGuild(id, name, next, memberIds);
    }

    /**
     * Returns a copy containing the member.
     *
     * @param memberId member identifier
     * @return updated guild, or this instance when unchanged
     */
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

    /**
     * Returns a copy without the member.
     *
     * @param memberId member identifier
     * @return updated guild, or this instance when unchanged
     */
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
    public String toString() {
        return "RegionGuild{id='" + id + "', government=" + government.form()
                + ", members=" + memberIds.size() + '}';
    }
}
