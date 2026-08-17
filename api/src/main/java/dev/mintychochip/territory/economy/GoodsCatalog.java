package dev.mintychochip.territory.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Minimal goods catalog: resolves categories (e.g. "vegetables") and names to stable good ids.
 * <p>
 * Pure domain — no Bukkit, no network.
 */
public final class GoodsCatalog {
    private final Map<String, Good> byId;

    /**
     * Creates a catalog from goods.
     *
     * @param goods goods to catalog
     */
    public GoodsCatalog(Collection<Good> goods) {
        Map<String, Good> map = new LinkedHashMap<>();
        if (goods != null) {
            for (Good g : goods) {
                Objects.requireNonNull(g, "good");
                if (map.put(g.id(), g) != null) {
                    throw new IllegalArgumentException("duplicate good id: " + g.id());
                }
            }
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    /**
     * Default in-process catalog used by tests and the economy bridge.
     * Vegetables: carrot, potato, beetroot, cabbage, onion. Plus a few non-vegetable goods.
     *
     * @return default catalog
     */
    public static GoodsCatalog defaultCatalog() {
        return new GoodsCatalog(List.of(
                new Good("carrot", "Carrot", "vegetables"),
                new Good("potato", "Potato", "vegetables"),
                new Good("beetroot", "Beetroot", "vegetables"),
                new Good("cabbage", "Cabbage", "vegetables"),
                new Good("onion", "Onion", "vegetables"),
                new Good("wheat", "Wheat", "grains"),
                new Good("iron_ore", "Iron Ore", "ores"),
                new Good("gold_ore", "Gold Ore", "ores")
        ));
    }

    /**
     * Finds a good by identifier.
     *
     * @param id good identifier
     * @return matching good, when present
     */
    public Optional<Good> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(Good.normalizeId(id)));
    }

    /**
     * Returns all catalogued goods.
     *
     * @return catalogued goods
     */
    public List<Good> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Finds ids for a category.
     *
     * @param category category name
     * @return matching good ids
     */
    public List<String> idsInCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        String cat = Good.normalizeId(category);
        // accept singular "vegetable" as "vegetables"
        if (cat.equals("vegetable")) {
            cat = "vegetables";
        }
        String want = cat;
        List<String> ids = new ArrayList<>();
        for (Good g : byId.values()) {
            if (g.category().equals(want)) {
                ids.add(g.id());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    /**
     * Resolve a free-text label to good ids: category name first, then exact good id/display name.
     *
     * @param label free-text category, id, or display name
     * @return resolved good ids
     */
    public List<String> resolveLabel(String label) {
        if (label == null || label.isBlank()) {
            return List.of();
        }
        String norm = label.trim().toLowerCase(Locale.ROOT);
        List<String> byCategory = idsInCategory(norm);
        if (!byCategory.isEmpty()) {
            return byCategory;
        }
        // singular/plural soft match for known categories present in catalog
        if (norm.endsWith("s")) {
            byCategory = idsInCategory(norm.substring(0, norm.length() - 1));
            if (!byCategory.isEmpty()) {
                return byCategory;
            }
        } else {
            byCategory = idsInCategory(norm + "s");
            if (!byCategory.isEmpty()) {
                return byCategory;
            }
        }
        String id = Good.normalizeId(label);
        if (byId.containsKey(id)) {
            return List.of(id);
        }
        for (Good g : byId.values()) {
            if (g.displayName().equalsIgnoreCase(label.trim())) {
                return List.of(g.id());
            }
        }
        return List.of();
    }

    /**
     * Returns the number of goods.
     *
     * @return catalog size
     */
    public int size() {
        return byId.size();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GoodsCatalog{size=" + byId.size()
                + ", ids=" + byId.keySet().stream().collect(Collectors.joining(",")) + '}';
    }
}
