package com.tiendasebas.tiendasebas.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Ejecuta V26 y verifica RLS en una instancia PostgreSQL privada creada por la fixture. */
class CheckoutInvitadoMigracionRlsTest {
    @Test
    void migracionRepetibleYSeparacionMultitienda() throws Exception {
        try (var pg = new ComercialPostgresFixture()) {
            pg.start();
            String url = pg.verifiedUrl();
            String sql = Files.readString(Path.of("src/main/resources/db/migration/V26__checkout_invitado_multitienda_candidato.sql"), StandardCharsets.UTF_8);
            try (Connection c = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword()); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE public.ventas (id_venta INTEGER PRIMARY KEY, tienda_id BIGINT NOT NULL, UNIQUE(tienda_id,id_venta))");
                for (String tabla : new String[]{"yape_pagos_manuales","transferencia_pagos_manuales","transferencia_comprobantes"})
                    s.execute("CREATE TABLE public." + tabla + " (usuario_id BIGINT NOT NULL)");
                s.execute(sql);
                s.execute(sql); // la migracion debe admitir tablas preexistentes
                for (String tabla : new String[]{"yape_pagos_manuales","transferencia_pagos_manuales","transferencia_comprobantes"}) {
                    try (var rs = s.executeQuery("SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='"+tabla+"' AND column_name='usuario_id'")) {
                        assertTrue(rs.next());
                        assertEquals("YES", rs.getString(1), tabla);
                    }
                }
                s.execute("INSERT INTO public.ventas(id_venta, tienda_id) VALUES (101,1),(202,2),(203,2)");
                s.execute("INSERT INTO public.venta_clientes_invitados(tienda_id,id_venta,nombres,apellidos,correo,telefono) VALUES (1,101,'Ana','Uno','ana@example.test','912345678'),(2,202,'Beto','Dos','beto@example.test','923456789')");
                s.execute("CREATE ROLE checkout_test_user NOLOGIN NOBYPASSRLS");
                s.execute("GRANT USAGE ON SCHEMA public TO checkout_test_user");
                for (String tabla : new String[]{"venta_clientes_invitados","checkout_invitado_reintentos","checkout_invitado_accesos"}) {
                    s.execute("GRANT SELECT,INSERT ON public." + tabla + " TO checkout_test_user");
                    try (var rs=s.executeQuery("SELECT relrowsecurity FROM pg_class WHERE oid = 'public."+tabla+"'::regclass")) {
                        assertTrue(rs.next()); assertTrue(rs.getBoolean(1), tabla+" carece de RLS");
                    }
                }
            }
            try (Connection c = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword()); Statement s = c.createStatement()) {
                s.execute("SET ROLE checkout_test_user");
                assertEquals(0, count(s)); // sin tenant, acceso denegado por RLS
                s.execute("SELECT set_config('app.tenant_id','1',false)");
                assertEquals(1, count(s));
                try (var rs=s.executeQuery("SELECT id_venta FROM public.venta_clientes_invitados")) {
                    assertTrue(rs.next()); assertEquals(101,rs.getInt(1)); assertFalse(rs.next());
                }
                assertThrows(SQLException.class, () -> s.execute("INSERT INTO public.venta_clientes_invitados(tienda_id,id_venta,nombres,apellidos,correo,telefono) VALUES (2,203,'Eve','Otro','eve@example.test','934567890')"));
                s.execute("SELECT set_config('app.tenant_id','2',false)");
                assertEquals(1, count(s));
                try (var rs=s.executeQuery("SELECT id_venta FROM public.venta_clientes_invitados")) {
                    assertTrue(rs.next()); assertEquals(202,rs.getInt(1)); assertFalse(rs.next());
                }
            }
        }
    }
    private int count(Statement s) throws SQLException {
        try(var rs=s.executeQuery("SELECT count(*) FROM public.venta_clientes_invitados")) {
            assertTrue(rs.next()); return rs.getInt(1);
        }
    }
}
