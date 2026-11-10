package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ClienteDocumentoMigrationTest {
    private static ResourceDatabasePopulator migration() {
        var script = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V3__cliente_documento_por_tienda.sql"));
        // Enviar el archivo completo al parser PostgreSQL, incluyendo DO $$.
        script.setSeparator(org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR);
        return script;
    }
    @Test
    void rejectsNullTenantWithoutChangingRowsOrConstraint() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        try (var isolated = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("v3_invalid")) {
            isolated.start();
            var source = new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(isolated), isolated.getUsername(), isolated.getPassword());
            var db = new JdbcTemplate(source);
            db.execute("CREATE TABLE clientes_presenciales (id integer PRIMARY KEY, tienda_id bigint, tipo_documento text, numero_documento text, CONSTRAINT uk_cliente_presencial_documento UNIQUE(tipo_documento,numero_documento))");
            db.update("INSERT INTO clientes_presenciales VALUES (1,NULL,'DNI','12345678')");
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                var failure = assertThrows(org.springframework.jdbc.datasource.init.ScriptException.class,
                        () -> migration().populate(connection));
                assertTrue(failure.getMostSpecificCause().getMessage().contains("Existen clientes sin tenant valido"));
                connection.rollback();
            }
            assertEquals(1, db.queryForObject("SELECT count(*) FROM clientes_presenciales WHERE tienda_id IS NULL", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='clientes_presenciales'::regclass AND conname='uk_cliente_presencial_documento'", Integer.class));
        }
    }
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("clientes_v3_test");

    @Test
    void rejectsExistingDuplicates() throws Exception {
        rejectIncompatible("", "(1,1,'DNI','123'),(2,1,'DNI','123')",
                "Existen documentos duplicados", 2);
    }

    @Test
    void rejectsUnexpectedConstraint() throws Exception {
        rejectIncompatible(", CONSTRAINT uk_cliente_presencial_documento UNIQUE(numero_documento)",
                "(1,1,'DNI','123')", "Restriccion documental distinta", 1);
    }

    private void rejectIncompatible(String constraint, String rows, String expected, int count) throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        try (var isolated = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("v3_incompatible")) {
            isolated.start();
            var source = new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(isolated), isolated.getUsername(), isolated.getPassword());
            var db = new JdbcTemplate(source);
            db.execute("CREATE TABLE clientes_presenciales (id integer PRIMARY KEY, tienda_id bigint, tipo_documento text, numero_documento text" + constraint + ")");
            db.update("INSERT INTO clientes_presenciales VALUES " + rows);
            var before = db.queryForList("SELECT * FROM clientes_presenciales ORDER BY id");
            var constraints = db.queryForList("SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid='clientes_presenciales'::regclass ORDER BY conname");
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                var failure = assertThrows(org.springframework.jdbc.datasource.init.ScriptException.class,
                        () -> migration().populate(connection));
                assertTrue(failure.getMostSpecificCause().getMessage().contains(expected));
                connection.rollback();
            }
            assertEquals(count, before.size());
            assertEquals(before, db.queryForList("SELECT * FROM clientes_presenciales ORDER BY id"));
            assertEquals(constraints, db.queryForList("SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid='clientes_presenciales'::regclass ORDER BY conname"));
        }
    }

    @Test
    void migrationPreservesClientsAndScopesDocuments() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        var source = new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres),
                postgres.getUsername(), postgres.getPassword());
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE clientes_presenciales (id integer PRIMARY KEY, tienda_id bigint, tipo_documento text NOT NULL, numero_documento text NOT NULL, CONSTRAINT uk_cliente_presencial_documento UNIQUE(tipo_documento,numero_documento))");
        db.update("INSERT INTO clientes_presenciales VALUES (1,1,'DNI','12345678')");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                migration().populate(connection);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM clientes_presenciales WHERE id=1 AND tienda_id=1", Integer.class));
        assertEquals(1, db.update("INSERT INTO clientes_presenciales VALUES (2,2,'DNI','12345678')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> db.update("INSERT INTO clientes_presenciales VALUES (3,1,'DNI','12345678')"));
        assertEquals(2, db.queryForObject("SELECT count(*) FROM clientes_presenciales", Integer.class));
    }
}
