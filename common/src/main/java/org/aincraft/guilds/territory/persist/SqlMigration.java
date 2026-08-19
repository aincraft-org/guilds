package org.aincraft.guilds.territory.persist;

/**
 * One versioned SQL resource in a migration track.
 *
 * @param track       catalog name such as {@code persist} or {@code guilds}
 * @param version     integer version recorded in {@code sql_schema_migrations}
 * @param slug        filename slug after {@code V{version}__}
 * @param description human-readable purpose stored with the applied row
 * @param resource    classpath path under {@code /sql/}
 */
public record SqlMigration(String track, int version, String slug, String description, String resource) {
    public String checksum() {
        return SqlScripts.checksum(resource);
    }
}
