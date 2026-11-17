package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.config.globaljdbc.GlobalJdbcConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapSuperadminJdbcTest {
    private int port;
    private final JsonMapper json = JsonMapper.builder().build();

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @ComponentScan(basePackages="com.tiendasebas.tiendasebas",excludeFilters=@ComponentScan.Filter(
            type=FilterType.REGEX,pattern=".*(integration|Test|TiendaSebasApplication).*"))
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(source);
            f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate",
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return f;
        }
    }

    private HttpResponse<String> call(String method,String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Host","fixture-lima.example.test")
                .header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(request.method(method,body==null?
                HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private long id(HttpResponse<String> response) throws Exception {
        assertEquals(201,response.statusCode()); return json.readTree(response.body()).get("id").asLong();
    }
    private String login(boolean global,String password) throws Exception {
        var response=call("POST",global?"/api/auth/superadmin/login":"/api/auth/login",
                json.writeValueAsString(Map.of("correo",global?"global@example.test":"boutique@example.test","password",password)),null);
        assertEquals(200,response.statusCode());return json.readTree(response.body()).get("token").asText();
    }
    private static long scalar(Connection c,String query) throws Exception {
        try(var result=c.createStatement().executeQuery(query)){assertTrue(result.next());return result.getLong(1);}
    }
    private static void denied(Connection c,String query) {
        var exception=assertThrows(java.sql.SQLException.class,()->c.createStatement().execute(query));
        assertEquals("42501",exception.getSQLState());
    }
    @Test void globalHttpJpaJdbcRollbackAndPoolIsolation() throws Exception {
        try(var pg=new GlobalPostgresFixture()) {
            pg.start();
            String boutiquePassword=UUID.randomUUID().toString();
            String authPassword=UUID.randomUUID().toString();
            String opsPassword=UUID.randomUUID().toString();
            String loginPassword="Aa9-"+UUID.randomUUID();
            try(var owner=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())) {
                owner.createStatement().execute("CREATE ROLE fixture_migrador NOLOGIN NOSUPERUSER NOBYPASSRLS;"
                        +"CREATE ROLE sebas_auth_app LOGIN PASSWORD '"+boutiquePassword+"' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;"
                        +"CREATE ROLE sebas_global_auth LOGIN PASSWORD '"+authPassword+"' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;"
                        +"CREATE ROLE sebas_global_ops LOGIN PASSWORD '"+opsPassword+"' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;"
                        +"CREATE ROLE sebas_catalogo_app NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE anon; CREATE ROLE authenticated;"
                        +"GRANT USAGE,CREATE ON SCHEMA public TO fixture_migrador; SET ROLE fixture_migrador;");
                BootstrapVacioPostgresTest.ejecutar(owner,Path.of("src/main/resources/db/bootstrap/B000__historico_vacio.sql"));
                for(int version=1;version<=22;version++) {
                    String prefix="V"+version+"__";
                    try(var files=Files.list(Path.of("src/main/resources/db/"+(version<=12?"migration":"manual")))) {
                        BootstrapVacioPostgresTest.ejecutar(owner,files.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow());
                    }
                }
                for(int version=23;version<=30;version++) {
                    String prefix="B0"+version+"__";
                    try(var files=Files.list(Path.of("src/main/resources/db/bootstrap"))) {
                        BootstrapVacioPostgresTest.ejecutar(owner,files.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow());
                    }
                }
                BootstrapVacioPostgresTest.ejecutar(owner,Path.of("docs/staging-bootstrap/fixtures-boutiques.sql"));
                owner.createStatement().execute("INSERT INTO roles(nombre_rol) VALUES('SUPER_ADMIN'),('ADMIN'),('CLIENTE'),('EMPLEADO')");
                String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(loginPassword);
                try(var insert=owner.prepareStatement("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,tienda_id) SELECT 'Fixture','Local',?, ?,id_rol,? FROM roles WHERE nombre_rol=?")) {
                    insert.setString(1,"global@example.test");insert.setString(2,hash);insert.setNull(3,java.sql.Types.BIGINT);insert.setString(4,"SUPER_ADMIN");assertEquals(1,insert.executeUpdate());
                    insert.setString(1,"boutique@example.test");insert.setLong(3,1);insert.setString(4,"ADMIN");assertEquals(1,insert.executeUpdate());
                }
                try(var insert=owner.prepareStatement("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,tienda_id) SELECT 'Cliente','Ficticio','cliente-duplicado@example.test',?,id_rol,1 FROM roles WHERE nombre_rol='CLIENTE'")) { insert.setString(1,hash); assertEquals(1,insert.executeUpdate()); }
            }
            Map<String,Object> props=new HashMap<>();
            props.put("spring.datasource.url",pg.verifiedUrl());props.put("spring.datasource.username","sebas_auth_app");props.put("spring.datasource.password",boutiquePassword);
            props.put("app.global-jdbc.authentication.url",pg.verifiedUrl());props.put("app.global-jdbc.authentication.username","sebas_global_auth");props.put("app.global-jdbc.authentication.password",authPassword);
            props.put("app.global-jdbc.operations.url",pg.verifiedUrl());props.put("app.global-jdbc.operations.username","sebas_global_ops");props.put("app.global-jdbc.operations.password",opsPassword);
            props.put("app.jwt.secret",UUID.randomUUID().toString()+UUID.randomUUID());
            var env=new StandardEnvironment();
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            env.getPropertySources().addFirst(new MapPropertySource("ephemeral-local",props));
            var app=new SpringApplication(Config.class);app.setEnvironment(env);
            try(var context=app.run("--spring.config.location=optional:classpath:/global-jdbc-empty/",
                    "--server.address=127.0.0.1","--server.port=0","--server.forward-headers-strategy=none",
                    "--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.rls.auth-enabled=true",
                    "--app.rls.superadmin-enabled=true","--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test",
                    "--app.tenant.base-domain=example.test","--app.storage.provider=local","--app.upload.dir=target/global-jdbc-uploads","--logging.level.root=WARN")) {
                port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
                var ops=context.getBean("globalOperationsJdbcPool",HikariDataSource.class);
                var auth=context.getBean("globalAuthJdbcPool",HikariDataSource.class);
                var tenant=context.getBean("boutiqueJdbcPool",HikariDataSource.class);
                for(var pool:java.util.List.of(ops,auth,tenant))try(var c=pool.getConnection()) {
                    try(var identity=c.createStatement().executeQuery("SELECT current_user")) { assertTrue(identity.next()); assertEquals(pool.getUsername(),identity.getString(1)); }
                    assertEquals(0,scalar(c,"SELECT count(*) FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)"));
                    assertEquals(0,scalar(c,"SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND pg_has_role(current_user,c.relowner,'MEMBER')"));
                }
                try(var c=ops.getConnection()) {
                    try(var metadata=c.createStatement().executeQuery("""
                            SELECT r.rolname,r.rolcanlogin,r.rolsuper,r.rolbypassrls,r.rolcreaterole,r.rolcreatedb,
                                   p.prosecdef,p.proconfig=ARRAY['search_path=pg_catalog'] AS config_locked,
                                   p.proowner=(SELECT relowner FROM pg_class WHERE oid='public.usuarios'::regclass) AS same_owner,
                                   p.prorettype='boolean'::regtype AS boolean_result,p.provolatile='s' AS stable_function
                              FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner
                             WHERE p.oid='public.global_correo_registrado(text)'::regprocedure
                            """)) {
                        assertTrue(metadata.next()); assertEquals("fixture_migrador",metadata.getString("rolname"));
                        for(String flag:java.util.List.of("rolcanlogin","rolsuper","rolbypassrls","rolcreaterole","rolcreatedb")) assertFalse(metadata.getBoolean(flag));
                        assertTrue(metadata.getBoolean("prosecdef")); assertTrue(metadata.getBoolean("config_locked"));
                        assertTrue(metadata.getBoolean("same_owner")); assertTrue(metadata.getBoolean("boolean_result")); assertTrue(metadata.getBoolean("stable_function"));
                        assertFalse(metadata.next());
                    }
                    try(var delegation=c.createStatement()) {
                        try { delegation.execute("GRANT EXECUTE ON FUNCTION public.global_correo_registrado(text) TO anon"); }
                        catch(java.sql.SQLException denied) { assertEquals("42501",denied.getSQLState()); }
                    }
                    try(var privilege=c.prepareStatement("SELECT has_function_privilege(CAST(? AS name),'public.global_correo_registrado(text)','EXECUTE')")) {
                        for(String role:java.util.List.of("anon","authenticated","sebas_auth_app","sebas_global_auth","sebas_global_ops")) {
                            privilege.setString(1,role);
                            try(var result=privilege.executeQuery()) { assertTrue(result.next()); assertEquals(role.equals("sebas_global_ops"),result.getBoolean(1)); }
                        }
                    }
                    assertEquals(0,scalar(c,"SELECT count(*) FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE p.oid='public.global_correo_registrado(text)'::regprocedure AND a.privilege_type='EXECUTE' AND (a.grantee NOT IN (p.proowner,(SELECT oid FROM pg_roles WHERE rolname='sebas_global_ops')) OR (a.grantee=(SELECT oid FROM pg_roles WHERE rolname='sebas_global_ops') AND a.is_grantable))"));
                }
                ops.setMaximumPoolSize(1);
                long operationsBackend;
                try(var c=ops.getConnection()) { operationsBackend=scalar(c,"SELECT pg_backend_pid()"); }
                try(var c=auth.getConnection()) {
                    assertEquals(1,scalar(c,"SELECT count(*) FROM usuarios"));
                    denied(c,"SELECT * FROM tiendas");denied(c,"SELECT * FROM planes_saas");
                    denied(c,"UPDATE usuarios SET nombre='invalido'");
                }
                try(var c=tenant.getConnection()) {
                    denied(c,"SELECT * FROM planes_saas");denied(c,"SET ROLE sebas_global_ops");
                    c.createStatement().execute("SELECT set_config('app.superadmin','true',false)");
                    assertEquals(0,scalar(c,"SELECT count(*) FROM usuarios WHERE tienda_id IS NULL"));
                }
                assertEquals(401,call("GET","/api/superadmin/tiendas",null,null).statusCode());
                String boutique=login(false,loginPassword);
                assertEquals(401,call("GET","/api/superadmin/tiendas",null,boutique).statusCode());
                assertEquals(401,call("POST","/api/auth/superadmin/login",json.writeValueAsString(Map.of("correo","boutique@example.test","password",loginPassword)),null).statusCode());
                String global=login(true,loginPassword);
                assertEquals(200,call("GET","/api/superadmin/me",null,global).statusCode());
                assertEquals(200,call("GET","/api/perfil",null,boutique).statusCode());
                assertEquals(200,call("PUT","/api/superadmin/tiendas/1/suspender","{}",global).statusCode());
                assertEquals(200,call("GET","/api/superadmin/me",null,global).statusCode());
                assertEquals(200,call("PUT","/api/superadmin/tiendas/1/activar","{}",global).statusCode());
                assertEquals(401,call("GET","/api/perfil",null,boutique).statusCode());
                assertEquals(200,call("GET","/api/superadmin/tiendas",null,global).statusCode());
                assertEquals(403,call("GET","/api/superadmin/configuracion",null,global).statusCode());
                long shop=id(call("POST","/api/superadmin/tiendas",json.writeValueAsString(Map.of("nombre","Boutique Global Fixture","subdominio","global-fixture")),global));
                assertEquals(200,call("PUT","/api/superadmin/tiendas/"+shop+"/suspender","{}",global).statusCode());
                assertEquals(200,call("PUT","/api/superadmin/tiendas/"+shop+"/activar","{}",global).statusCode());
                assertEquals(409,call("POST","/api/superadmin/propietarios",json.writeValueAsString(Map.of("tiendaId",2,"nombre","Duplicado","apellido","Ficticio","correo","cliente-duplicado@example.test","contrasena",loginPassword)),global).statusCode());
                var owner=call("POST","/api/superadmin/propietarios",json.writeValueAsString(Map.of("tiendaId",shop,"nombre","Dueño","apellido","Ficticio","correo","propietario@example.test","contrasena",loginPassword)),global);
                assertEquals(201,owner.statusCode());
                int ownerUser=json.readTree(owner.body()).get("usuarioId").asInt();
                assertEquals(409,call("PUT","/api/superadmin/propietarios/tiendas/2",json.writeValueAsString(Map.of("usuarioId",ownerUser)),global).statusCode());
                assertEquals(200,call("GET","/api/superadmin/propietarios",null,global).statusCode());
                long plan=id(call("POST","/api/superadmin/planes",json.writeValueAsString(Map.of("codigo","PILOTO","nombre","Plan ficticio","precioMensual",49.90,"duracionMeses",1,"beneficios",java.util.List.of())),global));
                assertEquals(200,call("PUT","/api/superadmin/suscripciones/plan-comercial",json.writeValueAsString(Map.of("planId",plan)),global).statusCode());
                assertEquals(409,call("PUT","/api/superadmin/planes/"+plan+"/desactivar","{}",global).statusCode());
                String subscription=json.writeValueAsString(Map.of("tiendaId",shop,"planId",plan,"fechaInicio",LocalDate.now(ZoneId.of("America/Lima")).toString()));
                assertEquals(201,call("POST","/api/superadmin/suscripciones",subscription,global).statusCode());
                assertEquals(409,call("POST","/api/superadmin/suscripciones",subscription,global).statusCode());
                assertEquals(200,call("GET","/api/superadmin/suscripciones",null,global).statusCode());
                try(var c=ops.getConnection()) {
                    assertEquals(1,scalar(c,"SELECT count(*) FROM suscripciones_saas WHERE tienda_id="+shop));
                    assertEquals(0,scalar(c,"SELECT count(*) FROM usuarios WHERE tienda_id IS NULL"));
                    denied(c,"SELECT * FROM auth_sesiones");denied(c,"SELECT * FROM ventas");
                    denied(c,"DELETE FROM tiendas WHERE id="+shop);
                }
                // Fallo intermedio sólo de fixture: DML y auditoría deben compartir rollback.
                try(var c=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())) {
                    c.createStatement().execute("SET ROLE fixture_migrador; CREATE FUNCTION fixture_fail_global() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fallo fixture'; END $$; CREATE TRIGGER fixture_fail_global BEFORE INSERT ON saas_actividad FOR EACH ROW EXECUTE FUNCTION fixture_fail_global();");
                }
                var failed=call("POST","/api/superadmin/tiendas",json.writeValueAsString(Map.of("nombre","Rollback Fixture","subdominio","rollback-fixture")),global);
                assertEquals(503,failed.statusCode());
                assertFalse(failed.body().contains("fixture_fail_global"));
                assertFalse(failed.body().contains("INSERT INTO"));
                try(var c=ops.getConnection()){assertEquals(0,scalar(c,"SELECT count(*) FROM tiendas WHERE subdominio='rollback-fixture'"));}
                var failedOwner=call("POST","/api/superadmin/propietarios",json.writeValueAsString(Map.of("tiendaId",2,"nombre","Rollback","apellido","Owner","correo","rollback-owner@example.test","contrasena",loginPassword)),global);
                assertEquals(503,failedOwner.statusCode());
                try(var c=ops.getConnection()){
                    assertEquals(0,scalar(c,"SELECT count(*) FROM usuarios WHERE correo='rollback-owner@example.test'"));
                    assertEquals(0,scalar(c,"SELECT count(*) FROM tienda_propietarios WHERE tienda_id=2"));
                }
                try(var c=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())){c.createStatement().execute("SET ROLE fixture_migrador; DROP TRIGGER fixture_fail_global ON saas_actividad; DROP FUNCTION fixture_fail_global();");}
                // Reutilización de conexiones y mezcla de solicitudes nunca promociona al cliente boutique.
                try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    for(int n=0;n<3;n++) {
                        var allowed=executor.submit(()->call("GET","/api/superadmin/planes",null,global).statusCode());
                        var rejected=executor.submit(()->call("GET","/api/superadmin/planes",null,boutique).statusCode());
                        assertEquals(200,allowed.get());assertEquals(401,rejected.get());
                    }
                }
                try(var c=ops.getConnection()) {
                    assertEquals(operationsBackend,scalar(c,"SELECT pg_backend_pid()"));
                    try(var state=c.createStatement().executeQuery("SELECT coalesce(nullif(current_setting('app.tenant_id',true),''),'SIN_TENANT')")) { assertTrue(state.next()); assertEquals("SIN_TENANT",state.getString(1)); }
                }
                assertEquals(204,call("POST","/api/superadmin/auth/logout","{}",global).statusCode());
                assertEquals(401,call("GET","/api/superadmin/me",null,global).statusCode());
            }
        }
    }
}
