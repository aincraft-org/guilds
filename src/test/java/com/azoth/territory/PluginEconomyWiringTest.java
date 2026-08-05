package com.azoth.territory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packaged plugin.yml soft-depends on Vault. */
class PluginEconomyWiringTest {
    @Test
    void pluginMetadataDeclaresSoftDependVault() throws Exception {
        var stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        String yml;
        try (stream) {
            yml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yml.contains("softdepend") && yml.contains("Vault"),
                "plugin.yml must soft-depend on Vault");
    }
}
