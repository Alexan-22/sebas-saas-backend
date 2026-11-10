package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato SQL de V2 sobre prerrequisitos minimos, sin Hibernate ni produccion. */
@Testcontainers
class KardexMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("kardex_migration_test");

    @Test
    void realMigrationPreservesRowsAndEnforcesConstraints() {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        var source = new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres),
                postgres.getUsername(), postgres.getPassword());
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE producto_tallas (id_producto_talla integer PRIMARY KEY, stock integer NOT NULL)");
        db.execute("CREATE TABLE saas_cancelaciones_cod (tienda_id bigint, id_venta integer, UNIQUE(tienda_id,id_venta))");
        db.update("INSERT INTO producto_tallas VALUES (10,18),(11,7)");
        db.update("INSERT INTO saas_cancelaciones_cod VALUES (1,100),(2,200)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__kardex_cancelaciones_cod.sql")).execute(source);
        assertEquals(18, db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=10", Integer.class));
        String insert = "INSERT INTO inventario_kardex (tienda_id,id_venta,id_producto_talla,tipo,origen,cantidad,stock_anterior,stock_posterior,motivo,operador) VALUES (?,?,?,'ENTRADA','CANCELACION_COD',2,18,?,'Prueba de migracion','fixture')";
        assertEquals(1, db.update(insert, 1, 100, 10, 20));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, 1, 100, 10, 20));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, 2, 100, 11, 20));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, 1, 100, 999, 20));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, 1, 100, 11, 21));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM inventario_kardex WHERE tienda_id=1", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM inventario_kardex WHERE tienda_id=2", Integer.class));
    }
}
