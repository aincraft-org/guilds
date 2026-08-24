package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.models.Resident;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-SQLite tests for offline resident prefix search (tab-completion
 * suggestions).
 */
class ResidentServiceImplSearchTest {

    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private ResidentService residents;

    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        residents = services.residentService();
        residents.createResident(UUID.randomUUID(), "alice");
        residents.createResident(UUID.randomUUID(), "alex");
        residents.createResident(UUID.randomUUID(), "bob");
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    private List<String> names(String prefix, int limit) {
        return residents.searchResidents(prefix, limit).stream().map(Resident::getName).toList();
    }

    @Test
    void searchMatchesCaseInsensitivePrefixOrderedByName() {
        assertEquals(List.of("alex", "alice"), names("al", 10));
        assertEquals(List.of("alex", "alice"), names("AL", 10));
    }

    @Test
    void searchRespectsLimit() {
        assertEquals(List.of("alex"), names("al", 1));
    }

    @Test
    void searchEmptyPrefixReturnsAll() {
        assertEquals(List.of("alex", "alice", "bob"), names("", 10));
    }

    @Test
    void searchNoMatchReturnsEmpty() {
        assertTrue(names("zz", 10).isEmpty());
    }
}
