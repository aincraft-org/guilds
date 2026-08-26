package dev.mintychochip.sql;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered SQL bind map that permits {@code null} values (unlike {@link Map#of()}).
 */
public final class SqlParams {

    private SqlParams() {
    }

    public static Map<String, Object> of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("SqlParams.of requires name/value pairs");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object name = pairs[i];
            if (!(name instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("SQL parameter name must be a non-blank string");
            }
            params.put(key, pairs[i + 1]);
        }
        return params;
    }
}
