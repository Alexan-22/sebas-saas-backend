package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.entity.*;
import com.tiendasebas.tiendasebas.security.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP real + PostgreSQL privado. No lee perfiles, variables DB ni Supabase. */
class AuthTenantPostgresTest {
 static final ComercialPostgresFixture pg=new ComercialPostgresFixture();
 static ConfigurableApplicationContext ctx;static JdbcTemplate db;static int port;static Long a,b;static Integer adminId,clientId,employeeId,globalId;static String admin,client,employee,global;
 static final String pass="Ficticia-Segura-2026";
 static final JsonMapper json=JsonMapper.builder().build();
 static volatile boolean deliveryEnabled=true;
 static final Map<String,String> mailbox=new ConcurrentHashMap<>();
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @EnableTransactionManagement
 @EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
 @ComponentScan(basePackages="com.tiendasebas.tiendasebas",excludeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(integration|Test|TiendaSebasApplication).*"))
 static class Application {
  @Bean(destroyMethod="close") DataSource dataSource(){
   var ds=new com.zaxxer.hikari.HikariDataSource();ds.setJdbcUrl(pg.verifiedUrl());ds.setUsername(pg.getUsername());ds.setPassword(pg.getPassword());ds.setMaximumPoolSize(1);ds.setMinimumIdle(0);ds.setConnectionTimeout(5000);return ds;
  }
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
  @Bean JpaTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
  @Bean @Primary RecoveryDelivery testMailbox(){return new RecoveryDelivery(){public boolean disponible(){return deliveryEnabled;}public void enviar(String email,String token,Long tenant){mailbox.put(tenant+":"+email,token);}};}
 }
 @BeforeAll static void start()throws Exception{
  pg.start();PostgresTestSafety.rejectExternalConfiguration(System.getenv());
  var env=new StandardEnvironment();env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
  env.getPropertySources().addFirst(new MapPropertySource("solo-fixture",Map.of("spring.datasource.url",pg.verifiedUrl(),"spring.datasource.username",pg.getUsername(),"spring.datasource.password",pg.getPassword(),"app.jwt.secret",UUID.randomUUID().toString()+UUID.randomUUID())));
  var app=new SpringApplication(Application.class);app.setEnvironment(env);
  ctx=app.run("--spring.config.location=optional:classpath:/auth-sin-config/","--spring.profiles.active=auth-test","--server.address=127.0.0.1","--server.port=0","--spring.flyway.enabled=false","--spring.liquibase.enabled=false","--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.jwt.expiration=28800000","--app.auth.idle-seconds=1800","--app.auth.absolute-seconds=28800","--app.cors.allowed-origins=http://fixture.example.test","--app.tenant.base-domain=saas.test","--app.tenant.local-subdomain=boutique-a","--app.storage.provider=local","--app.upload.dir=target/auth-fixture-uploads","--app.supabase.url=","--app.supabase.storage-key=","--app.supabase.service-role-key=","--logging.level.root=WARN");
  port=((WebServerApplicationContext)ctx).getWebServer().getPort();db=new JdbcTemplate(ctx.getBean(DataSource.class));
  pg.verifiedUrl();
  db.execute("CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN");
  db.execute("DROP TABLE auth_sesiones,auth_recuperaciones,auth_limites");
  try(var stream=AuthTenantPostgresTest.class.getResourceAsStream("/db/manual/V20__sesiones_y_recuperacion_segura.sql")){db.execute(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF",""));}
  db.execute("CREATE TABLE yape_pagos_manuales(tienda_id bigint,id_venta integer,usuario_id integer,estado text,importe numeric,moneda text,numero_yape text,titular_yape text,correo_cliente text,creado_en timestamptz DEFAULT now(),verificado_por text,verificado_en timestamptz,motivo_rechazo text,PRIMARY KEY(tienda_id,id_venta))");
  db.execute("CREATE TABLE transferencia_pagos_manuales(tienda_id bigint,id_venta integer,usuario_id integer,estado text,importe numeric,moneda text,banco text,titular text,numero_cuenta text,cci text,correo_cliente text,creado_en timestamptz DEFAULT now(),verificado_por text,verificado_en timestamptz,motivo_rechazo text,PRIMARY KEY(tienda_id,id_venta))");
  db.execute("CREATE TABLE saas_cobros_contraentrega(tienda_id bigint,id_venta integer,monto numeric,estado text,metodo_cobro text,referencia_cobro text,cobrado_en timestamptz,creado_en timestamptz DEFAULT now(),PRIMARY KEY(tienda_id,id_venta))");
  db.execute("CREATE TABLE saas_envios(tienda_id bigint,id_venta integer,estado text,PRIMARY KEY(tienda_id,id_venta))");
  try(var stream=AuthTenantPostgresTest.class.getResourceAsStream("/db/manual/V21__correo_unico_por_ambito.sql")){db.execute(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF",""));}
  db.execute("CREATE TABLE yape_checkout_reintentos(tienda_id bigint,usuario_id integer,clave uuid,huella text,id_venta integer)");
  db.execute("CREATE TABLE yape_configuracion_tienda(tienda_id bigint PRIMARY KEY,habilitado boolean,numero_yape text,titular_yape text)");
  db.execute("CREATE TABLE yape_qr_imagen(tienda_id bigint PRIMARY KEY,imagen bytea,sha256 text)");
  db.execute("ALTER TABLE usuarios DROP COLUMN revision");
  try(var stream=AuthTenantPostgresTest.class.getResourceAsStream("/db/manual/V22__revision_concurrencia_usuarios.sql")){db.execute(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF",""));}
  transaction(em->{Tienda t=new Tienda();t.setNombre("Ficticia A");t.setSubdominio("boutique-a");em.persist(t);a=t.getId();Tienda t2=new Tienda();t2.setNombre("Ficticia B");t2.setSubdominio("boutique-b");em.persist(t2);b=t2.getId();for(String role:List.of("ADMIN","CLIENTE","EMPLEADO","SUPER_ADMIN")){Rol r=new Rol();r.setNombreRol(role);em.persist(r);}});
 }
 @AfterAll static void stop()throws Exception{try{if(ctx!=null)ctx.close();}finally{pg.close();}}
 static void transaction(java.util.function.Consumer<EntityManager> work){new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)).executeWithoutResult(s->work.accept(EntityManagerFactoryUtils.getTransactionalEntityManager(ctx.getBean(EntityManagerFactory.class))));}
 @BeforeEach void seed(org.junit.jupiter.api.TestInfo info){
  System.out.println("AUTH AISLADO: "+info.getDisplayName());
  pg.verifiedUrl();deliveryEnabled=true;mailbox.clear();db.update("DELETE FROM auth_sesiones");db.update("DELETE FROM auth_recuperaciones");db.update("DELETE FROM auth_limites");db.execute("TRUNCATE TABLE yape_checkout_reintentos,yape_configuracion_tienda,yape_qr_imagen,usuarios,productos,categorias,generos,yape_pagos_manuales,transferencia_pagos_manuales,saas_cobros_contraentrega,saas_envios CASCADE");
  transaction(em->{adminId=user(em,"ADMIN",a,"admin@example.test");user(em,"ADMIN",b,"admin@example.test");clientId=user(em,"CLIENTE",a,"client@example.test");user(em,"CLIENTE",b,"client@example.test");employeeId=user(em,"EMPLEADO",a,"employee@example.test");globalId=user(em,"SUPER_ADMIN",null,"global@example.test");});
  admin=login("boutique-a","admin@example.test",false);client=login("boutique-a","client@example.test",false);employee=login("boutique-a","employee@example.test",false);global=login("boutique-a","global@example.test",true);
 }
 static Integer user(EntityManager em,String role,Long tenant,String email){Usuario u=new Usuario();u.setNombre("Ficticio");u.setApellido("Seguridad");u.setCorreo(email);u.setPasswordHash(ctx.getBean(PasswordEncoder.class).encode(pass));u.setRol(em.createQuery("select r from Rol r where r.nombreRol=:r",Rol.class).setParameter("r",role).getSingleResult());u.setTiendaId(tenant);u.setEstado(true);em.persist(u);return u.getIdUsuario();}
 record Response(int status,String body){}
 static Response request(String method,String host,String path,String token,Object body){try{
  var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20)).header("Host",host+".saas.test");if(token!=null)builder.header("Authorization","Bearer "+token);
  if(body!=null)builder.header("Content-Type","application/json");builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var res=HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());return new Response(res.statusCode(),res.body());
 }catch(Exception ex){throw new IllegalStateException("Fallo HTTP de fixture",ex);}}
 static String login(String host,String email,boolean global){var r=request("POST",host,global?"/api/auth/superadmin/login":"/api/auth/login",null,Map.of("correo",email,"password",pass));assertEquals(200,r.status(),r.body());return json.readTree(r.body()).get("token").asText();}
 static String sid(String token){return ctx.getBean(JwtService.class).getClaims(token).getId();}
 @Test void rolesLoginScopeYRegistro(){
  for(String t:List.of(admin,client,employee,global))assertNotNull(sid(t));
  assertNotEquals(ctx.getBean(JwtService.class).getClaims(admin).get("tid"),ctx.getBean(JwtService.class).getClaims(login("boutique-b","admin@example.test",false)).get("tid"));
  var body=Map.of("nombre","Nuevo","apellido","Ficticio","correo","new@example.test","password",pass,"rol","SUPER_ADMIN","tienda_id",b);
  var first=request("POST","boutique-a","/api/auth/registro",null,body);assertEquals(200,first.status());assertEquals(first.body(),request("POST","boutique-a","/api/auth/registro",null,body).body());
  assertEquals("CLIENTE",db.queryForObject("SELECT r.nombre_rol FROM usuarios u JOIN roles r ON u.id_rol=r.id_rol WHERE u.correo='new@example.test' AND u.tienda_id=?",String.class,a));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM usuarios WHERE correo='new@example.test' AND tienda_id=?",Integer.class,b));
 }
 @Test void tokensCruzadosRolesNoAutorizados(){
  assertEquals(401,request("GET","boutique-b","/api/usuarios/admin",admin,null).status());
  assertEquals(403,request("GET","boutique-a","/api/usuarios/admin",client,null).status());
  assertEquals(403,request("GET","boutique-a","/api/usuarios/admin",employee,null).status());
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",global,null).status());
  assertEquals(401,request("GET","boutique-a","/api/superadmin/perfil",admin,null).status());
  assertEquals(200,request("GET","boutique-a","/api/usuarios/admin?tienda_id="+b,admin,null).status());
  assertFalse(request("GET","boutique-a","/api/usuarios/admin",admin,null).body().contains("passwordHash"));
 }
 @Test void inactividadNoReviveYSesionAbsolutaNoSeRenueva(){
  db.update("UPDATE auth_sesiones SET creada_en=clock_timestamp()-interval '2 hours',ultimo_uso=clock_timestamp()-interval '31 minutes' WHERE id=?",UUID.fromString(sid(admin)));
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",admin,null).status());
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",admin,null).status());
  String fresh=login("boutique-a","admin@example.test",false);db.update("UPDATE auth_sesiones SET creada_en=clock_timestamp()-interval '9 hours',vence_en=clock_timestamp()-interval '1 second' WHERE id=?",UUID.fromString(sid(fresh)));
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",fresh,null).status());
  assertEquals(200,request("GET","boutique-a","/api/usuarios/admin",login("boutique-a","admin@example.test",false),null).status());
 }
 @Test void logoutRevocaSesionActualIncluidoEmpleadoYGlobal(){
  for(var pair:List.of(new String[]{admin,"/api/auth/logout"},new String[]{employee,"/api/auth/logout"},new String[]{global,"/api/superadmin/auth/logout"})){
   assertEquals(204,request("POST","boutique-a",pair[1],pair[0],Map.of()).status());
   assertTrue(db.queryForObject("SELECT revocada FROM auth_sesiones WHERE id=?",Boolean.class,UUID.fromString(sid(pair[0]))));
  }
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",admin,null).status());
 }
 @Test void cambioPasswordBCryptRevocaTodosLosTokens(){
  String tokenB=login("boutique-b","admin@example.test",false);
  String another=login("boutique-a","admin@example.test",false),next="Nueva-Ficticia-2027";
  assertEquals(204,request("POST","boutique-a","/api/auth/password/change",admin,Map.of("passwordActual",pass,"passwordNueva",next)).status());
  for(String token:List.of(admin,another))assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",token,null).status());
  assertEquals(200,request("GET","boutique-b","/api/usuarios/admin",tokenB,null).status());
  String hash=db.queryForObject("SELECT password_hash FROM usuarios WHERE id_usuario=?",String.class,adminId);assertTrue(ctx.getBean(PasswordEncoder.class).matches(next,hash));assertNotEquals(next,hash);
  assertEquals(401,request("POST","boutique-a","/api/auth/login",null,Map.of("correo","admin@example.test","password",pass)).status());
  assertEquals(200,request("POST","boutique-a","/api/auth/login",null,Map.of("correo","admin@example.test","password",next)).status());
 }
 @Test void recoveryNoEnumeraNiPublicaTokenYNiCruzaTenant(){
  var known=request("POST","boutique-a","/api/auth/password/forgot",null,Map.of("correo","client@example.test"));var unknown=request("POST","boutique-a","/api/auth/password/forgot",null,Map.of("correo","missing@example.test"));assertEquals(202,known.status());assertEquals(known,unknown);
  String token=mailbox.get(a+":client@example.test");assertNotNull(token);assertFalse(known.body().contains(token));assertNotEquals(token,db.queryForObject("SELECT digest FROM auth_recuperaciones WHERE usuario_id=?",String.class,clientId));
  var body=Map.of("token",token,"passwordNueva","Nueva-Ficticia-2027");
  assertEquals(400,request("POST","boutique-b","/api/auth/password/reset",null,body).status());
  assertEquals(204,request("POST","boutique-a","/api/auth/password/reset",null,body).status());
  assertEquals(400,request("POST","boutique-a","/api/auth/password/reset",null,body).status());
  assertEquals(401,request("GET","boutique-a","/api/ventas/mis-compras",client,null).status());
 }
 @Test void recuperacionConcurrenteSoloSeConsumeUnaVez(){
  var config=ctx.getBean(com.zaxxer.hikari.HikariDataSource.class).getHikariConfigMXBean();config.setMaximumPoolSize(2);
  try{
   assertEquals(202,request("POST","boutique-a","/api/auth/password/forgot",null,Map.of("correo","client@example.test")).status());String token=mailbox.get(a+":client@example.test");var body=Map.of("token",token,"passwordNueva","Nueva-Ficticia-2027");
   var one=CompletableFuture.supplyAsync(()->request("POST","boutique-a","/api/auth/password/reset",null,body).status());var two=CompletableFuture.supplyAsync(()->request("POST","boutique-a","/api/auth/password/reset",null,body).status());
   var codes=new ArrayList<>(List.of(one.join(),two.join()));Collections.sort(codes);assertEquals(List.of(204,400),codes);
  }finally{config.setMaximumPoolSize(1);}
 }
 @Test void tokensVencidosYGlobalNoSeAceptanComoRecuperacionTienda(){
  request("POST","boutique-a","/api/auth/superadmin/password/forgot",null,Map.of("correo","global@example.test"));String token=mailbox.get("null:global@example.test");assertNotNull(token);
  assertEquals(400,request("POST","boutique-a","/api/auth/password/reset",null,Map.of("token",token,"passwordNueva","Nueva-Ficticia-2027")).status());
  db.update("UPDATE auth_recuperaciones SET vence_en=clock_timestamp()-interval '1 second'");
  assertEquals(400,request("POST","boutique-a","/api/auth/superadmin/password/reset",null,Map.of("token",token,"passwordNueva","Nueva-Ficticia-2027")).status());
 }
 @Test void rateLimitPersisteAunqueLoginFalle(){
  for(int i=0;i<15;i++)assertEquals(401,request("POST","boutique-a","/api/auth/login",null,Map.of("correo","missing@example.test","password",pass)).status());
  assertEquals(429,request("POST","boutique-a","/api/auth/login",null,Map.of("correo","missing@example.test","password",pass)).status());
 }
 @Test void fallaCerradoSiFaltaRegistroSesion(){
  db.update("DELETE FROM auth_sesiones WHERE id=?",UUID.fromString(sid(admin)));
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",admin,null).status());
 }

 @Test void catalogoInventarioPedidosYReportesNoCruzanTiendas(){
  final int[] idB=new int[1];
  transaction(em->{
   for(Long tenant:List.of(a,b)){
    String label=tenant.equals(a)?"SOLO_A":"SOLO_B";
    Genero g=new Genero();g.setNombreGenero(label);em.persist(g);
    Categoria c=new Categoria();c.setNombreCategoria(label);c.setTiendaId(tenant);em.persist(c);
    Producto p=new Producto();p.setNombre(label);p.setPrecio(new java.math.BigDecimal("12.50"));p.setTiendaId(tenant);p.setGenero(g);p.setCategoria(c);em.persist(p);if(tenant.equals(b))idB[0]=p.getIdProducto();
    ProductoTalla v=new ProductoTalla();v.setProducto(p);v.setStock(5);em.persist(v);
    Usuario u=em.createQuery("select u from Usuario u where u.correo='client@example.test' and u.tiendaId=:t",Usuario.class).setParameter("t",tenant).getSingleResult();
    Venta sale=new Venta();sale.setUsuario(u);sale.setTiendaId(tenant);sale.setTipoVenta(TipoVenta.ONLINE);sale.setFechaVenta(java.time.LocalDateTime.now());sale.setTotal(new java.math.BigDecimal("12.50"));sale.setSubtotal(sale.getTotal());sale.setCostoDelivery(java.math.BigDecimal.ZERO);sale.setEstado("REGISTRADA");sale.setEstadoPedido(EstadoPedido.RECIBIDO);sale.setMedioPago("YAPE");em.persist(sale);
    DetalleVenta d=new DetalleVenta();d.setVenta(sale);d.setProductoTalla(v);d.setCantidad(1);d.setPrecioUnitario(sale.getTotal());d.setSubtotal(sale.getTotal());em.persist(d);
   }
  });
  String other=login("boutique-b","admin@example.test",false);
  for(String path:List.of("/api/productos/admin","/api/producto-tallas/admin/reportes/inventario","/api/ventas/admin/reportes/detalles")){
   Response ra=request("GET","boutique-a",path,admin,null),rb=request("GET","boutique-b",path,other,null);
   assertEquals(200,ra.status(),ra.body());assertEquals(200,rb.status(),rb.body());assertTrue(ra.body().contains("SOLO_A"),path+" "+ra.body());assertFalse(ra.body().contains("SOLO_B"));assertTrue(rb.body().contains("SOLO_B"),path+" "+rb.body());assertFalse(rb.body().contains("SOLO_A"));
  }
  assertEquals(404,request("GET","boutique-a","/api/productos/admin/"+idB[0],admin,null).status());
  var sales=request("GET","boutique-a","/api/ventas/admin",admin,null);assertEquals(1,json.readTree(sales.body()).size());
  int foreign=db.queryForObject("SELECT id_venta FROM ventas WHERE tienda_id=? LIMIT 1",Integer.class,b);
  assertEquals(404,request("GET","boutique-a","/api/ventas/"+foreign+"/detalle",client,null).status());
  int variantB=db.queryForObject("SELECT pt.id_producto_talla FROM producto_tallas pt JOIN productos p ON p.id_producto=pt.id_producto WHERE p.tienda_id=? LIMIT 1",Integer.class,b);
  assertEquals(404,request("PUT","boutique-a","/api/producto-tallas/admin/"+variantB+"/stock?stock=999",admin,null).status());
  assertEquals(5,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=?",Integer.class,variantB));
  int categoryB=db.queryForObject("SELECT id_categoria FROM categorias WHERE tienda_id=? LIMIT 1",Integer.class,b),gender=db.queryForObject("SELECT id_genero FROM generos LIMIT 1",Integer.class);
  assertEquals(404,request("POST","boutique-a","/api/productos/admin?idGenero="+gender+"&idCategoria="+categoryB,admin,Map.of("nombre","INYECCION_RECHAZADA","precio",10,"tiendaId",b)).status());
  db.update("INSERT INTO yape_configuracion_tienda VALUES (?,true,'900000000','Ficticio')",a);db.update("INSERT INTO yape_qr_imagen VALUES (?,?,?)",a,new byte[]{1},"ficticio");
  var purchase=Map.of("medioPago","YAPE","claveIdempotencia",UUID.randomUUID().toString(),"tienda_id",b,"items",List.of(Map.of("idProductoTalla",variantB,"cantidad",1)),"direccionEnvio",Map.of("departamento","Lima","provincia","Lima","distrito","Ficticio","direccionExacta","Direccion ficticia","referencia",""));
  var denied=request("POST","boutique-a","/api/ventas",client,purchase);assertEquals(409,denied.status(),denied.body());assertTrue(denied.body().contains("no encontrado"));
  assertEquals(2,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));assertEquals(5,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=?",Integer.class,variantB));
 }
 @Test void paginasDePagosCompletasYSinTenantManipulable(){
  db.update("INSERT INTO yape_pagos_manuales(tienda_id,id_venta,estado,importe,moneda) SELECT ?,g,'PENDIENTE',1,'PEN' FROM generate_series(1,235) g",a);
  db.update("INSERT INTO transferencia_pagos_manuales(tienda_id,id_venta,estado,importe,moneda) SELECT ?,g,'PENDIENTE',1,'PEN' FROM generate_series(1,235) g",a);
  db.update("INSERT INTO saas_cobros_contraentrega(tienda_id,id_venta,estado,monto) SELECT ?,g,'PENDIENTE',1 FROM generate_series(1,235) g",a);
  db.update("INSERT INTO saas_envios(tienda_id,id_venta,estado) SELECT ?,g,'PENDIENTE' FROM generate_series(1,235) g",a);
  db.update("INSERT INTO yape_pagos_manuales(tienda_id,id_venta,estado,importe,moneda) VALUES (?,999,'SOLO_B',9,'PEN')",b);
  for(String path:List.of("/api/yape/pagos/admin/reportes","/api/transferencias/pagos/admin/reportes","/api/ventas/admin/contraentrega/reportes")){
   Integer cursor=null;Set<Integer> ids=new HashSet<>();int pages=0;
   do{var res=request("GET","boutique-a",path+"?size=100&tienda_id="+b+(cursor==null?"":"&cursor="+cursor),admin,null);assertEquals(200,res.status(),res.body());var body=json.readTree(res.body());for(var row:body.get("registros")){assertTrue(ids.add(row.get("idVenta").asInt()));assertFalse(row.toString().contains("SOLO_B"));}cursor=body.get("siguienteCursor").isNull()?null:body.get("siguienteCursor").asInt();pages++;}while(cursor!=null&&pages<10);
   assertEquals(235,ids.size());assertEquals(3,pages);
   assertEquals(403,request("GET","boutique-a",path,client,null).status());
   assertEquals(400,request("GET","boutique-a",path+"?size=999",admin,null).status());
   assertEquals(400,request("GET","boutique-a",path+"?size=no-numero",admin,null).status());
  }
 }
 @Test void migracionSeguridadHabilitaRlsSinAccesoPublico(){
  assertEquals(3,db.queryForObject("SELECT count(*) FROM pg_class WHERE relname IN ('auth_sesiones','auth_recuperaciones','auth_limites') AND relrowsecurity",Integer.class));
  assertFalse(db.queryForObject("SELECT has_table_privilege('anon','auth_sesiones','SELECT')",Boolean.class));
  assertFalse(db.queryForObject("SELECT has_table_privilege('authenticated','auth_recuperaciones','SELECT')",Boolean.class));
 }

 @Test void sinEntregaSeguraNoConfirmaEnvioNiEnumeraCuentas(){
  deliveryEnabled=false;
  for(String email:List.of("client@example.test","missing@example.test")){
   var res=request("POST","boutique-a","/api/auth/password/forgot",null,Map.of("correo",email));assertEquals(503,res.status());assertFalse(res.body().contains(email));
  }
  assertTrue(mailbox.isEmpty());assertEquals(0,db.queryForObject("SELECT count(*) FROM auth_recuperaciones",Integer.class));
 }
 @Test void globalCambiaPasswordYRevocaSuSesion(){
  var res=request("POST","boutique-a","/api/superadmin/auth/password/change",global,Map.of("passwordActual",pass,"passwordNueva","Global-Ficticia-2027"));assertEquals(204,res.status(),res.body());
  assertEquals(401,request("GET","boutique-a","/api/superadmin/me",global,null).status());
  assertEquals(200,request("POST","boutique-a","/api/auth/superadmin/login",null,Map.of("correo","global@example.test","password","Global-Ficticia-2027")).status());
 }
 @Test void cambioDePasswordLegadoTambienInvalidaEnlacesDeRecuperacion(){
  assertEquals(202,request("POST","boutique-a","/api/auth/password/forgot",null,Map.of("correo","client@example.test")).status());String token=mailbox.get(a+":client@example.test");
  var body=Map.of("contrasenaActual",pass,"contrasenaNueva","Nueva-Ficticia-2027","confirmarContrasena","Nueva-Ficticia-2027");
  assertEquals(204,request("PUT","boutique-a","/api/perfil/contrasena",client,body).status());
  assertEquals(400,request("POST","boutique-a","/api/auth/password/reset",null,Map.of("token",token,"passwordNueva","Otra-Ficticia-2028")).status());
 }
 @Test void jwtLegadoSinJtiEsRechazadoAunqueLaFirmaYCredencialesSeanValidas(){
  var current=ctx.getBean(JwtService.class).getClaims(admin);String secret=ctx.getEnvironment().getProperty("app.jwt.secret");
  String legacy=io.jsonwebtoken.Jwts.builder().subject(current.getSubject()).claim("uid",adminId).claim("tid",a).claim("rol","ADMIN").claim("cv",current.get("cv")).expiration(new Date(System.currentTimeMillis()+60000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
  assertEquals(401,request("GET","boutique-a","/api/usuarios/admin",legacy,null).status());
 }

 @Test void nuevoLoginIgnoraBearerRevocadoYExigeCredencialesValidas(){
  db.update("UPDATE auth_sesiones SET revocada=true WHERE id=?",UUID.fromString(sid(admin)));
  assertEquals(200,request("POST","boutique-a","/api/auth/login",admin,Map.of("correo","admin@example.test","password",pass)).status());
  assertEquals(401,request("POST","boutique-a","/api/auth/login",admin,Map.of("correo","admin@example.test","password","Incorrecta-Ficticia-2027")).status());
 }

 @Test void indiceGlobalLegadoNoPermiteEnumerarCuentasDeOtroAmbito(){
  pg.verifiedUrl();
  db.execute("CREATE UNIQUE INDEX auth_fixture_global_legacy ON usuarios(correo) WHERE correo='global@example.test'");
  try{
   var data=Map.of("nombre","Nuevo","apellido","Ficticio","correo","global@example.test","password",pass);
   var existing=request("POST","boutique-a","/api/auth/registro",null,data);
   var fresh=request("POST","boutique-a","/api/auth/registro",null,Map.of("nombre","Nuevo","apellido","Ficticio","correo","fresh@example.test","password",pass));
   assertEquals(200,existing.status());assertEquals(existing,fresh);
   assertEquals(0,db.queryForObject("SELECT count(*) FROM usuarios WHERE correo='global@example.test' AND tienda_id=?",Integer.class,a));
  }finally{db.execute("DROP INDEX auth_fixture_global_legacy");}
 }

 @Test void perfilAntiguoNoPuedeRestaurarContrasenaTrasElCambio(){
  var repo=ctx.getBean(com.tiendasebas.tiendasebas.repository.UsuarioRepository.class);
  Usuario viejo=repo.findById(clientId).orElseThrow();viejo.setNombre("Perfil anterior");
  String next="Nueva-Ficticia-2027";
  assertEquals(204,request("POST","boutique-a","/api/auth/password/change",client,Map.of("passwordActual",pass,"passwordNueva",next)).status());
  assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.saveAndFlush(viejo));
  String actual=db.queryForObject("SELECT password_hash FROM usuarios WHERE id_usuario=?",String.class,clientId);
  assertTrue(ctx.getBean(PasswordEncoder.class).matches(next,actual));assertFalse(ctx.getBean(PasswordEncoder.class).matches(pass,actual));
 }
}
