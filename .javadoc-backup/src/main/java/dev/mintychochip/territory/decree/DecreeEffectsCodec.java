package dev.mintychochip.territory.decree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON codec for {@link DecreeEffects} — the structured payload attached to policies.
 * <p>
 * Shape (version 1):
 * <pre>
 * {
 *   "version": 1,
 *   "taxes": [
 *     { "goodIds": ["carrot", "potato", ...], "taxDeltaPercentPoints": 15 }
 *   ]
 * }
 * </pre>
 */
public final class DecreeEffectsCodec {
    /** Prevents instantiation. */
    private DecreeEffectsCodec() {
    }

    /**
     * Serializes decree effects to a JSON object.
     *
     * @param effects effects to serialize
     * @return serialized effects
     */
    public static JsonObject toJson(DecreeEffects effects) {
        if (effects == null || effects.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("version", DecreeEffects.SCHEMA_VERSION);
            empty.add("taxes", new JsonArray());
            return empty;
        }
        JsonObject o = new JsonObject();
        o.addProperty("version", effects.version());
        JsonArray taxes = new JsonArray();
        for (TaxEffect t : effects.taxes()) {
            JsonObject te = new JsonObject();
            JsonArray ids = new JsonArray();
            for (String id : t.goodIds()) {
                ids.add(id);
            }
            te.add("goodIds", ids);
            te.addProperty("taxDeltaPercentPoints", t.taxDeltaPercentPoints());
            taxes.add(te);
        }
        o.add("taxes", taxes);
        return o;
    }

    /**
     * Serializes decree effects to a JSON string.
     *
     * @param effects effects to serialize
     * @return serialized JSON
     */
    public static String toJsonString(DecreeEffects effects) {
        return toJson(effects).toString();
    }

    /**
     * Deserializes decree effects from JSON.
     *
     * @param o JSON object
     * @return decoded effects
     */
    public static DecreeEffects fromJson(JsonObject o) {
        if (o == null) {
            return DecreeEffects.empty();
        }
        int version = o.has("version") ? o.get("version").getAsInt() : DecreeEffects.SCHEMA_VERSION;
        List<TaxEffect> taxes = new ArrayList<>();
        if (o.has("taxes") && o.get("taxes").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("taxes")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject te = el.getAsJsonObject();
                List<String> goodIds = new ArrayList<>();
                if (te.has("goodIds") && te.get("goodIds").isJsonArray()) {
                    for (JsonElement idEl : te.getAsJsonArray("goodIds")) {
                        goodIds.add(idEl.getAsString());
                    }
                }
                if (goodIds.isEmpty()) {
                    continue;
                }
                double delta = te.has("taxDeltaPercentPoints")
                        ? te.get("taxDeltaPercentPoints").getAsDouble()
                        : 0.0;
                taxes.add(new TaxEffect(goodIds, delta));
            }
        }
        return new DecreeEffects(version, taxes);
    }

    /**
     * Deserializes decree effects from a JSON string.
     *
     * @param json JSON string
     * @return decoded effects
     */
    public static DecreeEffects fromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return DecreeEffects.empty();
        }
        JsonElement el = JsonParser.parseString(json);
        if (!el.isJsonObject()) {
            throw new IllegalArgumentException("decree effects JSON must be an object");
        }
        return fromJson(el.getAsJsonObject());
    }
}
