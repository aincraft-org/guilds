package org.aincraft.guilds.territory.persist;

import java.io.IOException;
import java.util.Objects;

/** Opens the shared HikariCP database for the configured SQL backend. */
public final class DatabaseFactory {
    private DatabaseFactory() {
    }

    public static Database open(DatabaseSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        return new HikariDatabase(settings);
    }
}
