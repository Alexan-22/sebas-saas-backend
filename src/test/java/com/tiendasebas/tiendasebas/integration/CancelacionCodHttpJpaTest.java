package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.config.*;
import com.tiendasebas.tiendasebas.controller.CancelacionCodController;
import com.tiendasebas.tiendasebas.entity.*;
import com.tiendasebas.tiendasebas.security.*;
import com.tiendasebas.tiendasebas.service.CancelacionCodService;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP sobre socket real, filtros reales y repositorios JPA sin sustituciones. */
@Testcontainers
@Timeout(60)
class CancelacionCodHttpJpaTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("cod_http_jpa").withUsername("test")
            .withPassword(UUID.randomUUID().toString());
    static ConfigurableApplicationContext context;
    static JdbcTemplate db;
    static int port;
    Long tenant;
    Integer saleId;
    Integer variantId;
    String admin;
    String client;
    String otherAdmin;
    String host;
    String otherHost;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @EnableMethodSecurity
    @EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
    @Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class,
            TenantResolver.class, CancelacionCodController.class, CancelacionCodService.class,
            com.tiendasebas.tiendasebas.controller.UsuarioController.class,
            com.tiendasebas.tiendasebas.service.UsuarioService.class,
            com.tiendasebas.tiendasebas.controller.SuperAdminPerfilController.class,

            com.tiendasebas.tiendasebas.controller.VentaController.class,
            com.tiendasebas.tiendasebas.service.VentaService.class,
            com.tiendasebas.tiendasebas.transferencias.TransferenciaManualService.class,
            com.tiendasebas.tiendasebas.service.DeliveryService.class,
            com.tiendasebas.tiendasebas.service.ContraEntregaService.class,
            PosErrors.class, com.tiendasebas.tiendasebas.pagos.PaymentAdminErrors.class, com.tiendasebas.tiendasebas.pagos.PaymentAdminController.class})
    static class Application {
        @Bean
        com.tiendasebas.tiendasebas.yape.PagoYapeManualService pagoYapeManualService(
                org.springframework.jdbc.core.JdbcTemplate jdbc) {
            return new com.tiendasebas.tiendasebas.yape.PagoYapeManualService(jdbc);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres),
                    postgres.getUsername(), postgres.getPassword());
        }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.tiendasebas.tiendasebas.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice(
            assignableTypes = com.tiendasebas.tiendasebas.controller.VentaController.class)
    static class PosErrors extends com.tiendasebas.tiendasebas.exception.GlobalExceptionHandler {}

    @BeforeAll
    static void start() {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        String url = PostgresTestSafety.verifiedUrl(postgres);
        var application = new SpringApplication(Application.class);
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("testcontainers-only", Map.of(
                "spring.datasource.url", url,
                "spring.datasource.username", postgres.getUsername(),
                "spring.datasource.password", postgres.getPassword())));
        application.setEnvironment(environment);
        application.addInitializers(ctx -> {
            var properties = ctx.getEnvironment();
            if (!url.equals(properties.getProperty("spring.datasource.url"))
                    || !postgres.getUsername().equals(properties.getProperty("spring.datasource.username"))
                    || !postgres.getPassword().equals(properties.getProperty("spring.datasource.password"))) {
                throw new IllegalStateException("Las propiedades JDBC no corresponden a Testcontainers");
            }
        });
        context = application.run(
                "--spring.config.location=optional:classpath:/cod-http-isolated/",
                "--payments.sandbox.enabled=true", "--spring.profiles.active=cod-http-isolated,payments-sandbox", "--server.port=0",
                "--spring.sql.init.mode=never", "--spring.flyway.enabled=false",
                "--spring.liquibase.enabled=false", "--spring.jpa.open-in-view=false",
                "--app.jwt.secret=" + UUID.randomUUID() + UUID.randomUUID(),
                "--app.jwt.expiration=600000", "--app.tenant.base-domain=example.test",
                "--app.cors.allowed-origins=http://example.test");
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
        db = context.getBean(JdbcTemplate.class);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V4__pagos_simulados_persistentes.sql"))
                .execute(context.getBean(DataSource.class));
        db.execute("CREATE TABLE saas_cobros_contraentrega (tienda_id bigint, id_venta integer, estado text, actualizado_en timestamptz, PRIMARY KEY(tienda_id,id_venta))");
        db.execute("CREATE TABLE saas_envios (tienda_id bigint, id_venta integer, estado text, operador text, modalidad text, actualizado_en timestamptz, PRIMARY KEY(tienda_id,id_venta))");
        db.execute("CREATE TABLE saas_cancelaciones_cod (tienda_id bigint, id_venta integer, motivo text, cancelado_por text, unidades_repuestas integer, UNIQUE(tienda_id,id_venta))");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V2__kardex_cancelaciones_cod.sql"))
                .execute(context.getBean(DataSource.class));
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @BeforeEach
    void seed() {
        var manager = context.getBean(JpaTransactionManager.class);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Tienda shop = new Tienda(); shop.setNombre("Fixture"); shop.setSubdominio("shop-" + suffix);
            em.persist(shop); tenant = shop.getId(); host = shop.getSubdominio() + ".example.test";
            Tienda other = new Tienda(); other.setNombre("Other"); other.setSubdominio("other-" + suffix); em.persist(other);
            otherHost = other.getSubdominio() + ".example.test";
            Rol role = em.createQuery("select r from Rol r where r.nombreRol='ADMIN'", Rol.class)
                    .getResultStream().findFirst().orElseGet(() -> role(em, "ADMIN"));
            Rol customer = em.createQuery("select r from Rol r where r.nombreRol='CLIENTE'", Rol.class)
                    .getResultStream().findFirst().orElseGet(() -> role(em, "CLIENTE"));
            admin = user(em, role, tenant, "admin-" + suffix);
            client = user(em, customer, tenant, "client-" + suffix);
            otherAdmin = user(em, role, other.getId(), "other-" + suffix);
            Genero gender = new Genero(); gender.setNombreGenero(suffix); em.persist(gender);
            Categoria category = new Categoria(); category.setNombreCategoria("Fixture"); category.setTiendaId(tenant); em.persist(category);
            Talla size = new Talla(); size.setNombreTalla(suffix); em.persist(size);
            Producto product = new Producto(); product.setNombre("Fixture"); product.setPrecio(BigDecimal.TEN);
            product.setGenero(gender); product.setCategoria(category); product.setTiendaId(tenant); em.persist(product);
            ProductoTalla variant = new ProductoTalla(); variant.setProducto(product); variant.setTalla(size); variant.setStock(18); em.persist(variant); variantId = variant.getIdProductoTalla();
            Venta sale = new Venta(); sale.setTiendaId(tenant); sale.setTipoVenta(TipoVenta.ONLINE);
            sale.setMedioPago("CONTRA_ENTREGA"); sale.setEstadoPedido(EstadoPedido.RECIBIDO);
            sale.setEstado("REGISTRADA"); sale.setTotal(BigDecimal.valueOf(20)); sale.setFechaVenta(LocalDateTime.now()); em.persist(sale); saleId = sale.getIdVenta();
            DetalleVenta detail = new DetalleVenta(); detail.setVenta(sale); detail.setProductoTalla(variant);
            detail.setCantidad(2); detail.setPrecioUnitario(BigDecimal.TEN); detail.setSubtotal(BigDecimal.valueOf(20)); em.persist(detail);
        });
        db.update("INSERT INTO saas_cobros_contraentrega VALUES (?,?,'PENDIENTE',now())", tenant, saleId);
        db.update("INSERT INTO saas_envios VALUES (?,?,'PENDIENTE','PROPIO','DOMICILIO',now())", tenant, saleId);
    }

    static Rol role(EntityManager em, String name) {
        Rol role = new Rol(); role.setNombreRol(name); em.persist(role); return role;
    }

    String user(EntityManager em, Rol role, Long shop, String name) {
        Usuario user = new Usuario(); user.setNombre("Fixture"); user.setApellido("Fixture");
        user.setCorreo(name + "@example.test"); user.setPasswordHash("unused-test-fixture");
        user.setRol(role); user.setTiendaId(shop); em.persist(user);
        return context.getBean(JwtService.class).generateToken(user.getIdUsuario(), user.getCorreo(), role.getNombreRol(), shop);
    }

    int cancel(String token) throws Exception {
        return request("PATCH", "/api/ventas/admin/contraentrega/" + saleId + "/cancelar", token);
    }

    int request(String method, String path, String token) throws Exception {
        return request(method, path, token, "{\"motivo\":\"Cancelacion de prueba HTTP aislada\"}");
    }

    int request(String method, String path, String token, String json) throws Exception {
        return response(method,path,token,json).status();
    }
    record WireResponse(int status, String wire) {}
    WireResponse response(String method, String path, String token, String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(30000);
            String authorization = token == null ? "" : "Authorization: Bearer " + token + "\r\n";
            String headers = method + " " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n" + authorization
                    + "Content-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(body); socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int status = Integer.parseInt(response.split(" ", 3)[1]);
            // Solo datos ficticios y respuesta HTTP; nunca imprimir Authorization.
            System.out.printf("COD HTTP %s %s host=%s status=%d stock=%d audits=%d%n",
                    method, path, host, status, stock(), audits());
            return new WireResponse(status,response);
        }
    }

    int stock() {
        return db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=?", Integer.class, variantId);
    }
    int audits() {
        return db.queryForObject("SELECT count(*) FROM saas_cancelaciones_cod WHERE tienda_id=? AND id_venta=?", Integer.class, tenant, saleId);
    }
    int kardex() {
        return db.queryForObject("SELECT count(*) FROM inventario_kardex WHERE tienda_id=? AND id_venta=?",
                Integer.class, tenant, saleId);
    }

    @Test void successAndDuplicate() throws Exception {
        assertEquals(200, cancel(admin)); assertEquals(20, stock()); assertEquals(1, audits());
        assertEquals(409, cancel(admin)); assertEquals(20, stock()); assertEquals(1, audits());
        assertEquals(1, kardex());
        assertEquals(2, db.queryForObject("SELECT cantidad FROM inventario_kardex WHERE tienda_id=? AND id_venta=?", Integer.class, tenant, saleId));
        assertEquals(18, db.queryForObject("SELECT stock_anterior FROM inventario_kardex WHERE tienda_id=? AND id_venta=?", Integer.class, tenant, saleId));
        assertEquals(20, db.queryForObject("SELECT stock_posterior FROM inventario_kardex WHERE tienda_id=? AND id_venta=?", Integer.class, tenant, saleId));
    }
    @Test void invalidJwt() throws Exception { assertEquals(401, cancel("invalid")); assertEquals(18, stock()); }
    @Test void paymentAdministrationRequiresAdminAndKeepsConfigurationScoped() throws Exception {
        String route = "/api/pagos/admin/configuracion";
        assertEquals(401, request("GET", route, null));
        assertEquals(401, request("GET", route, "invalid"));
        assertEquals(403, request("GET", route, client));
        assertEquals(401, request("PUT", route, otherAdmin, "{\"habilitado\":true}"));
        assertEquals(200, request("GET", route, admin));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM pagos_proveedor_configuracion WHERE tienda_id=?", Integer.class, tenant));
        assertEquals(200, request("PUT", route, admin, "{\"habilitado\":true}"));
        assertEquals(Boolean.TRUE, db.queryForObject("SELECT habilitado FROM pagos_proveedor_configuracion WHERE tienda_id=?", Boolean.class, tenant));
        Long other = context.getBean(JwtService.class).getClaims(otherAdmin).get("tid", Number.class).longValue();
        assertEquals(0, db.queryForObject("SELECT count(*) FROM pagos_proveedor_configuracion WHERE tienda_id=?", Integer.class, other));
        assertEquals(200, request("GET", "/api/pagos/admin?pagina=0", admin));
        assertEquals(400, request("GET", "/api/pagos/admin?pagina=-1", admin));
        assertEquals(404, request("GET", "/api/pagos/admin/" + UUID.randomUUID(), admin));
    }
    @Test void paymentHttpPagesAndDetailsExcludeForeignTenant() throws Exception {
        var own=pendingPayment();
        long other=context.getBean(JwtService.class).getClaims(otherAdmin).get("tid",Number.class).longValue();
        var manager=context.getBean(JpaTransactionManager.class);
        Integer foreignSale=new TransactionTemplate(manager).execute(status->{
            EntityManager em=EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            Venta v=new Venta(); v.setTiendaId(other); v.setTipoVenta(TipoVenta.ONLINE); v.setMedioPago("SIMULADO");
            v.setEstado("PENDIENTE"); v.setEstadoPedido(EstadoPedido.RECIBIDO); v.setTotal(BigDecimal.valueOf(20)); v.setFechaVenta(LocalDateTime.now()); em.persist(v); em.flush(); return v.getIdVenta();
        });
        db.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor,habilitado) VALUES (?,'SANDBOX',true)",other);
        TenantContext.setCurrentTenant(other);
        var foreign=paymentProvider().crear(new com.tiendasebas.tiendasebas.pagos.PaymentProvider.Solicitud(other,foreignSale,BigDecimal.valueOf(20),"PEN","foreign-http"));
        paymentIdentity();
        var page=response("GET","/api/pagos/admin?pagina=0",admin,"");
        assertEquals(200,page.status()); assertTrue(page.wire().contains(own.id().toString())); assertFalse(page.wire().contains(foreign.id().toString()));
        var second=response("GET","/api/pagos/admin?pagina=1",admin,"");
        assertEquals(200,second.status()); assertFalse(second.wire().contains(own.id().toString())); assertFalse(second.wire().contains(foreign.id().toString()));
        assertEquals(200,request("GET","/api/pagos/admin/"+own.id(),admin));
        assertEquals(404,request("GET","/api/pagos/admin/"+foreign.id(),admin));
        assertEquals(403,request("GET","/api/pagos/admin/"+own.id(),client));
        assertEquals(401,request("GET","/api/pagos/admin/"+own.id(),null));
        String originalHost=host;
        try {
            host=otherHost;
            var b=response("GET","/api/pagos/admin?pagina=0",otherAdmin,"");
            assertEquals(200,b.status()); assertTrue(b.wire().contains(foreign.id().toString())); assertFalse(b.wire().contains(own.id().toString()));
            assertEquals(404,request("GET","/api/pagos/admin/"+own.id(),otherAdmin));
        } finally { host=originalHost; }
        paymentInvariant(own,"PENDIENTE",1);
    }
    @Test void paymentCannotUseForeignSale() {
        var p=pendingPayment();
        long other=context.getBean(JwtService.class).getClaims(otherAdmin).get("tid",Number.class).longValue();
        TenantContext.setCurrentTenant(other);
        var ex=assertThrows(NoSuchElementException.class,()->paymentProvider().crear(new com.tiendasebas.tiendasebas.pagos.PaymentProvider.Solicitud(other,saleId,BigDecimal.valueOf(20),"PEN","foreign-sale")));
        assertEquals("Pedido no disponible",ex.getMessage());
        paymentIdentity(); paymentInvariant(p,"PENDIENTE",1);
    }
    @Test void paymentWithoutTenantCannotTransition() {
        var p=pendingPayment(); TenantContext.clear();
        var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->transition(p,"APROBADO"));
        assertEquals(401,ex.getStatusCode().value()); paymentIdentity(); paymentInvariant(p,"PENDIENTE",1);
    }
    private void paymentIdentity() {
        TenantContext.setCurrentTenant(tenant);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test-admin", null,
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    private com.tiendasebas.tiendasebas.pagos.PersistentSandboxPaymentProvider paymentProvider() {
        return new com.tiendasebas.tiendasebas.pagos.PersistentSandboxPaymentProvider(db, context.getBean(JpaTransactionManager.class),
            new com.tiendasebas.tiendasebas.pagos.SandboxPolicy(context.getEnvironment()));
    }
    private com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago pendingPayment() {
        paymentIdentity();
        db.update("UPDATE ventas SET medio_pago='SIMULADO',estado='PENDIENTE' WHERE tienda_id=? AND id_venta=?", tenant,saleId);
        db.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor,habilitado) VALUES (?,'SANDBOX',true)",tenant);
        return paymentProvider().crear(new com.tiendasebas.tiendasebas.pagos.PaymentProvider.Solicitud(tenant,saleId,BigDecimal.valueOf(20),"PEN","transition-test"));
    }
    private com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago transition(com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago p, String state) {
        return paymentProvider().transicionar(tenant,p.id(),com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.valueOf(state),p.monto(),p.moneda());
    }
    private void paymentInvariant(com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago p, String state, int events) {
        assertEquals(state,paymentProvider().consultar(tenant,p.id()).estado().name());
        assertEquals(events,db.queryForObject("SELECT count(*) FROM pagos_simulados_eventos WHERE pago_id=?",Integer.class,p.id()));
        assertEquals(state.equals("APROBADO") ? "PAGADA" : "PENDIENTE",db.queryForObject("SELECT estado FROM ventas WHERE tienda_id=? AND id_venta=?",String.class,tenant,saleId));
        assertEquals(18,stock()); assertEquals(0,kardex());
    }
    @org.junit.jupiter.api.AfterEach void clearPaymentIdentity() { TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Test void paymentPendingToApproved() { var p=pendingPayment(); transition(p,"APROBADO"); paymentInvariant(p,"APROBADO",2); }
    @Test void paymentPendingToRejected() { var p=pendingPayment(); transition(p,"RECHAZADO"); paymentInvariant(p,"RECHAZADO",2); }
    @Test void paymentPendingToExpired() { var p=pendingPayment(); transition(p,"EXPIRADO"); paymentInvariant(p,"EXPIRADO",2); }
    @Test void paymentRepeatedApprovalIsIdempotent() { var p=pendingPayment(); assertEquals(transition(p,"APROBADO"),transition(p,"APROBADO")); paymentInvariant(p,"APROBADO",2); }
    @Test void paymentTerminalTransitionRejected() { var p=pendingPayment(); transition(p,"RECHAZADO"); var ex=assertThrows(IllegalStateException.class,()->transition(p,"APROBADO")); assertEquals("Transicion no permitida",ex.getMessage()); paymentInvariant(p,"RECHAZADO",2); }
    @Test void paymentWrongAmountRejected() { var p=pendingPayment(); assertThrows(IllegalArgumentException.class,()->paymentProvider().transicionar(tenant,p.id(),com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO,BigDecimal.ONE,"PEN")); paymentInvariant(p,"PENDIENTE",1); }
    @Test void paymentWrongCurrencyRejected() { var p=pendingPayment(); assertThrows(IllegalArgumentException.class,()->paymentProvider().transicionar(tenant,p.id(),com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO,p.monto(),"USD")); paymentInvariant(p,"PENDIENTE",1); }
    @Test void paymentDisabledConfigurationRejected() {
        var p=pendingPayment(); db.update("UPDATE pagos_proveedor_configuracion SET habilitado=false WHERE tienda_id=?",tenant);
        assertThrows(IllegalStateException.class,()->transition(p,"APROBADO")); paymentInvariant(p,"PENDIENTE",1);
    }
    @Test void paymentAuditFailureRollsBackAndCanRetry() {
        var p=pendingPayment();
        db.execute("CREATE FUNCTION payment_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.estado='APROBADO' THEN IF NOT EXISTS (SELECT 1 FROM pagos_simulados p JOIN ventas v ON v.tienda_id=p.tienda_id AND v.id_venta=p.id_venta WHERE p.id=NEW.pago_id AND p.estado='APROBADO' AND v.estado='PAGADA') THEN RAISE EXCEPTION 'updates not observed'; END IF; RAISE EXCEPTION 'deliberate audit failure'; END IF; RETURN NEW; END $$");
        db.execute("CREATE TRIGGER payment_fail_audit BEFORE INSERT ON pagos_simulados_eventos FOR EACH ROW EXECUTE FUNCTION payment_fail_audit()");
        try {
            var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->transition(p,"APROBADO"));
            assertTrue(failure.getMostSpecificCause().getMessage().contains("deliberate audit failure"));
            paymentInvariant(p,"PENDIENTE",1);
        } finally {
            db.execute("DROP TRIGGER payment_fail_audit ON pagos_simulados_eventos");
            db.execute("DROP FUNCTION payment_fail_audit()");
        }
        transition(p,"APROBADO"); paymentInvariant(p,"APROBADO",2);
    }
    @Test void paymentApproveRejectRaceIsConsistent() throws Exception {
        var p=pendingPayment(); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        var connections=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.function.Function<String,Callable<String>> action=state->()->{
                paymentIdentity();
                try { return new TransactionTemplate(context.getBean(JpaTransactionManager.class)).execute(tx -> {
                    connections.add(db.queryForObject("SELECT pg_backend_pid()",Integer.class)); ready.countDown();
                    try { assertTrue(go.await(10,TimeUnit.SECONDS)); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Race interrupted",ex); }
                    transition(p,state); return state;
                }); }
                catch(IllegalStateException ex) { assertEquals("Transicion no permitida",ex.getMessage()); return "CONFLICT"; }
                finally { TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            };
            var a=pool.submit(action.apply("APROBADO")); var b=pool.submit(action.apply("RECHAZADO"));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            var outcomes=java.util.List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(2,connections.size());
            assertEquals(1,outcomes.stream().filter("CONFLICT"::equals).count());
            paymentInvariant(p,outcomes.stream().filter(x->!x.equals("CONFLICT")).findFirst().orElseThrow(),2);
        }
    }
    @Test void persistentPaymentSurvivesAdapterRecreation() throws Exception {
        db.update("UPDATE ventas SET medio_pago='SIMULADO', estado='PENDIENTE' WHERE id_venta=?", saleId);
        db.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor,habilitado) VALUES (?,'SANDBOX',true)", tenant);
        var manager = context.getBean(JpaTransactionManager.class);
        try {
            TenantContext.setCurrentTenant(tenant);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test-admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
            var provider = new com.tiendasebas.tiendasebas.pagos.PersistentSandboxPaymentProvider(db, manager, new com.tiendasebas.tiendasebas.pagos.SandboxPolicy(context.getEnvironment()));
            var input = new com.tiendasebas.tiendasebas.pagos.PaymentProvider.Solicitud(
                    tenant, saleId, BigDecimal.valueOf(20), "PEN", "payment-fixture");
            var first = provider.crear(input);
            assertEquals(first, provider.crear(input));
            var recreated = new com.tiendasebas.tiendasebas.pagos.PersistentSandboxPaymentProvider(db, manager, new com.tiendasebas.tiendasebas.pagos.SandboxPolicy(context.getEnvironment()));
            assertEquals(first, recreated.consultar(tenant, first.id()));
            assertThrows(IllegalArgumentException.class, () -> recreated.transicionar(tenant, first.id(),
                    com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO, BigDecimal.ONE, "PEN"));
            assertThrows(IllegalArgumentException.class, () -> recreated.transicionar(tenant, first.id(),
                    com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO, first.monto(), "USD"));
            com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago approved;
            var gate = new CountDownLatch(2);
            var connectionIds = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<com.tiendasebas.tiendasebas.pagos.PaymentProvider.Pago> confirm = () -> {
                    TenantContext.setCurrentTenant(tenant);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test-admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
                    try {
                        return new TransactionTemplate(manager).execute(tx -> {
                            connectionIds.add(db.queryForObject("SELECT pg_backend_pid()",Integer.class)); gate.countDown();
                            try { assertTrue(gate.await(10,TimeUnit.SECONDS)); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                            return recreated.transicionar(tenant, first.id(),
                                com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO, first.monto(), "PEN");
                        });
                    } finally { TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                };
                var one = pool.submit(confirm); var two = pool.submit(confirm);
                approved = one.get(30, TimeUnit.SECONDS);
                assertEquals(approved, two.get(30, TimeUnit.SECONDS));
                assertEquals(2, connectionIds.size());
            }
            assertEquals(approved, recreated.transicionar(tenant, first.id(),
                    com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.APROBADO, first.monto(), "PEN"));
            assertEquals(approved, recreated.crear(input));
            assertEquals("PAGADA", db.queryForObject("SELECT estado FROM ventas WHERE id_venta=?", String.class, saleId));
            assertEquals(2, db.queryForObject("SELECT count(*) FROM pagos_simulados_eventos WHERE pago_id=?", Integer.class, first.id()));
            assertThrows(IllegalStateException.class, () -> recreated.transicionar(tenant, first.id(),
                    com.tiendasebas.tiendasebas.pagos.PaymentProvider.Estado.RECHAZADO, first.monto(), "PEN"));
            TenantContext.setCurrentTenant(context.getBean(JwtService.class).getClaims(otherAdmin).get("tid", Number.class).longValue());
            assertThrows(NoSuchElementException.class, () -> recreated.consultar(tenant, first.id()));
            assertEquals(18, stock());
        } finally {
            TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
    @Test void posYClientesPresencialesNoEstanDisponibles() throws Exception {
        int ventasAntes = db.queryForObject(
                "SELECT count(*) FROM ventas", Integer.class);

        assertEquals(404, request("POST",
                "/api/ventas/admin/pos", admin, "{}"));

        assertEquals(404, request("GET",
                "/api/clientes-presenciales/admin", admin));

        assertEquals(404, request("POST",
                "/api/clientes-presenciales/admin", admin, "{}"));

        assertEquals(ventasAntes, db.queryForObject(
                "SELECT count(*) FROM ventas", Integer.class));

        assertEquals(18, stock());
    }
    @Test void globalSuperAdminHasOnlyGlobalAccess() throws Exception {
        var manager = context.getBean(JpaTransactionManager.class);
        String globalToken = new TransactionTemplate(manager).execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            Rol role = em.createQuery("select r from Rol r where r.nombreRol='SUPER_ADMIN'", Rol.class)
                    .getResultStream().findFirst().orElseGet(() -> role(em, "SUPER_ADMIN"));
            return user(em, role, null, "global-" + UUID.randomUUID());
        });
        assertEquals(200, request("GET", "/api/superadmin/me", globalToken));
        assertEquals(401, request("GET", "/api/superadmin/me", admin));
        assertEquals(401, request("GET", "/api/usuarios/admin", globalToken));
        assertEquals(401, cancel(globalToken));
        assertEquals(401, request("GET", "/api/pagos/admin", globalToken));
        db.update("UPDATE tiendas SET estado='SUSPENDIDA' WHERE id=?", tenant);
        assertEquals(200, request("GET", "/api/superadmin/me", globalToken));
        assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void userHttpAccessIsScopedToAuthenticatedShop() throws Exception {
        var jwt = context.getBean(JwtService.class);
        int ownId = jwt.getClaims(admin).get("uid", Number.class).intValue();
        int foreignId = jwt.getClaims(otherAdmin).get("uid", Number.class).intValue();
        assertEquals(200, request("GET", "/api/usuarios/admin/" + ownId, admin));
        assertEquals(404, request("GET", "/api/usuarios/admin/" + foreignId, admin));
        assertEquals(404, request("PUT", "/api/usuarios/admin/" + foreignId + "/estado?estado=false", admin));
        assertEquals(Boolean.TRUE, db.queryForObject("SELECT estado FROM usuarios WHERE id_usuario=?", Boolean.class, foreignId));
        assertEquals(403, request("GET", "/api/usuarios/admin", client));
        assertEquals(18, stock()); assertEquals(0, audits());
    }

    @Test void userServiceRequiresTenantAndFiltersList() {
        var service = context.getBean(com.tiendasebas.tiendasebas.service.UsuarioService.class);
        TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, service::listarTodos);
        try {
            TenantContext.setCurrentTenant(tenant);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test-admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertEquals(2, service.listarTodos().size());
        } finally {
            TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
    @Test void saleWithoutTenantCannotBePersisted() {
        var manager = context.getBean(JpaTransactionManager.class);
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            Venta sale = new Venta();
            sale.setFechaVenta(LocalDateTime.now()); sale.setTotal(BigDecimal.TEN);
            sale.setEstado("REGISTRADA"); sale.setMedioPago("EFECTIVO");
            em.persist(sale); em.flush();
        }));
        assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void missingJwt() throws Exception {
        assertEquals(401, cancel(null)); assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void directErrorRequestRequiresAuthentication() throws Exception {
        assertEquals(401, request("GET", "/error", null));
        assertEquals(401, request("GET", "/error", "invalid"));
        assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void errorDispatchDoesNotAuthorizeNextRequest() throws Exception {
        assertEquals(401, cancel("invalid"));
        assertEquals(403, cancel(client));
        assertEquals(401, cancel(otherAdmin));
        assertEquals(18, stock()); assertEquals(0, audits());
        assertEquals(200, cancel(admin)); assertEquals(20, stock()); assertEquals(1, audits());
    }
    @Test void clientForbidden() throws Exception { assertEquals(403, cancel(client)); assertEquals(18, stock()); }
    @Test void foreignJwt() throws Exception { assertEquals(401, cancel(otherAdmin)); assertEquals(18, stock()); }
    @Test void suspendedShop() throws Exception {
        db.update("UPDATE tiendas SET estado='SUSPENDIDA' WHERE id=?", tenant);
        assertEquals(404, cancel(admin)); assertEquals(18, stock());
    }
    @Test void paidOrder() throws Exception {
        db.update("UPDATE saas_cobros_contraentrega SET estado='COBRADO' WHERE id_venta=?", saleId);
        assertEquals(409, cancel(admin)); assertEquals(18, stock());
    }
    @Test void dispatchedOrder() throws Exception {
        db.update("UPDATE saas_envios SET estado='DESPACHADO' WHERE id_venta=?", saleId);
        assertEquals(409, cancel(admin)); assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void ordinaryPayment() throws Exception {
        db.update("UPDATE ventas SET medio_pago='TARJETA' WHERE id_venta=?", saleId);
        assertEquals(409, cancel(admin)); assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void authenticatedOtherShopCannotSeeOrder() throws Exception {
        host = otherHost;
        assertEquals(404, cancel(otherAdmin)); assertEquals(18, stock()); assertEquals(0, audits());
    }
    @Test void rollback() throws Exception {
        db.update("UPDATE producto_tallas SET stock=? WHERE id_producto_talla=?", Integer.MAX_VALUE, variantId);
        assertEquals(409, cancel(admin)); assertEquals(Integer.MAX_VALUE, stock()); assertEquals(0, audits());
        assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM saas_cobros_contraentrega WHERE id_venta=?", String.class, saleId));
    }
    @Test void concurrentRequests() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> { start.await(); return cancel(admin); };
            var first = pool.submit(request); var second = pool.submit(request); start.countDown();
            var results = new ArrayList<>(List.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS)));
            Collections.sort(results); assertEquals(List.of(200, 409), results);
        }
        assertEquals(20, stock()); assertEquals(1, audits());
        assertEquals(1, kardex());
    }

    @Test void rollbackAfterDatabaseStockUpdate() throws Exception {
        // La secuencia deja evidencia incluso si PostgreSQL revierte la transaccion.
        // El trigger AFTER demuestra que se ejecuto el UPDATE real de Hibernate.
        db.execute("CREATE SEQUENCE cod_stock_update_probe");
        db.execute("""
                CREATE FUNCTION cod_fail_after_stock() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  PERFORM nextval('cod_stock_update_probe');
                  RAISE EXCEPTION 'Fallo deliberado posterior a UPDATE stock';
                  RETURN NEW;
                END;
                $$
                """);
        db.execute("CREATE TRIGGER cod_fail_after_stock AFTER UPDATE OF stock ON producto_tallas "
                + "FOR EACH ROW WHEN (NEW.id_producto_talla = " + variantId + ") "
                + "EXECUTE FUNCTION cod_fail_after_stock()");
        try {
            assertEquals(500, cancel(admin));
            assertEquals(Boolean.TRUE, db.queryForObject("SELECT is_called FROM cod_stock_update_probe", Boolean.class));
            assertEquals(18, stock()); assertEquals(0, audits());
            assertEquals(0, kardex());
            assertEquals("RECIBIDO", db.queryForObject("SELECT estado_pedido FROM ventas WHERE id_venta=?", String.class, saleId));
            assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM saas_cobros_contraentrega WHERE id_venta=?", String.class, saleId));
            assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM saas_envios WHERE id_venta=?", String.class, saleId));
        } finally {
            db.execute("DROP TRIGGER cod_fail_after_stock ON producto_tallas");
            db.execute("DROP FUNCTION cod_fail_after_stock()");
            db.execute("DROP SEQUENCE cod_stock_update_probe");
        }
        assertEquals(200, cancel(admin)); assertEquals(20, stock()); assertEquals(1, audits());
    }

    @Test void kardexFailureRollsBackAlreadyFlushedStock() throws Exception {
        db.execute("CREATE SEQUENCE cod_kardex_probe");
        db.execute("""
                CREATE FUNCTION cod_fail_kardex() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF (SELECT stock FROM producto_tallas WHERE id_producto_talla=NEW.id_producto_talla) = NEW.stock_posterior THEN
                    PERFORM nextval('cod_kardex_probe');
                  END IF;
                  RAISE EXCEPTION 'Fallo deliberado en kardex';
                  RETURN NEW;
                END;
                $$
                """);
        db.execute("CREATE TRIGGER cod_fail_kardex BEFORE INSERT ON inventario_kardex FOR EACH ROW EXECUTE FUNCTION cod_fail_kardex()");
        try {
            assertEquals(500, cancel(admin));
            assertEquals(Boolean.TRUE, db.queryForObject("SELECT is_called FROM cod_kardex_probe", Boolean.class));
            assertEquals(18, stock()); assertEquals(0, audits()); assertEquals(0, kardex());
            assertEquals("RECIBIDO", db.queryForObject("SELECT estado_pedido FROM ventas WHERE id_venta=?", String.class, saleId));
            assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM saas_cobros_contraentrega WHERE id_venta=?", String.class, saleId));
            assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM saas_envios WHERE id_venta=?", String.class, saleId));
        } finally {
            db.execute("DROP TRIGGER cod_fail_kardex ON inventario_kardex");
            db.execute("DROP FUNCTION cod_fail_kardex()");
            db.execute("DROP SEQUENCE cod_kardex_probe");
        }
        assertEquals(200, cancel(admin)); assertEquals(20, stock()); assertEquals(1, kardex());
    }
}
