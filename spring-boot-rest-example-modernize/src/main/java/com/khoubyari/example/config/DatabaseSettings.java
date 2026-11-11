package com.khoubyari.example.config;

import java.util.Map;
import java.util.Objects;

/** Immutable settings. Never include credentials in log output. */
public final class DatabaseSettings {
    private final String url;
    private final String username;
    private final String password;
    private final String schemaMode;
    private final String serviceName;
    private final int poolSize;

    public DatabaseSettings(String url, String username, String password,
                            String schemaMode, String serviceName, int poolSize) {
        this.url = required(url, "DB_URL");
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("Only PostgreSQL JDBC URLs are supported");
        }
        this.username = required(username, "DB_USERNAME");
        this.password = required(password, "DB_PASSWORD");
        if (!java.util.Set.of("validate", "update", "none").contains(schemaMode)) {
            throw new IllegalArgumentException("DB_SCHEMA_MODE must be validate, update or none");
        }
        this.schemaMode = schemaMode;
        this.serviceName = required(serviceName, "HOTEL_SERVICE_NAME");
        if (poolSize < 1 || poolSize > 100) throw new IllegalArgumentException("DB_POOL_SIZE must be 1..100");
        this.poolSize = poolSize;
    }

    public static DatabaseSettings fromEnvironment() {
        return from(System.getenv());
    }

    public static DatabaseSettings from(Map<String, String> values) {
        Objects.requireNonNull(values, "settings");
        return new DatabaseSettings(values.getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/bootexample"),
                values.getOrDefault("DB_USERNAME", "bootexample"), values.get("DB_PASSWORD"),
                values.getOrDefault("DB_SCHEMA_MODE", "validate"),
                values.getOrDefault("HOTEL_SERVICE_NAME", "default profile:"),
                Integer.parseInt(values.getOrDefault("DB_POOL_SIZE", "10")));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getSchemaMode() { return schemaMode; }
    public String getServiceName() { return serviceName; }
    public int getPoolSize() { return poolSize; }

    @Override public String toString() {
        return "DatabaseSettings[credentials=REDACTED, schemaMode=" + schemaMode + ", poolSize=" + poolSize + "]";
    }
}
