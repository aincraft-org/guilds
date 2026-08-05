package org.aincraft.towny;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

/**
 * Basic working test to demonstrate the testing framework
 * This shows the fundamental structure and how to run tests
 */
class BasicWorkingTest {

    @Test
    @DisplayName("Should demonstrate basic test functionality")
    void shouldDemonstrateBasicTestFunctionality() {
        // Given
        int a = 2;
        int b = 3;

        // When
        int result = a + b;

        // Then
        assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("Should verify string operations work")
    void shouldVerifyStringOperationsWork() {
        // Given
        String input = "TownyPlugin";

        // When
        String result = input.toUpperCase();

        // Then
        assertThat(result).isEqualTo("TOWNYPLUGIN");
        assertThat(result).startsWith("TOWNY");
        assertThat(result).endsWith("PLUGIN");
    }

    @Test
    @DisplayName("Should test list operations")
    void shouldTestListOperations() {
        // Given
        java.util.List<String> items = java.util.List.of("town", "nation", "resident");

        // When & Then
        assertThat(items).hasSize(3);
        assertThat(items).contains("town");
        assertThat(items).doesNotContain("admin");
    }
}