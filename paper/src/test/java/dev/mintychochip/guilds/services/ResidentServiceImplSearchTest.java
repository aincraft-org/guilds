package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.GuildsServiceTestFixture;
import dev.mintychochip.guilds.models.Resident;
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

    /** The temp dir. */
    @TempDir
    Path tempDir;

    /** The services. */
    private GuildsServiceTestFixture.Services services;
    /** The residents. */
    private ResidentService residents;

    /** Sets the up. */
    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        residents = services.residentService();
        residents.createResident(UUID.randomUUID(), "alice");
        residents.createResident(UUID.randomUUID(), "alex");
        residents.createResident(UUID.randomUUID(), "bob");
    }

    /** Performs the tear down operation. */
    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    /**
     * Performs the names operation.
     * @param prefix the prefix
     * @param limit the limit
     * @return the result
     */
    private List<String> names(String prefix, int limit) {
        return residents.searchResidents(prefix, limit).stream().map(Resident::getName).toList();
    }

    /** Finds the matches case insensitive prefix ordered by name. */
    @Test
    void searchMatchesCaseInsensitivePrefixOrderedByName() {
        assertEquals(List.of("alex", "alice"), names("al", 10));
        assertEquals(List.of("alex", "alice"), names("AL", 10));
    }

    /** Finds the respects limit. */
    @Test
    void searchRespectsLimit() {
        assertEquals(List.of("alex"), names("al", 1));
    }

    /** Finds the empty prefix returns all. */
    @Test
    void searchEmptyPrefixReturnsAll() {
        assertEquals(List.of("alex", "alice", "bob"), names("", 10));
    }

    /** Finds the no match returns empty. */
    @Test
    void searchNoMatchReturnsEmpty() {
        assertTrue(names("zz", 10).isEmpty());
    }
}
