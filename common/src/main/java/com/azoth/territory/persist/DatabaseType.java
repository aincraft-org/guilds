package com.azoth.territory.persist;

import java.util.Locale;

public enum DatabaseType {
    POSTGRESQL,
    MYSQL;

    public static DatabaseType parse(String value) {
        if (value == null || value.isBlank()) {
            return POSTGRESQL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "postgresql", "postgres" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            default -> throw new IllegalArgumentException(
                    "Unknown database.type '" + value + "'; expected postgresql or mysql");
        };
    }
}
