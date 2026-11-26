package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapCheckoutRlsTest {
 static javax.sql.DataSource source;
 static final java.util.concurrent.ConcurrentHashMap<Long,String> mailbox=new java.util.concurrent.ConcurrentHashMap<>();
 static int port;
 private record RespuestaHttp(int statusCode, String body) {}

 static RespuestaHttp call(String host, String method, String route, String body, String token) throws Exception {
  return call(host, method, route, body, token, null);
 }

 private static RespuestaHttp call(
     String host, String method, String route, String body, String token, String checkoutToken
 ) throws Exception {
  var command = new ArrayList<>(List.of(
      "curl", "--disable", "--silent", "--show-error", "--noproxy", "*",
      "--max-time", "20", "--request", method, "--header", "Host: " + host + ".example.test",
      "--header", "Content-Type: application/json", "--write-out", "\n%{http_code}",
      "http://127.0.0.1:" + port + route
  ));
  if (token != null) command.addAll(List.of("--header", "Authorization: Bearer " + token));
  if (checkoutToken != null) command.addAll(List.of("--header", "X-Checkout-Token: " + checkoutToken));
  if (body != null) command.addAll(List.of("--data-binary", "@-"));

  var process = new ProcessBuilder(command).redirectErrorStream(true).start();
  try (var input = process.getOutputStream()) {
   if (body != null) input.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
  if (process.waitFor() != 0) throw new IllegalStateException("Falló la solicitud HTTP aislada: " + output);
  int separator = output.lastIndexOf('\n');
  return new RespuestaHttp(Integer.parseInt(output.substring(separator + 1).trim()), output.substring(0, separator));
 }

 static String compra(String key,int variant,int quantity){return "{\"claveIdempotencia\":\""+key+"\",\"medioPago\":\"YAPE\",\"items\":[{\"idProductoTalla\":"+variant+",\"cantidad\":"+quantity+"}],\"direccionEnvio\":{\"departamento\":\"Lima\",\"provincia\":\"Lima\",\"distrito\":\"Miraflores\",\"direccionExacta\":\"Fixture local\"}}";}
 static String login(String host,String password)throws Exception{
  return login(host,"igual@example.test",password);
 }
 static String login(String host,String email,String password)throws Exception{
  var r=call(host,"POST","/api/auth/login","{\"correo\":\""+email+"\",\"password\":\""+password+"\"}",null);
  assertEquals(200,r.statusCode(),r.body());
  return tools.jackson.databind.json.JsonMapper.builder().build().readTree(r.body()).get("token").asText();
 }

 @Configuration @EnableTransactionManagement @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")

 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.context.annotation.ComponentScan(basePackages="com.tiendasebas.tiendasebas",excludeFilters=@org.springframework.context.annotation.ComponentScan.Filter(type=org.springframework.context.annotation.FilterType.REGEX,pattern=".*(integration|Test|TiendaSebasApplication).*"))
 static class Config{
  @Bean @Primary com.tiendasebas.tiendasebas.security.RecoveryDelivery delivery(){return new com.tiendasebas.tiendasebas.security.RecoveryDelivery(){public boolean disponible(){return true;}public void enviar(String email,String token,Long tenant){mailbox.put(tenant,token);}};}

  @Bean javax.sql.DataSource dataSource(){return source;}
  @Bean org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(){return new org.springframework.jdbc.core.JdbcTemplate(source);}
  @Bean org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean entityManagerFactory(){var f=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();f.setDataSource(source);f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");f.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
  @Bean CatalogoRlsLocalService servicio(org.springframework.jdbc.core.JdbcTemplate db){return new CatalogoRlsLocalService(db);}
 }
 @Test void loginRealPoolRollbackYConcurrencia()throws Exception{
  try(var pg=new ComercialPostgresFixture()){
   pg.start();String password=UUID.randomUUID().toString();
   try(var c=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())){
    c.createStatement().execute("CREATE ROLE fixture_migrador NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE sebas_auth_app LOGIN PASSWORD '"+password+"' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE; CREATE ROLE anon; CREATE ROLE authenticated; GRANT USAGE,CREATE ON SCHEMA public TO fixture_migrador; SET ROLE fixture_migrador;");
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B000__historico_vacio.sql"));
    for(int n=1;n<=22;n++){final String prefix="V"+n+"__";try(var files=Files.list(Path.of("src/main/resources/db/"+(n<=12?"migration":"manual")))){BootstrapVacioPostgresTest.ejecutar(c,files.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow());}}
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B023__cerrar_acceso_publico.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("docs/staging-bootstrap/fixtures-boutiques.sql"));
    c.createStatement().execute("INSERT INTO generos(nombre_genero) VALUES('Fixture'); INSERT INTO categorias(nombre_categoria,estado,tienda_id) SELECT 'Fixture',true,id FROM tiendas; INSERT INTO productos(nombre,precio,id_genero,id_categoria,tienda_id) SELECT 'Producto '||tienda_id,10,1,id_categoria,tienda_id FROM categorias; INSERT INTO producto_tallas(id_producto,stock) SELECT id_producto,10 FROM productos;");
    c.createStatement().execute("INSERT INTO roles(nombre_rol) VALUES('ADMIN'),('CLIENTE'),('EMPLEADO'),('SUPER_ADMIN');");
    try(var insert=c.prepareStatement("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,estado,tienda_id) SELECT 'Fixture','Local','igual@example.test',?,r.id_rol,true,t.id FROM roles r CROSS JOIN tiendas t WHERE r.nombre_rol='CLIENTE'")){insert.setString(1,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Fixture-Auth-2026"));insert.executeUpdate();}
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B025__auth_tenant_local.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B026__resolver_host_auth_local.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B027__reportes_ventas_rls_local.sql"));
    c.createStatement().execute("INSERT INTO direcciones_envio(tienda_id,departamento,provincia,distrito,direccion_exacta,costo_delivery) SELECT id,'Fixture','Fixture','Fixture','DIRECCION_'||id,0 FROM tiendas; INSERT INTO ventas(id_usuario,tipo_venta,fecha_venta,total,estado,estado_pedido,medio_pago,id_direccion_envio,tienda_id) SELECT u.id_usuario,'ONLINE',now(),10,'REGISTRADA','RECIBIDO','CONTRA_ENTREGA',d.id,u.tienda_id FROM usuarios u JOIN direcciones_envio d ON d.tienda_id=u.tienda_id; INSERT INTO detalle_venta(id_venta,id_producto_talla,cantidad,precio_unitario,subtotal) SELECT v.id_venta,pt.id_producto_talla,1,10,10 FROM ventas v JOIN productos p ON p.tienda_id=v.tienda_id JOIN producto_tallas pt ON pt.id_producto=p.id_producto;");
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B028__checkout_yape_pendiente_rls.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/migration/V26__checkout_invitado_multitienda_candidato.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/test/resources/db/checkout-invitado-rls-fixture.sql"));
    c.createStatement().execute("INSERT INTO transferencia_cuentas_bancarias(id,tienda_id,banco,titular,numero_cuenta,habilitado) SELECT id,id,'BCP','Fixture','0000000',true FROM tiendas");
    c.createStatement().execute("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,estado,tienda_id) SELECT 'Fixture','Admin','admin@example.test',u.password_hash,r.id_rol,true,u.tienda_id FROM usuarios u CROSS JOIN roles r WHERE r.nombre_rol='ADMIN'");
    c.createStatement().execute("CREATE FUNCTION public.fixture_fallar_segundo_detalle() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF EXISTS(SELECT 1 FROM public.detalle_venta WHERE id_venta=NEW.id_venta) THEN RAISE EXCEPTION 'Fixture detalle duplicado' USING ERRCODE='23514'; END IF; RETURN NEW; END $$; CREATE TRIGGER fixture_segundo_detalle BEFORE INSERT ON detalle_venta FOR EACH ROW EXECUTE FUNCTION fixture_fallar_segundo_detalle();");
    c.createStatement().execute("INSERT INTO yape_configuracion_tienda(tienda_id,habilitado,numero_yape,titular_yape,qr_url) SELECT id,true,'900000000','Fixture','/fixture' FROM tiendas; INSERT INTO yape_qr_imagen(tienda_id,imagen,tamano_bytes,sha256) SELECT id,decode('00','hex'),1,repeat('a',64) FROM tiendas; UPDATE producto_tallas SET stock=3;");

   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_auth_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    var application=new org.springframework.boot.SpringApplication(Config.class);application.setEnvironment(env);
    try(var context=application.run("--spring.config.location=optional:classpath:/http-rls-empty/","--server.address=127.0.0.1","--server.port=0","--server.forward-headers-strategy=none","--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.rls.auth-enabled=true","--spring.profiles.active=checkout-invitado-test","--sebas.checkout.invitado.enabled=true","--app.jwt.secret="+UUID.randomUUID()+UUID.randomUUID(),"--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test","--app.tenant.base-domain=example.test","--app.storage.provider=local","--app.upload.dir=target/http-rls-uploads","--logging.level.root=WARN")){
     port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
     var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);assertEquals("sebas_auth_app",db.queryForObject("SELECT current_user",String.class));
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager.class));
     String token=login("fixture-lima","Fixture-Auth-2026"),other=login("fixture-cusco","Fixture-Auth-2026");
     String key=UUID.randomUUID().toString(),body=compra(key,1,1);
     var first=call("fixture-lima","POST","/api/ventas",body,token);assertEquals(200,first.statusCode());
     var repeat=call("fixture-lima","POST","/api/ventas",body,token);assertEquals(200,repeat.statusCode());
     var json=tools.jackson.databind.json.JsonMapper.builder().build();assertEquals(json.readTree(first.body()).get("idVenta"),json.readTree(repeat.body()).get("idVenta"));
     assertEquals(409,call("fixture-lima","POST","/api/ventas",compra(key,1,2),token).statusCode());
     assertEquals(409,call("fixture-cusco","POST","/api/ventas",compra(UUID.randomUUID().toString(),1,1),other).statusCode());
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(2,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));});}finally{TenantContext.clear();}
     assertEquals(409,call("fixture-lima","POST","/api/ventas",compra(UUID.randomUUID().toString(),1,3),token).statusCode());
     String dbFailure=compra(UUID.randomUUID().toString(),1,1).replace("}],", "},{\"idProductoTalla\":1,\"cantidad\":1}],");
     assertEquals(409,call("fixture-lima","POST","/api/ventas",dbFailure,token).statusCode());
     String failure=compra(UUID.randomUUID().toString(),1,1).replace("}],", "},{\"idProductoTalla\":999999,\"cantidad\":1}],");
     assertEquals(409,call("fixture-lima","POST","/api/ventas",failure,token).statusCode());
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(2,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM direcciones_envio",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM detalle_venta",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_checkout_reintentos",Integer.class));db.update("UPDATE producto_tallas SET stock=1 WHERE id_producto_talla=1");});}finally{TenantContext.clear();}
     pool.setMaximumPoolSize(2);
     try(var ex=Executors.newFixedThreadPool(2)){
      var f1=ex.submit(()->call("fixture-lima","POST","/api/ventas",compra(UUID.randomUUID().toString(),1,1),token));var f2=ex.submit(()->call("fixture-lima","POST","/api/ventas",compra(UUID.randomUUID().toString(),1,1),token));var codes=List.of(f1.get().statusCode(),f2.get().statusCode());assertTrue(codes.contains(200));assertTrue(codes.contains(409));
     }
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->db.update("UPDATE producto_tallas SET stock=1 WHERE id_producto_talla=1"));}finally{TenantContext.clear();}
     String same=compra(UUID.randomUUID().toString(),1,1);
     try(var ex=Executors.newFixedThreadPool(2)){var f1=ex.submit(()->call("fixture-lima","POST","/api/ventas",same,token));var f2=ex.submit(()->call("fixture-lima","POST","/api/ventas",same,token));var one=f1.get();var two=f2.get();assertEquals(200,one.statusCode());assertEquals(200,two.statusCode());assertEquals(json.readTree(one.body()).get("idVenta"),json.readTree(two.body()).get("idVenta"));}
     TenantContext.setCurrentTenant(2L);try{tx.executeWithoutResult(st->assertEquals(3,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=2",Integer.class)));}finally{TenantContext.clear();}
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(0,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE yape_pagos_manuales SET estado='APROBADO'"));st.setRollbackOnly();});}finally{TenantContext.clear();}

     probarCheckoutInvitado(db, tx, json);
    }
   }
  }finally{TenantContext.clear();}
 }
 private static void probarCheckoutInvitado(
     org.springframework.jdbc.core.JdbcTemplate db,
     org.springframework.transaction.support.TransactionTemplate tx, tools.jackson.databind.json.JsonMapper json
 ) throws Exception {
  assertEquals(1, db.queryForObject("""
      SELECT count(*) FROM pg_roles WHERE rolname=current_user AND current_user=session_user
          AND NOT rolsuper AND NOT rolbypassrls
      """, Integer.class));
  assertEquals(0, db.queryForObject("""
      SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace
          AND pg_get_userbyid(relowner)=current_user
      """, Integer.class));
  conTienda(tx, 1L, () -> db.update("UPDATE producto_tallas SET stock=3 WHERE id_producto_talla=1"));
  var yape = checkout("fixture-lima", "YAPE", UUID.randomUUID(), 1, 1);
  assertEquals(200, yape.statusCode(), yape.body());
  var yapeJson = json.readTree(yape.body());
  int ventaYape = yapeJson.get("compra").get("idVenta").asInt();
  String tokenYape = yapeJson.get("tokenAcceso").asText();

  var transferencia = checkout("fixture-cusco", "TRANSFERENCIA", UUID.randomUUID(), 2, 1);
  assertEquals(200, transferencia.statusCode(), transferencia.body());
  var transferenciaJson = json.readTree(transferencia.body());
  int ventaTransferencia = transferenciaJson.get("compra").get("idVenta").asInt();
  assertEquals(2L, transferenciaJson.get("cuentaBancariaId").asLong());
  conTienda(tx, 2L, () -> assertThrows(org.springframework.dao.DataAccessException.class,
      () -> db.update("UPDATE transferencia_cuentas_bancarias SET id=id WHERE id=2")));
  assertEquals(200, detalleInvitado("fixture-lima", ventaYape, tokenYape).statusCode());
  assertEquals(404, detalleInvitado("fixture-cusco", ventaYape, tokenYape).statusCode());
  assertEquals(404, detalleInvitado("fixture-lima", ventaYape, transferenciaJson.get("tokenAcceso").asText()).statusCode());

  String admin = login("fixture-lima", "admin@example.test", "Fixture-Auth-2026");
  var pedidosAdmin = call("fixture-lima", "GET", "/api/ventas/admin", null, admin);
  assertEquals(200, pedidosAdmin.statusCode(), pedidosAdmin.body());
  assertTrue(pedidosAdmin.body().contains("\"idVenta\":" + ventaYape));
  assertFalse(pedidosAdmin.body().contains("\"idVenta\":" + ventaTransferencia));

  conTienda(tx, 1L, () -> {
   assertEquals(2, db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1", Integer.class));
   assertEquals(1, db.queryForObject("SELECT count(*) FROM yape_pagos_manuales WHERE id_venta=? AND estado='PENDIENTE' AND usuario_id IS NULL", Integer.class, ventaYape));
  });
  conTienda(tx, 2L, () -> {
   assertEquals(2, db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=2", Integer.class));
   assertEquals(1, db.queryForObject("SELECT count(*) FROM transferencia_pagos_manuales WHERE id_venta=? AND estado='PENDIENTE' AND usuario_id IS NULL", Integer.class, ventaTransferencia));
  });
  assertEquals(409, checkout("fixture-lima", "YAPE", UUID.randomUUID(), 1, 3).statusCode());
  assertEquals(409, checkout("fixture-cusco", "YAPE", UUID.randomUUID(), 1, 1).statusCode());

  UUID clave = UUID.randomUUID();
  try (var tareas = Executors.newFixedThreadPool(2)) {
   var primera = tareas.submit(() -> checkout("fixture-lima", "YAPE", clave, 1, 1));
   var segunda = tareas.submit(() -> checkout("fixture-lima", "YAPE", clave, 1, 1));
   assertEquals(List.of(200, 409), java.util.stream.Stream.of(primera.get().statusCode(),
       segunda.get().statusCode()).sorted().toList());
  }
  try (var tareas = Executors.newFixedThreadPool(2)) {
   var primera = tareas.submit(() -> checkout("fixture-lima", "YAPE", UUID.randomUUID(), 1, 1));
   var segunda = tareas.submit(() -> checkout("fixture-lima", "YAPE", UUID.randomUUID(), 1, 1));
   assertEquals(List.of(200, 409), java.util.stream.Stream.of(primera.get().statusCode(),
       segunda.get().statusCode()).sorted().toList());
  }
  conTienda(tx, 1L, () -> {
   assertEquals(0, db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1", Integer.class));
   assertEquals(3, db.queryForObject("SELECT count(*) FROM checkout_invitado_reintentos", Integer.class));
  });
  assertEquals(0, db.queryForObject("SELECT count(*) FROM checkout_invitado_reintentos", Integer.class));
 }

 private static void conTienda(org.springframework.transaction.support.TransactionTemplate tx, long id, Runnable action) {
  TenantContext.setCurrentTenant(id);
  try { tx.executeWithoutResult(status -> action.run()); }
  finally { TenantContext.clear(); }
 }

 private static RespuestaHttp checkout(String host, String medio, UUID clave, int variante, int cantidad)
     throws Exception {
  String body = compra(clave.toString(), variante, cantidad).replace("\"YAPE\"", "\"" + medio + "\"");
  body = body.substring(0, body.length()-1)
      + ",\"cliente\":{\"nombres\":\"Fixture\",\"apellidos\":\"Local\",\"correo\":\"guest@example.test\",\"telefono\":\"900000000\"}"
      + ("TRANSFERENCIA".equals(medio) ? ",\"cuentaBancariaId\":2" : "") + "}";
  return call(host, "POST", "/api/ventas/invitado", body, null);
 }

 private static RespuestaHttp detalleInvitado(String host, int idVenta, String token) throws Exception {
  return call(host, "GET", "/api/ventas/invitado/" + idVenta, null, null, token);
 }

}
