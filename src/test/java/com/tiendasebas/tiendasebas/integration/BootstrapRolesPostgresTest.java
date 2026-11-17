package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.nio.file.*;import java.sql.*;import static org.junit.jupiter.api.Assertions.*;
/** Roles sin privilegios administrativos: prueba negativa, no acredita compatibilidad JDBC completa. */
class BootstrapRolesPostgresTest {
 @Test void propietarioSeparadoYJdbcFallaCerrado()throws Exception{
  try(var pg=new ComercialPostgresFixture()){
   pg.start();
   try(var c=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())){
    try(var s=c.createStatement()){
     s.execute("CREATE ROLE fixture_owner NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE; CREATE ROLE fixture_jdbc NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE; CREATE ROLE anon; CREATE ROLE authenticated;");
     s.execute("GRANT USAGE,CREATE ON SCHEMA public TO fixture_owner; GRANT USAGE ON SCHEMA public TO fixture_jdbc;");
     s.execute("SET ROLE fixture_owner");
    }
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B000__historico_vacio.sql"));
    for(int n=1;n<=22;n++){final String p="V"+n+"__";try(var files=Files.list(Path.of("src/main/resources/db/"+(n<=12?"migration":"manual")))){BootstrapVacioPostgresTest.ejecutar(c,files.filter(f->f.getFileName().toString().startsWith(p)).findFirst().orElseThrow());}}
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B023__cerrar_acceso_publico.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("docs/staging-bootstrap/fixtures-boutiques.sql"));
    try(var s=c.createStatement()){
     // Sólo lectura de una tabla para distinguir ACL de RLS; no grants masivos.
     s.execute("GRANT SELECT ON tiendas TO fixture_jdbc; RESET ROLE; SET SESSION AUTHORIZATION fixture_jdbc");
     assertEquals(0,BootstrapVacioPostgresTest.scalar(c,"SELECT count(*) FROM tiendas"));
     assertEquals(0,BootstrapVacioPostgresTest.scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls)"));
     assertEquals(0,BootstrapVacioPostgresTest.scalar(c,"SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner=current_user"));
     var denied=assertThrows(SQLException.class,()->s.executeQuery("SELECT * FROM usuarios"));assertEquals("42501",denied.getSQLState());
     denied=assertThrows(SQLException.class,()->s.execute("INSERT INTO tiendas(nombre,subdominio,estado) VALUES('Intruso','intruso','ACTIVA')"));assertEquals("42501",denied.getSQLState());
     denied=assertThrows(SQLException.class,()->s.execute("SET ROLE fixture_owner"));assertEquals("42501",denied.getSQLState());
     s.execute("RESET SESSION AUTHORIZATION");
     assertEquals(2,BootstrapVacioPostgresTest.scalar(c,"SELECT count(*) FROM tiendas"));
     assertEquals(0,BootstrapVacioPostgresTest.scalar(c,"SELECT count(*) FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','authenticated','PUBLIC')"));
    }
   }
  }
 }
}

