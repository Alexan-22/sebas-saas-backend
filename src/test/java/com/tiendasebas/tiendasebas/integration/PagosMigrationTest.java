package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PagosMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("pagos_v4_test");

    @Test
    void v4PreservesOrdersAndEnforcesTenantCurrencyAndIdempotency() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        var source = new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres), postgres.getUsername(), postgres.getPassword());
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE tiendas(id bigint PRIMARY KEY)");
        db.execute("CREATE TABLE ventas(id_venta integer PRIMARY KEY, tienda_id bigint NOT NULL REFERENCES tiendas(id), total numeric(12,2))");
        db.update("INSERT INTO tiendas VALUES (1),(2)");
        db.update("INSERT INTO ventas VALUES (10,1,20),(20,2,30)");
        var before = db.queryForList("SELECT * FROM ventas ORDER BY id_venta");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__pagos_simulados_persistentes.sql")).populate(connection);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback(); throw failure;
            }
        }
        assertEquals(before, db.queryForList("SELECT * FROM ventas ORDER BY id_venta"));
        String insert = "INSERT INTO pagos_simulados(id,tienda_id,id_venta,clave_idempotencia,importe,moneda,estado) VALUES (?,?,?,'same-key',20,?,'PENDIENTE')";
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, UUID.randomUUID(), 2, 10, "PEN"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, UUID.randomUUID(), 1, 10, "USD"));
        assertEquals(1, db.update(insert, UUID.randomUUID(), 1, 10, "PEN"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, UUID.randomUUID(), 1, 10, "PEN"));
        assertEquals(1, db.update(insert, UUID.randomUUID(), 2, 20, "PEN"));
        db.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor) VALUES (1,'SANDBOX'),(2,'SANDBOX')");
        assertEquals(0, db.queryForObject("SELECT count(*) FROM pagos_proveedor_configuracion WHERE habilitado", Integer.class));
    }
}
