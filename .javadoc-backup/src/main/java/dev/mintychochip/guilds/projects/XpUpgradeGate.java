package dev.mintychochip.guilds.projects;

import dev.mintychochip.guilds.models.ResourceType;

import java.util.Locale;
import java.util.Map;

/**
 * Guild level-up is gated by experience progress only.
 * Material leftovers in upgrade_progress or leftover material costs are ignored.
 */
public final class XpUpgradeGate {

    /** The experience key constant. */
    public static final String EXPERIENCE_KEY = ResourceType.EXPERIENCE.getNormalizedName();

    /** Creates a new xp upgrade gate instance. */
    private XpUpgradeGate() {
    }

    /**
     * Performs the required experience operation.
     * @param resourceCosts the resource costs
     * @return the result
     */
    public static int requiredExperience(Map<String, Integer> resourceCosts) {
        if (resourceCosts == null || resourceCosts.isEmpty()) {
            return 0;
        }
        int required = 0;
        for (Map.Entry<String, Integer> entry : resourceCosts.entrySet()) {
            if (isExperienceKey(entry.getKey()) && entry.getValue() != null) {
                required += entry.getValue();
            }
        }
        return required;
    }

    /**
     * Performs the contributed experience operation.
     * @param progress the progress
     * @return the result
     */
    public static int contributedExperience(Map<String, Integer> progress) {
        if (progress == null || progress.isEmpty()) {
            return 0;
        }
        int contributed = 0;
        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            if (isExperienceKey(entry.getKey()) && entry.getValue() != null) {
                contributed += entry.getValue();
            }
        }
        return contributed;
    }

    /**
     * Returns whether enough experience.
     * @param progress the progress
     * @param requiredExperience the required experience
     * @return the result
     */
    public static boolean hasEnoughExperience(Map<String, Integer> progress, int requiredExperience) {
        return contributedExperience(progress) >= requiredExperience;
    }

    /**
     * Returns whether experience key.
     * @param key the key
     * @return the result
     */
    public static boolean isExperienceKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return ResourceType.fromString(key)
                .map(type -> type == ResourceType.EXPERIENCE)
                .orElseGet(() -> EXPERIENCE_KEY.equals(key.trim().toLowerCase(Locale.ROOT)));
    }
}
