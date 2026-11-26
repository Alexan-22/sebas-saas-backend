package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.tiendasebas.tiendasebas.integration.BootstrapVacioPostgresTest.ejecutar;
import static com.tiendasebas.tiendasebas.integration.BootstrapVacioPostgresTest.scalar;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutInvitadoMigracionRlsTest {
    private static final Path DB = Path.of("src/main/resources/db");
    private static final Path V26 = DB.resolve("migration/V26__checkout_invitado_multitienda_candidato.sql");
    private static final String APP = "checkout_test_user";
    private static final List<String> TABLAS = List.of(
            "venta_clientes_invitados", "checkout_invitado_reintentos", "checkout_invitado_accesos");

    @ParameterizedTest(name = "V26 con datos previos: {0}")
    @ValueSource(booleans = {false, true})
    void migraYProtegeConUnaConexionRealDeAplicacion(boolean conDatosPrevios) throws Exception {
        try (var pg = new ComercialPostgresFixture()) {
            pg.start();
            String url = pg.verifiedUrl();
            String password = UUID.randomUUID().toString();
            try (var owner = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword());
                 var sql = owner.createStatement()) {
                instalarBase(owner);
                if (conDatosPrevios) {
                    registrarVentas(owner);
                    sql.execute("""
                            INSERT INTO yape_pagos_manuales
                                (tienda_id,id_venta,usuario_id,correo_cliente,importe,numero_yape,
                                 titular_yape,qr_imagen,qr_sha256)
                            VALUES (1,101,1,'registrado@example.test',10,'900000000','Fixture',
                                    decode('00','hex'),repeat('a',64));
                            INSERT INTO transferencia_cuentas_bancarias
                                (id,tienda_id,banco,titular,numero_cuenta,habilitado)
                            VALUES (1,2,'BCP','Fixture','0000000',true);
                            INSERT INTO transferencia_pagos_manuales
                                (tienda_id,id_venta,usuario_id,correo_cliente,cuenta_bancaria_id,importe,
                                 banco,titular,numero_cuenta)
                            VALUES (2,202,2,'registrado@example.test',1,10,'BCP','Fixture','0000000');
                            INSERT INTO transferencia_comprobantes
                                (tienda_id,id_venta,usuario_id,imagen,mime_tipo,tamano_bytes,sha256)
                            VALUES (2,202,2,decode('00','hex'),'image/png',1,repeat('a',64))
                            """);
                }

                ejecutar(owner, V26);
                if (conDatosPrevios) {
                    assertEquals(1, scalar(owner, "SELECT usuario_id FROM yape_pagos_manuales WHERE id_venta=101"));
                    assertEquals(2, scalar(owner, "SELECT usuario_id FROM transferencia_pagos_manuales WHERE id_venta=202"));
                    assertEquals(2, scalar(owner, "SELECT usuario_id FROM transferencia_comprobantes WHERE id_venta=202"));
                } else {
                    registrarVentas(owner);
                }

                sql.execute("CREATE ROLE " + APP + " LOGIN PASSWORD '" + password
                        + "' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE");
                sql.execute("GRANT USAGE ON SCHEMA public TO " + APP);
                for (String tabla : TABLAS) {
                    sql.execute("GRANT SELECT, INSERT ON public." + tabla + " TO " + APP);
                    if (conDatosPrevios) {
                        sql.execute("CREATE POLICY fixture_previa ON public." + tabla + " USING (true)");
                    }
                }
                insertarInvitados(owner);
                // Reaplicar con tablas y datos ya presentes, incluidas políticas permisivas anteriores.
                ejecutar(owner, V26);
            }

            try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
                pool.setJdbcUrl(url);
                pool.setUsername(APP);
                pool.setPassword(password);
                pool.setMaximumPoolSize(1);
                pool.setMinimumIdle(0);
                long pid;
                try (var app = pool.getConnection(); var sql = app.createStatement()) {
                    assertEquals(1, scalar(app, """
                            SELECT count(*) FROM pg_roles
                            WHERE rolname=current_user AND current_user=session_user
                                AND rolname='checkout_test_user' AND NOT rolsuper AND NOT rolbypassrls
                            """));
                    assertEquals(0, scalar(app, """
                            SELECT count(*) FROM pg_class
                            WHERE relname IN ('venta_clientes_invitados','checkout_invitado_reintentos','checkout_invitado_accesos')
                                AND (pg_get_userbyid(relowner)=current_user OR NOT relrowsecurity)
                            """));
                    pid = scalar(app, "SELECT pg_backend_pid()");
                    assertFilas(app, 0);
                    app.setAutoCommit(false);
                    sql.execute("SELECT set_config('app.tenant_id','1',true)");
                    assertFilas(app, 1);
                    assertEquals(101, scalar(app, "SELECT id_venta FROM venta_clientes_invitados"));
                    assertEquals("42501", assertThrows(SQLException.class, () -> sql.execute("""
                            INSERT INTO venta_clientes_invitados(tienda_id,id_venta,nombres,apellidos,correo,telefono)
                            VALUES (2,203,'Fixture','Local','guest@example.test','900000001')
                            """)).getSQLState());
                    app.rollback();
                }
                try (var app = pool.getConnection(); var sql = app.createStatement()) {
                    assertEquals(pid, scalar(app, "SELECT pg_backend_pid()"));
                    assertFilas(app, 0);
                    app.setAutoCommit(false);
                    sql.execute("SELECT set_config('app.tenant_id','2',true)");
                    assertFilas(app, 1);
                    assertEquals(202, scalar(app, "SELECT id_venta FROM venta_clientes_invitados"));
                    app.commit();
                }
                try (var app = pool.getConnection()) {
                    assertEquals(pid, scalar(app, "SELECT pg_backend_pid()"));
                    assertFilas(app, 0);
                }
            }
        }
    }

    private static void instalarBase(Connection owner) throws Exception {
        ejecutar(owner, DB.resolve("bootstrap/B000__historico_vacio.sql"));
        for (int numero = 1; numero <= 12; numero++) {
            String prefijo = "V" + numero + "__";
            try (var archivos = Files.list(DB.resolve("migration"))) {
                ejecutar(owner, archivos.filter(path -> path.getFileName().toString().startsWith(prefijo))
                        .findFirst().orElseThrow());
            }
        }
    }

    private static void registrarVentas(Connection owner) throws SQLException {
        try (var sql = owner.createStatement()) {
            sql.execute("""
                    INSERT INTO tiendas(id,nombre,subdominio)
                    VALUES (1,'Tienda fixture 1','fixture-uno'),(2,'Tienda fixture 2','fixture-dos');
                    INSERT INTO roles(id_rol,nombre_rol) VALUES (1,'CLIENTE');
                    INSERT INTO usuarios(id_usuario,nombre,apellido,correo,password_hash,id_rol,tienda_id)
                    VALUES (1,'Fixture','Uno','uno@example.test','fixture',1,1),
                           (2,'Fixture','Dos','dos@example.test','fixture',1,2);
                    INSERT INTO ventas(id_venta,tienda_id,id_usuario,tipo_venta,fecha_venta,total,estado,medio_pago)
                    VALUES (101,1,1,'ONLINE',now(),10,'PAGO_PENDIENTE','YAPE'),
                           (202,2,2,'ONLINE',now(),10,'PAGO_PENDIENTE','TRANSFERENCIA'),
                           (203,2,null,'ONLINE',now(),10,'PAGO_PENDIENTE','YAPE')
                    """);
        }
    }

    private static void insertarInvitados(Connection owner) throws SQLException {
        try (var sql = owner.createStatement()) {
            sql.execute("""
                    INSERT INTO venta_clientes_invitados(tienda_id,id_venta,nombres,apellidos,correo,telefono)
                    VALUES (1,101,'Fixture','Uno','uno@example.test','900000001'),
                           (2,202,'Fixture','Dos','dos@example.test','900000002');
                    INSERT INTO checkout_invitado_reintentos(tienda_id,clave,huella,id_venta)
                    SELECT tienda_id,gen_random_uuid(),repeat('a',64),id_venta FROM venta_clientes_invitados;
                    INSERT INTO checkout_invitado_accesos(tienda_id,id_venta,token_hash,vence_en)
                    SELECT tienda_id,id_venta,repeat(tienda_id::text,64),now()+interval '1 day'
                    FROM venta_clientes_invitados
                    """);
        }
    }

    private static void assertFilas(Connection app, int cantidad) throws Exception {
        for (String tabla : TABLAS) {
            assertEquals(cantidad, scalar(app, "SELECT count(*) FROM public." + tabla), tabla);
        }
    }
}
