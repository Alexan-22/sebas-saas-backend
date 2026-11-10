package com.tiendasebas.tiendasebas.integration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostgresTestSafetyTest {
    @Test
    void rejectsExternalDestinationsAndConfigurationOverrides() {
        for (String name : new String[]{"DB_URL", "DATABASE_URL", "SPRING_DATASOURCE_URL",
                "spring.datasource.url", "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION",
                "SPRING_FLYWAY_URL", "spring.jpa.properties.hibernate.connection.url",
                "jakarta.persistence.jdbc.url", "SPRING_PROFILES_ACTIVE"}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> PostgresTestSafety.rejectExternalConfiguration(Map.of(name, "sensitive-value")), name);
            assertFalse(error.getMessage().contains("sensitive-value"));
        }
    }

    @Test
    void allowsUnrelatedEnvironment() {
        assertDoesNotThrow(() -> PostgresTestSafety.rejectExternalConfiguration(Map.of("JAVA_HOME", "jdk", "PATH", "bin")));
    }
}
