package com.tiendasebas.tiendasebas.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.util.Set;
import java.util.Map;
import java.util.Locale;

/** Valida el destino antes de abrir conexiones y ejecutar DDL de pruebas. */
final class PostgresTestSafety {
    private PostgresTestSafety() {}

    static void rejectExternalConfiguration(Map<?, ?> values) {
        for (Object key : values.keySet()) {
            String name = key.toString().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            if (name.startsWith("SPRING_DATASOURCE_") || name.startsWith("SPRING_JPA_")
                    || name.startsWith("SPRING_FLYWAY_") || name.startsWith("SPRING_LIQUIBASE_")
                    || name.startsWith("SPRING_SQL_INIT_") || name.startsWith("SPRING_CONFIG_")
                    || name.startsWith("SPRING_PROFILES_") || name.startsWith("HIBERNATE_")
                    || name.startsWith("JAKARTA_PERSISTENCE_") || name.startsWith("SUPABASE_")
                    || Set.of("DB_URL", "DATABASE_URL", "JDBC_URL", "SPRING_APPLICATION_JSON",
                            "DB_USERNAME", "DB_USER", "DB_PASSWORD", "DATABASE_USERNAME",
                            "DATABASE_PASSWORD").contains(name)) {
                throw new IllegalStateException("Configuracion externa prohibida en pruebas: " + key);
            }
        }
    }

    static String verifiedUrl(PostgreSQLContainer container) {
        if (!container.isRunning()) {
            throw new IllegalStateException("PostgreSQL temporal no esta iniciado");
        }
        String url = container.getJdbcUrl();
        URI address = URI.create(url.substring("jdbc:".length()));
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(address.getHost())
                || address.getPort() != container.getMappedPort(5432)
                || !address.getPath().equals("/" + container.getDatabaseName())) {
            throw new IllegalStateException("Destino PostgreSQL de pruebas no permitido");
        }
        return url;
    }
}
