package com.azoth.territory.standing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@code bonuses.json} from a path. Returns empty when the file is
 * absent or invalid — caller decides whether to fall back to defaults or
 * disable the subsystem (spec §5).
 */
public final class StandingConfigLoader {

    private static final Logger LOG = Logger.getLogger(StandingConfigLoader.class.getName());

    private StandingConfigLoader() {
    }

    public static Optional<StandingConfig> load(Path file) {
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.get("version") == null || root.get("version").getAsInt() != 1) {
                LOG.warning("bonuses.json: unsupported version (expected 1)");
                return Optional.empty();
            }
            double cap = root.get("cap").getAsDouble();
            JsonObject sources = root.getAsJsonObject("sources");
            double pvpKill = sources.get("pvp-kill").getAsDouble();
            double pveKill = sources.get("pve-kill").getAsDouble();
            double blockBreak = sources.get("block-break").getAsDouble();
            List<StandingTier> tiers = new ArrayList<>();
            JsonArray rawTiers = root.getAsJsonArray("tiers");
            for (JsonElement raw : rawTiers) {
                JsonObject tier = raw.getAsJsonObject();
                tiers.add(new StandingTier(
                        tier.get("level").getAsInt(),
                        tier.get("threshold").getAsDouble(),
                        tier.get("harvest_multiplier").getAsDouble(),
                        tier.get("influence_multiplier").getAsDouble()
                ));
            }
            return Optional.of(new StandingConfig(cap, pvpKill, pveKill, blockBreak, List.copyOf(tiers)));
        } catch (IOException | IllegalStateException | JsonSyntaxException | NullPointerException |
                 IllegalArgumentException e) {
            LOG.log(Level.WARNING, "bonuses.json: invalid configuration — " + e.getMessage(), e);
            return Optional.empty();
        }
    }
}
