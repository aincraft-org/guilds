package org.aincraft.guilds.territory.persist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads {@code sql/migrations/{track}/manifest} and resolves versioned SQL files. */
public final class SqlMigrationCatalog {
    private static final Pattern ENTRY = Pattern.compile("^(\\d+)\\s+(\\S+)\\s+(.+?)$");

    private SqlMigrationCatalog() {
    }

    public static List<SqlMigration> load(String track, DatabaseType type) {
        List<SqlMigration> migrations = new ArrayList<>();
        String manifest = SqlStatements.load("migrations/" + track + "/manifest");
        for (String raw : manifest.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid migration manifest line in " + track + ": " + line);
            }
            int version = Integer.parseInt(matcher.group(1));
            String slug = matcher.group(2);
            String description = matcher.group(3).strip();
            String resource = SqlScripts.trackResource(track, type, version, slug);
            SqlStatements.load(resource);
            migrations.add(new SqlMigration(track, version, slug, description, resource));
        }
        int last = 0;
        for (SqlMigration migration : migrations) {
            if (migration.version() <= last) {
                throw new IllegalStateException(track + " migrations must increase in version order");
            }
            last = migration.version();
            if (!migration.slug().equals(migration.slug().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Migration slug must be lowercase: " + migration.slug());
            }
        }
        return List.copyOf(migrations);
    }
}
