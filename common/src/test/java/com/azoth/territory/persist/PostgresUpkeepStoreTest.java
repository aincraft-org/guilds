package com.azoth.territory.persist;

import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.economy.ExpenseOutcome;
import com.azoth.territory.upkeep.UpkeepState;
import com.azoth.territory.upkeep.UpkeepStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresUpkeepStoreTest {
    private PostgresDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void roundTripPreservesDurableUpkeepState() throws Exception {
        UpkeepState state = new UpkeepState(
                "t1", 42.5, UpkeepStatus.GRACE,
                2_000L, 2_500L, "upkeep:t1:1000", ExpenseOutcome.INSUFFICIENT_FUNDS);
        PostgresUpkeepStore store = new PostgresUpkeepStore(database);

        store.save(List.of(state));

        assertEquals(List.of(state), new PostgresUpkeepStore(database).load());
    }
}
