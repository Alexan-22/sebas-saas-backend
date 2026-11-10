package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class YapeMigrationTest {
    @Test void v8PreservesV7PaymentsAndEnforcesRetryAndReplenishmentConstraints() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        try(var postgres=new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("yape_migration_test")
                .withUsername("test").withPassword(UUID.randomUUID().toString())) {
            postgres.start();
            var source=new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres),postgres.getUsername(),postgres.getPassword());
            var db=new JdbcTemplate(source);
            db.execute("CREATE TABLE tiendas(id bigint PRIMARY KEY)");
            db.execute("CREATE TABLE usuarios(id_usuario integer PRIMARY KEY)");
            db.execute("CREATE TABLE ventas(id_venta integer PRIMARY KEY, tienda_id bigint NOT NULL REFERENCES tiendas(id))");
            db.execute("CREATE TABLE producto_tallas(id_producto_talla integer PRIMARY KEY)");
            db.update("INSERT INTO tiendas VALUES(1),(2)");db.update("INSERT INTO usuarios VALUES(1),(2)");
            db.update("INSERT INTO ventas VALUES(10,1),(20,2)");db.update("INSERT INTO producto_tallas VALUES(1)");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__yape_pagos_manuales.sql")).execute(source);
            for(int i=1;i<=2;i++) db.update("""
                    INSERT INTO yape_pagos_manuales(tienda_id,id_venta,usuario_id,correo_cliente,importe,numero_yape,titular_yape,qr_imagen,qr_sha256)
                    VALUES(?,?,?,'fixture@example.test',45,'900000001','Fixture',?,?)
                    """,i,i*10,i,new byte[]{1,2,3},"a".repeat(64));
            var before=db.queryForList("SELECT tienda_id,id_venta,importe,estado,encode(qr_imagen,'hex') AS qr FROM yape_pagos_manuales ORDER BY id_venta");
            try(var connection=source.getConnection()) {
                connection.setAutoCommit(false);
                try { new ResourceDatabasePopulator(new ClassPathResource("db/migration/V8__yape_checkout_idempotencia.sql")).populate(connection);connection.commit(); }
                catch(Exception failure){connection.rollback();throw failure;}
            }
            assertEquals(before,db.queryForList("SELECT tienda_id,id_venta,importe,estado,encode(qr_imagen,'hex') AS qr FROM yape_pagos_manuales ORDER BY id_venta"));
            UUID key=UUID.randomUUID();
            String retry="INSERT INTO yape_checkout_reintentos(tienda_id,usuario_id,clave,huella,id_venta) VALUES(?,?,?,? ,?)";
            assertEquals(1,db.update(retry,1,1,key,"b".repeat(64),10));
            assertEquals("23505",assertThrows(DataIntegrityViolationException.class,()->db.update(retry,1,1,key,"b".repeat(64),10)).getMostSpecificCause() instanceof java.sql.SQLException sql?sql.getSQLState():"not SQL");
            assertEquals("23503",assertThrows(DataIntegrityViolationException.class,()->db.update(retry,2,2,UUID.randomUUID(),"b".repeat(64),10)).getMostSpecificCause() instanceof java.sql.SQLException sql?sql.getSQLState():"not SQL");
            assertEquals(1,db.update(retry,2,2,key,"b".repeat(64),20));
            String ledger="INSERT INTO yape_inventario_reposiciones(tienda_id,id_venta,id_producto_talla,cantidad,stock_anterior,stock_posterior,actor_correo) VALUES(1,10,1,1,19,?,'admin@example.test')";
            assertEquals("23514",assertThrows(DataIntegrityViolationException.class,()->db.update(ledger,21)).getMostSpecificCause() instanceof java.sql.SQLException sql?sql.getSQLState():"not SQL");
            assertEquals(1,db.update(ledger,20));
            assertEquals("23505",assertThrows(DataIntegrityViolationException.class,()->db.update(ledger,20)).getMostSpecificCause() instanceof java.sql.SQLException sql?sql.getSQLState():"not SQL");
            assertEquals(2,db.queryForObject("SELECT count(*) FROM yape_pagos_manuales",Integer.class));
        }
    }
}
