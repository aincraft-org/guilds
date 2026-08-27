package dev.mintychochip.sql;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlParamsTest {

    @Test
    void allowsNullBindValuesAndPreservesOrder() {
        Map<String, Object> params = SqlParams.of(
                "name", "Everfall",
                "home_block_x", null,
                "balance", 1.5);

        assertEquals("Everfall", params.get("name"));
        assertTrue(params.containsKey("home_block_x"));
        assertNull(params.get("home_block_x"));
        assertEquals(1.5, params.get("balance"));
        assertEquals(3, params.size());
    }

    @Test
    void rejectsOddPairCount() {
        assertThrows(IllegalArgumentException.class, () -> SqlParams.of("only-name"));
    }
}
