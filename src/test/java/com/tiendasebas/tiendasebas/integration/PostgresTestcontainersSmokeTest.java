package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de infraestructura, NO prueba la lógica de CancelacionCodService.
 * Nunca crea un contexto Spring ni lee DB_URL, application-dev o Supabase.
 * Testcontainers publica PostgreSQL en un puerto aleatorio del host.
 */
@Testcontainers
class PostgresTestcontainersSmokeTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("sebas_fase3d_test")
            .withUsername("sebas_test")
            .withPassword("clave_temporal_solo_testcontainers");

    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestSafety.verifiedUrl(postgres), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void conectaSoloAlPostgresqlTemporal() throws SQLException {
        try (Connection conexion = abrir();
             Statement consulta = conexion.createStatement();
             ResultSet resultado = consulta.executeQuery(
                     "SELECT current_database(), current_user, current_setting('server_version_num')::integer")) {
            assertTrue(resultado.next());
            assertEquals("sebas_fase3d_test", resultado.getString(1));
            assertEquals("sebas_test", resultado.getString(2));
            assertTrue(resultado.getInt(3) >= 170000);
        }
    }

    @Test
    void rollbackDeshaceCambiosEnPostgresqlTemporal() throws SQLException {
        try (Connection conexion = abrir();
             Statement sql = conexion.createStatement()) {
            // Tabla TEMPORAL, solo visible en esta conexión de prueba.
            sql.execute("CREATE TEMP TABLE tc_fase3d_rollback (cantidad INTEGER NOT NULL)");
            conexion.setAutoCommit(false);
            sql.executeUpdate("INSERT INTO tc_fase3d_rollback(cantidad) VALUES (2)");
            conexion.rollback();
            try (ResultSet rs = sql.executeQuery("SELECT COUNT(*) FROM tc_fase3d_rollback")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
