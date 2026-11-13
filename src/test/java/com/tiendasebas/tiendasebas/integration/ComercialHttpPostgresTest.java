package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.config.SecurityConfig;
import com.tiendasebas.tiendasebas.config.TenantResolver;
import com.tiendasebas.tiendasebas.controller.*;
import com.tiendasebas.tiendasebas.entity.*;
import com.tiendasebas.tiendasebas.security.*;
import com.tiendasebas.tiendasebas.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP, JWT, RBAC, JPA/JDBC y PostgreSQL reales. No ejecuta scripts de migración. */
@Testcontainers
@Timeout(60)
class ComercialHttpPostgresTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("comercial_aislado").withUsername("test")
            .withPassword(UUID.randomUUID().toString());
    static final String secret = UUID.randomUUID().toString() + UUID.randomUUID();
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static ConfigurableApplicationContext context;
    static JdbcTemplate db;
    static int port;
    static boolean esquemaCreado;
    static String superadmin, admin, cliente, empleado, superConTenant;
    static Long tienda1, tienda2;
    static final JsonMapper json = JsonMapper.builder().build();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @EnableMethodSecurity
    @EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, TenantResolver.class,
            SuperAdminPlanController.class, SuperAdminSuscripcionController.class,
            SuperAdminReporteSaasController.class, ComercialExceptionHandler.class,
            PlanComercialService.class, SuscripcionService.class, SaasActividadService.class, ReporteComercialService.class})
    static class Application {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres), postgres.getUsername(), postgres.getPassword());
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.tiendasebas.tiendasebas.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", esquemaCreado ? "validate" : "create",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean UserDetailsService noBasicLogin() { return username -> { throw new UsernameNotFoundException("Solo JWT en esta fixture"); }; }
    }

    static void startContext() {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        String url = PostgresTestSafety.verifiedUrl(postgres);
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("postgres-desechable", Map.of(
                "spring.datasource.url", url, "spring.datasource.username", postgres.getUsername(),
                "spring.datasource.password", postgres.getPassword(), "app.jwt.secret", secret)));
        var app = new SpringApplication(Application.class);
        app.setEnvironment(env);
        app.addInitializers(ctx -> {
            if (!url.equals(ctx.getEnvironment().getProperty("spring.datasource.url"))) throw new IllegalStateException("Datasource externo rechazado");
        });
        context = app.run("--spring.config.location=optional:classpath:/comercial-sin-config/",
                "--spring.profiles.active=comercial-test", "--server.address=127.0.0.1", "--server.port=0",
                "--spring.sql.init.mode=never", "--spring.flyway.enabled=false", "--spring.liquibase.enabled=false",
                "--spring.jpa.open-in-view=false", "--app.jwt.expiration=3600000", "--app.saas.plan-comercial-id=0",
                "--app.cors.allowed-origins=http://fixture.example.test", "--app.tenant.base-domain=saas.test",
                "--logging.level.root=WARN");
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
        db = new JdbcTemplate(context.getBean(DataSource.class));
        esquemaCreado = true;
    }

    @BeforeAll static void start() {
        startContext();
        // DDL exclusivo de la fixture en Testcontainers. No lee ni ejecuta archivos SQL.
        db.execute("CREATE TABLE planes_saas(id BIGSERIAL PRIMARY KEY, codigo varchar(50) UNIQUE NOT NULL, nombre varchar(100) NOT NULL, descripcion text, precio_mensual numeric(12,2) NOT NULL, moneda char(3) NOT NULL DEFAULT 'PEN', duracion_meses integer NOT NULL, limite_productos integer, limite_usuarios integer, limite_sucursales integer, beneficios jsonb NOT NULL DEFAULT '[]', activo boolean NOT NULL DEFAULT TRUE, creado_en timestamptz NOT NULL DEFAULT now(), actualizado_en timestamptz NOT NULL DEFAULT now())");
        db.execute("CREATE TABLE saas_configuracion(clave varchar(100) PRIMARY KEY, valor text NOT NULL, actualizado_en timestamptz NOT NULL DEFAULT now())");
        db.execute("CREATE TABLE suscripciones_saas(id BIGSERIAL PRIMARY KEY, tienda_id bigint NOT NULL REFERENCES tiendas(id), plan_id bigint NOT NULL REFERENCES planes_saas(id), estado varchar(20) NOT NULL CHECK(estado IN ('ACTIVA','PENDIENTE','VENCIDA','CANCELADA')), fecha_inicio date NOT NULL, fecha_fin date NOT NULL CHECK(fecha_fin>fecha_inicio), precio_contratado numeric(12,2) NOT NULL CHECK(precio_contratado>=0), moneda char(3) NOT NULL, creado_en timestamptz NOT NULL DEFAULT now(), actualizado_en timestamptz NOT NULL DEFAULT now())");
        db.execute("CREATE TABLE saas_actividad(id BIGSERIAL PRIMARY KEY, usuario text NOT NULL, accion text NOT NULL, entidad text NOT NULL, entidad_id text, descripcion text NOT NULL, creado_en timestamptz NOT NULL DEFAULT now())");
        new TransactionTemplate(context.getBean(JpaTransactionManager.class)).executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            Tienda a = new Tienda(); a.setNombre("Boutique A"); a.setSubdominio("boutique-a"); em.persist(a); tienda1 = a.getId();
            Tienda b = new Tienda(); b.setNombre("Boutique B"); b.setSubdominio("boutique-b"); em.persist(b); tienda2 = b.getId();
            superadmin = user(em, "SUPER_ADMIN", null, "super");
            admin = user(em, "ADMIN", tienda1, "admin");
            cliente = user(em, "CLIENTE", tienda2, "cliente");
            empleado = user(em, "EMPLEADO", tienda1, "empleado");
            superConTenant = context.getBean(JwtService.class).generateToken(1, "super@example.test", "SUPER_ADMIN", tienda1, "solo-fixture");
        });
    }
    static String user(EntityManager em, String roleName, Long tienda, String alias) {
        Rol rol = new Rol(); rol.setNombreRol(roleName); em.persist(rol);
        Usuario u = new Usuario(); u.setNombre("Fixture"); u.setApellido("Comercial"); u.setCorreo(alias + "@example.test");
        u.setPasswordHash("solo-fixture-" + alias); u.setRol(rol); u.setTiendaId(tienda); em.persist(u);
        return context.getBean(JwtService.class).generateToken(u.getIdUsuario(), u.getCorreo(), roleName, tienda, u.getMaterialSesion());
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @BeforeEach void seed() {
        PostgresTestSafety.verifiedUrl(postgres);
        db.execute("ALTER TABLE saas_actividad DROP CONSTRAINT IF EXISTS rechazo_fixture");
        db.update("DELETE FROM suscripciones_saas"); db.update("DELETE FROM saas_actividad"); db.update("DELETE FROM saas_configuracion"); db.update("DELETE FROM planes_saas");
        for (int id = 1; id <= 3; id++) db.update("INSERT INTO planes_saas(id,codigo,nombre,precio_mensual,moneda,duracion_meses,activo) VALUES (?,?,?,?,?,1,?)", id, "PLAN_" + id, "Plan " + id, id == 1 ? 79 : 129, "PEN", id != 3);
        db.execute("SELECT setval(pg_get_serial_sequence('planes_saas','id'),3)");
    }
    record Respuesta(int status, String body) {}
    static Respuesta request(String method, String ruta, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ruta)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Respuesta(res.statusCode(), res.body());
    }
    static Map<?,?> object(Respuesta res) { return json.readValue(res.body(), Map.class); }
    static Number number(Object valor) { return (Number) valor; }
    static String selection(int id) { return "{\"planId\":" + id + "}"; }
    static Respuesta select(int id) throws Exception { return request("PUT", "/api/superadmin/suscripciones/plan-comercial", superadmin, selection(id)); }
    static String subscription(Long tienda, int plan, LocalDate inicio, String precio) {
        return "{\"tiendaId\":" + tienda + ",\"planId\":" + plan + ",\"fechaInicio\":\"" + inicio + "\",\"precioContratado\":" + precio + "}";
    }
    static LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Lima")); }
    static long count(String sql) { return db.queryForObject(sql, Long.class); }
    static List<Integer> race(Callable<Respuesta> a, Callable<Respuesta> b) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var fa = pool.submit(() -> { start.await(); return a.call().status(); });
            var fb = pool.submit(() -> { start.await(); return b.call().status(); });
            start.countDown();
            return List.of(fa.get(20, TimeUnit.SECONDS), fb.get(20, TimeUnit.SECONDS));
        }
    }

    @Test void multipleActivePlansRequireExplicitSelection() throws Exception {
        var res = request("GET", "/api/superadmin/suscripciones/plan-comercial", superadmin, null);
        assertEquals(200, res.status()); assertNull(object(res).get("planId"));
        assertEquals("MULTIPLES_PLANES_ACTIVOS", object(res).get("motivo"));
    }
    @Test void principalIsPersistentAndPutIsIdempotent() throws Exception {
        assertEquals(200, select(1).status()); assertEquals(200, select(1).status());
        assertEquals("1", db.queryForObject("SELECT valor FROM saas_configuracion WHERE clave='plan_comercial_principal'", String.class));
        assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE entidad='PLAN_COMERCIAL'"));
        assertEquals("super@example.test", db.queryForObject("SELECT usuario FROM saas_actividad", String.class));
    }
    @Test void selectionSurvivesApplicationRestart() throws Exception {
        assertEquals(200, select(2).status()); context.close(); startContext();
        var res = request("GET", "/api/superadmin/suscripciones/plan-comercial", superadmin, null);
        assertEquals(200, res.status()); assertEquals(2, number(object(res).get("planId")).intValue());
    }
    @Test void validatesPrincipalWithoutChangingContracts() throws Exception {
        assertEquals(400, select(0).status()); assertEquals(404, select(9999).status()); assertEquals(409, select(3).status());
        assertEquals(400, request("PUT", "/api/superadmin/suscripciones/plan-comercial", superadmin, "{}").status());
        assertEquals(0, count("SELECT COUNT(*) FROM saas_configuracion")); assertEquals(0, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void cannotDisablePrincipalAndCanSwitchBeforeDisabling() throws Exception {
        assertEquals(200, select(1).status());
        assertEquals(409, request("PUT", "/api/superadmin/planes/1/desactivar", superadmin, "{}").status());
        assertEquals(200, select(2).status());
        assertEquals(200, request("PUT", "/api/superadmin/planes/1/desactivar", superadmin, "{}").status());
        assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE accion='DESACTIVAR'"));
    }
    @Test void concurrentLastPlanDeactivationsLeaveOneActive() throws Exception {
        var estados = race(() -> request("PUT", "/api/superadmin/planes/1/desactivar", superadmin, "{}"),
                () -> request("PUT", "/api/superadmin/planes/2/desactivar", superadmin, "{}"));
        assertTrue(estados.contains(200)); assertTrue(estados.contains(409));
        assertEquals(1, count("SELECT COUNT(*) FROM planes_saas WHERE activo"));
    }
    @Test void selectionAndDeactivationAreSerialized() throws Exception {
        select(1);
        var estados = race(() -> select(2), () -> request("PUT", "/api/superadmin/planes/2/desactivar", superadmin, "{}"));
        assertTrue(estados.contains(200)); assertTrue(estados.contains(409));
        assertEquals(1, count("SELECT COUNT(*) FROM planes_saas p JOIN saas_configuracion c ON c.valor=p.id::text WHERE c.clave='plan_comercial_principal' AND p.activo"));
    }
    @Test void concurrentIdenticalSelectionHasSingleAudit() throws Exception {
        assertEquals(List.of(200,200), race(() -> select(1), () -> select(1)));
        assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE entidad='PLAN_COMERCIAL'"));
    }
    @Test void selectionRollsBackWhenAuditFails() throws Exception {
        select(1); db.execute("ALTER TABLE saas_actividad ADD CONSTRAINT rechazo_fixture CHECK(accion <> 'SELECCIONAR') NOT VALID");
        var res = select(2); assertEquals(409, res.status()); assertFalse(res.body().contains("INSERT"));
        assertEquals("1", db.queryForObject("SELECT valor FROM saas_configuracion WHERE clave='plan_comercial_principal'", String.class));
    }
    @Test void cannotCreateWithoutPrincipalOrWithDifferentPlan() throws Exception {
        assertEquals(409, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59")).status());
        select(2);
        assertEquals(409, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59")).status());
        assertEquals(0, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void createKeepsContractPriceAndAuditsOnlyCommittedInsert() throws Exception {
        select(1);
        var res = request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59.50"));
        assertEquals(201, res.status()); assertEquals(0, new BigDecimal(object(res).get("precio_contratado").toString()).compareTo(new BigDecimal("59.50")));
        assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE entidad='SUSCRIPCION'"));
        assertEquals("PEN", object(res).get("moneda").toString().trim());
    }
    @Test void defaultPriceAndCurrencyAreSnapshotsFromThePlan() throws Exception {
        db.update("UPDATE planes_saas SET moneda='USD', precio_mensual=25.75 WHERE id=1"); select(1);
        var res = request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"null"));
        assertEquals(201,res.status()); assertEquals("USD", object(res).get("moneda").toString().trim());
        assertEquals(0, new BigDecimal(object(res).get("precio_contratado").toString()).compareTo(new BigDecimal("25.75")));
    }
    @Test void duplicateConcurrentPostsCreateOnlyOneContract() throws Exception {
        select(1); String body = subscription(tienda1,1,hoy(),"59");
        var estados = race(() -> request("POST", "/api/superadmin/suscripciones", superadmin, body), () -> request("POST", "/api/superadmin/suscripciones", superadmin, body));
        assertTrue(estados.contains(201)); assertTrue(estados.contains(409));
        assertEquals(1, count("SELECT COUNT(*) FROM suscripciones_saas")); assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE entidad='SUSCRIPCION'"));
    }
    @Test void createAndPrincipalChangeNeverUseAnUnselectedPlan() throws Exception {
        select(1);
        var estados = race(() -> request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59")), () -> select(2));
        assertEquals(200, estados.get(1)); assertTrue(Set.of(201,409).contains(estados.getFirst()));
        assertEquals(estados.getFirst()==201?1:0, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void subscriptionRollsBackWhenAuditFails() throws Exception {
        select(1); db.execute("ALTER TABLE saas_actividad ADD CONSTRAINT rechazo_fixture CHECK(accion <> 'CREAR') NOT VALID");
        assertEquals(409, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59")).status());
        assertEquals(0, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void validatesDatePriceAndRelations() throws Exception {
        select(1);
        for (String precio : List.of("-1","0.001","10000000000"))
            assertEquals(400, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),precio)).status());
        assertEquals(400, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy().minusDays(1),"59")).status());
        assertEquals(404, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(9999L,1,hoy(),"59")).status());
        assertEquals(400, request("POST", "/api/superadmin/suscripciones", superadmin, "{\"fechaInicio\":\"2026-02-30\"}").status());
        assertEquals(0, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void contiguousFutureContractDoesNotExtendExistingContract() throws Exception {
        select(1);
        var primero = request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59"));
        var segundo = request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy().plusMonths(1),"79"));
        assertEquals(201, primero.status()); assertEquals(201, segundo.status());
        assertEquals("PENDIENTE", object(segundo).get("estado")); assertEquals(2, count("SELECT COUNT(*) FROM suscripciones_saas"));
    }
    @Test void overlappingPersistedExpiredStateIsNotABypass() throws Exception {
        select(1); contrato(tienda1,1,"VENCIDA",hoy(),hoy().plusMonths(1),"59","PEN");
        assertEquals(409, request("POST", "/api/superadmin/suscripciones", superadmin, subscription(tienda1,1,hoy(),"59")).status());
    }
    @Test void updatingPlanDoesNotRepriceHistoryAndIsAudited() throws Exception {
        select(1); contrato(tienda1,1,"ACTIVA",hoy(),hoy().plusMonths(1),"59","PEN");
        String body = "{\"nombre\":\"Plan actualizado\",\"precioMensual\":99,\"duracionMeses\":2,\"limiteProductos\":100,\"limiteUsuarios\":2,\"limiteSucursales\":1,\"beneficios\":[\"Catálogo\"]}";
        assertEquals(200, request("PUT", "/api/superadmin/planes/1", superadmin, body).status());
        assertEquals(0, db.queryForObject("SELECT precio_contratado FROM suscripciones_saas", BigDecimal.class).compareTo(new BigDecimal("59")));
        assertEquals(1, count("SELECT COUNT(*) FROM saas_actividad WHERE entidad='PLAN' AND accion='ACTUALIZAR'"));
    }
    static void contrato(Long tienda,int plan,String estado,LocalDate inicio,LocalDate fin,String precio,String moneda) {
        db.update("INSERT INTO suscripciones_saas(tienda_id,plan_id,estado,fecha_inicio,fecha_fin,precio_contratado,moneda) VALUES (?,?,?,?,?,?,?)",tienda,plan,estado,inicio,fin,new BigDecimal(precio),moneda);
    }
    @Test void reportsSeparateCurrencyAndNeverClaimCashCollected() throws Exception {
        LocalDate d = LocalDate.of(2026,11,13);
        contrato(tienda1,1,"ACTIVA",d.minusDays(1),d.plusDays(5),"59","PEN");
        contrato(tienda2,2,"ACTIVA",d.minusDays(1),d.plusDays(5),"129","PEN");
        contrato(tienda1,1,"ACTIVA",d.minusDays(1),d.plusDays(5),"500","USD");
        contrato(tienda1,1,"CANCELADA",d.minusDays(1),d.plusDays(5),"999","PEN");
        contrato(tienda1,1,"ACTIVA",d.minusDays(9),d,"999","PEN");
        var res = request("GET", "/api/superadmin/reportes/saas/resumen?fechaReferencia=2026-11-13", superadmin,null);
        assertEquals(200,res.status()); assertEquals(2,number(object(res).get("suscripciones_activas")).intValue());
        assertEquals(0,new BigDecimal(object(res).get("importe_contratado").toString()).compareTo(new BigDecimal("188")));
        assertEquals(false,object(res).get("ingresos_cobrados_disponibles")); assertEquals("PEN",object(res).get("moneda"));
        var usd = request("GET", "/api/superadmin/reportes/saas/resumen?fechaReferencia=2026-11-13&moneda=USD",superadmin,null);
        assertEquals(200,usd.status()); assertEquals(0,new BigDecimal(object(usd).get("importe_contratado").toString()).compareTo(new BigDecimal("500")));
    }
    @Test void filtersApplyToAllThreeReportEndpoints() throws Exception {
        LocalDate d=LocalDate.of(2026,11,13);
        contrato(tienda1,1,"ACTIVA",d,d.plusMonths(1),"59","PEN"); contrato(tienda2,2,"ACTIVA",d,d.plusMonths(1),"129","PEN");
        String filtro="?tiendaId="+tienda1+"&planId=1&estado=ACTIVA&fechaReferencia=2026-11-13&desde=2026-11-13&hasta=2026-11-13";
        var resumen=request("GET","/api/superadmin/reportes/saas/resumen"+filtro,superadmin,null);
        assertEquals(200,resumen.status()); assertEquals(1,number(object(resumen).get("total_tiendas")).intValue());
        assertEquals(1,number(object(resumen).get("suscripciones_activas")).intValue());
        var lista=request("GET","/api/superadmin/reportes/saas/suscripciones"+filtro,superadmin,null);
        assertEquals(200,lista.status()); List<?> filas=json.readValue(lista.body(),List.class); assertEquals(1,filas.size());
        assertEquals(tienda1.longValue(),number(((Map<?,?>)filas.getFirst()).get("tienda_id")).longValue());
        var planes=request("GET","/api/superadmin/reportes/saas/planes"+filtro,superadmin,null); assertEquals(200,planes.status());
        assertEquals(1,json.readValue(planes.body(),List.class).size());
    }
    @Test void invalidReportFiltersAreRejected() throws Exception {
        for(String filtro:List.of("tiendaId=-1","estado=INVENTADO","moneda=xxxx","desde=2026-02-30","desde=2026-12-01&hasta=2026-01-01","inventado=1"))
            assertEquals(400,request("GET","/api/superadmin/reportes/saas/resumen?"+filtro,superadmin,null).status());
    }
    @Test void superadminRoutesRejectAnonymousAndTenantTokens() throws Exception {
        for(String ruta:List.of("/api/superadmin/suscripciones","/api/superadmin/suscripciones/plan-comercial","/api/superadmin/planes","/api/superadmin/reportes/saas/resumen","/api/superadmin/reportes/saas/planes","/api/superadmin/reportes/saas/suscripciones")) {
            assertEquals(401,request("GET",ruta,null,null).status());
            for(String token:List.of(admin,cliente,empleado,superConTenant)) assertEquals(401,request("GET",ruta,token,null).status());
        }
        for(String token:List.of(admin,cliente,empleado)) {
            assertEquals(401,request("PUT","/api/superadmin/suscripciones/plan-comercial",token,selection(1)).status());
            assertEquals(401,request("POST","/api/superadmin/suscripciones",token,subscription(tienda1,1,hoy(),"59")).status());
            assertEquals(401,request("PUT","/api/superadmin/planes/1/desactivar",token,"{}").status());
        }
        assertEquals(0,count("SELECT COUNT(*) FROM saas_actividad"));
    }
    @Test void forgedAndRevokedTokensCannotSelectAPrincipal() throws Exception {
        assertEquals(401,request("PUT","/api/superadmin/suscripciones/plan-comercial","invalid.test.token",selection(1)).status());
        db.update("UPDATE usuarios SET version_sesion=? WHERE correo='super@example.test'",UUID.randomUUID().toString());
        try { assertEquals(401,select(1).status()); } finally { db.update("UPDATE usuarios SET version_sesion=NULL WHERE correo='super@example.test'"); }
        assertEquals(0,count("SELECT COUNT(*) FROM saas_configuracion"));
    }
    @Test void planReportUsesHistoricalContractCurrency() throws Exception {
        LocalDate d=LocalDate.of(2026,11,13);
        contrato(tienda1,1,"ACTIVA",d,d.plusMonths(1),"59","PEN");
        db.update("UPDATE planes_saas SET moneda='USD' WHERE id=1");
        var res=request("GET","/api/superadmin/reportes/saas/planes?fechaReferencia=2026-11-13&planId=1",superadmin,null);
        assertEquals(200,res.status());
        Map<?,?> fila=(Map<?,?>)json.readValue(res.body(),List.class).getFirst();
        assertEquals("PEN",fila.get("moneda"));assertEquals(1,number(fila.get("suscripciones_activas")).intValue());
        assertEquals(0,new BigDecimal(fila.get("mrr_contratado").toString()).compareTo(new BigDecimal("59")));
    }

}
