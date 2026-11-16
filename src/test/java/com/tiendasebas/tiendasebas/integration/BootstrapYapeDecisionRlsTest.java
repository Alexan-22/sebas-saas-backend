package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapYapeDecisionRlsTest {
 static javax.sql.DataSource source;
 static final java.util.concurrent.ConcurrentHashMap<Long,String> mailbox=new java.util.concurrent.ConcurrentHashMap<>();
 static int port;
 static java.net.http.HttpResponse<String> call(String host,String method,String route,String body,String token)throws Exception{
  var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+route)).timeout(java.time.Duration.ofSeconds(20)).header("Host",host+".example.test").header("Content-Type","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  return java.net.http.HttpClient.newHttpClient().send(builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
 }


 static String cuenta(String host,String email)throws Exception{var res=call(host,"POST","/api/auth/login","{\"correo\":\""+email+"\",\"password\":\"Fixture-Auth-2026\"}",null);assertEquals(200,res.statusCode());return tools.jackson.databind.json.JsonMapper.builder().build().readTree(res.body()).get("token").asText();}
 static int pedido(String token)throws Exception{var res=call("fixture-lima","POST","/api/ventas",compra(UUID.randomUUID().toString(),1,1),token);assertEquals(200,res.statusCode());return tools.jackson.databind.json.JsonMapper.builder().build().readTree(res.body()).get("idVenta").asInt();}
 static String compra(String key,int variant,int quantity){return "{\"claveIdempotencia\":\""+key+"\",\"medioPago\":\"YAPE\",\"items\":[{\"idProductoTalla\":"+variant+",\"cantidad\":"+quantity+"}],\"direccionEnvio\":{\"departamento\":\"Lima\",\"provincia\":\"Lima\",\"distrito\":\"Miraflores\",\"direccionExacta\":\"Fixture local\"}}";}
 static String login(String host,String password)throws Exception{var r=call(host,"POST","/api/auth/login","{\"correo\":\"igual@example.test\",\"password\":\""+password+"\"}",null);assertEquals(200,r.statusCode());return tools.jackson.databind.json.JsonMapper.builder().build().readTree(r.body()).get("token").asText();}

 @Configuration @EnableTransactionManagement @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")

 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.context.annotation.ComponentScan(basePackages="com.tiendasebas.tiendasebas",excludeFilters=@org.springframework.context.annotation.ComponentScan.Filter(type=org.springframework.context.annotation.FilterType.REGEX,pattern=".*(integration|Test|TiendaSebasApplication).*"))
 static class Config{
  @Bean @Primary com.tiendasebas.tiendasebas.security.RecoveryDelivery delivery(){return new com.tiendasebas.tiendasebas.security.RecoveryDelivery(){public boolean disponible(){return true;}public void enviar(String email,String token,Long tenant){mailbox.put(tenant,token);}};}

  @Bean javax.sql.DataSource dataSource(){return source;}
  @Bean org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(){return new org.springframework.jdbc.core.JdbcTemplate(source);}
  @Bean org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean entityManagerFactory(){var f=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();f.setDataSource(source);f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");f.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
  @Bean com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager(f,source);}
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
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B029__decision_yape_rls_local.sql"));
    c.createStatement().execute("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,estado,tienda_id) SELECT 'Admin','Fixture','admin@example.test',u.password_hash,r.id_rol,true,u.tienda_id FROM usuarios u CROSS JOIN roles r WHERE u.correo='igual@example.test' AND r.nombre_rol='ADMIN'; INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,estado,tienda_id) SELECT 'Segundo','Fixture','segundo@example.test',password_hash,id_rol,true,tienda_id FROM usuarios WHERE correo='igual@example.test' AND tienda_id=1;");
    c.createStatement().execute("CREATE FUNCTION public.fixture_error_auditoria() RETURNS trigger LANGUAGE plpgsql AS $body$ BEGIN IF NEW.observacion='FALLO_FIXTURE' THEN RAISE EXCEPTION 'Fallo fixture auditoria' USING ERRCODE='23514'; END IF; RETURN NEW; END $body$; CREATE TRIGGER fixture_error_auditoria BEFORE INSERT ON yape_pagos_auditoria FOR EACH ROW EXECUTE FUNCTION fixture_error_auditoria();");

    c.createStatement().execute("CREATE FUNCTION public.fixture_fallar_segundo_detalle() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF EXISTS(SELECT 1 FROM public.detalle_venta WHERE id_venta=NEW.id_venta) THEN RAISE EXCEPTION 'Fixture detalle duplicado' USING ERRCODE='23514'; END IF; RETURN NEW; END $$; CREATE TRIGGER fixture_segundo_detalle BEFORE INSERT ON detalle_venta FOR EACH ROW EXECUTE FUNCTION fixture_fallar_segundo_detalle();");
    c.createStatement().execute("INSERT INTO yape_configuracion_tienda(tienda_id,habilitado,numero_yape,titular_yape,qr_url) SELECT id,true,'900000000','Fixture','/fixture' FROM tiendas; INSERT INTO yape_qr_imagen(tienda_id,imagen,tamano_bytes,sha256) SELECT id,decode('00','hex'),1,repeat('a',64) FROM tiendas; UPDATE producto_tallas SET stock=10;");

   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_auth_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    var application=new org.springframework.boot.SpringApplication(Config.class);application.setEnvironment(env);
    try(var context=application.run("--spring.config.location=optional:classpath:/http-rls-empty/","--server.address=127.0.0.1","--server.port=0","--server.forward-headers-strategy=none","--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.rls.auth-enabled=true","--app.jwt.secret="+UUID.randomUUID()+UUID.randomUUID(),"--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test","--app.tenant.base-domain=example.test","--app.storage.provider=local","--app.upload.dir=target/http-rls-uploads","--logging.level.root=WARN")){
     port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
     var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);assertEquals("sebas_auth_app",db.queryForObject("SELECT current_user",String.class));
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager.class));
     String client=cuenta("fixture-lima","igual@example.test"),second=cuenta("fixture-lima","segundo@example.test"),admin=cuenta("fixture-lima","admin@example.test"),foreign=cuenta("fixture-cusco","admin@example.test");
     int approved=pedido(client);
     assertTrue(call("fixture-lima","GET","/api/yape/pagos/mios/"+approved,null,client).body().contains("PENDIENTE"));
     assertEquals(404,call("fixture-lima","GET","/api/yape/pagos/mios/"+approved,null,second).statusCode());
     assertEquals(404,call("fixture-lima","GET","/api/yape/pagos/mios/"+approved+"/qr",null,second).statusCode());
     assertEquals(403,call("fixture-lima","POST","/api/yape/pagos/admin/"+approved+"/aprobar","{}",client).statusCode());
     assertEquals(404,call("fixture-cusco","POST","/api/yape/pagos/admin/"+approved+"/aprobar","{}",foreign).statusCode());
     assertEquals(200,call("fixture-lima","POST","/api/yape/pagos/admin/"+approved+"/aprobar","{}",admin).statusCode());
     assertEquals(409,call("fixture-lima","POST","/api/yape/pagos/admin/"+approved+"/aprobar","{}",admin).statusCode());
     assertTrue(call("fixture-lima","GET","/api/yape/pagos/mios/"+approved,null,client).body().contains("APROBADO"));
     int rejected=pedido(second);
     String reason="{\"motivo\":\"Fixture rechazo\"}";
     assertEquals(200,call("fixture-lima","POST","/api/yape/pagos/admin/"+rejected+"/rechazar",reason,admin).statusCode());
     assertEquals(409,call("fixture-lima","POST","/api/yape/pagos/admin/"+rejected+"/rechazar",reason,admin).statusCode());
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(9,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_inventario_reposiciones WHERE id_venta=?",Integer.class,rejected));});}finally{TenantContext.clear();}
     int rollback=pedido(client);
     var failed=call("fixture-lima","POST","/api/yape/pagos/admin/"+rollback+"/rechazar","{\"motivo\":\"FALLO_FIXTURE\"}",admin);assertEquals(500,failed.statusCode());assertFalse(failed.body().contains("fixture_error_auditoria"));assertFalse(failed.body().contains("INSERT INTO"));
     assertTrue(call("fixture-lima","GET","/api/yape/pagos/mios/"+rollback,null,client).body().contains("PENDIENTE"));
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(8,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM yape_inventario_reposiciones WHERE id_venta=?",Integer.class,rollback));});}finally{TenantContext.clear();}
     pool.setMaximumPoolSize(2);
     try(var ex=Executors.newFixedThreadPool(2)){var a=ex.submit(()->call("fixture-lima","POST","/api/yape/pagos/admin/"+rollback+"/rechazar",reason,admin));var b=ex.submit(()->call("fixture-lima","POST","/api/yape/pagos/admin/"+rollback+"/rechazar",reason,admin));var codes=List.of(a.get().statusCode(),b.get().statusCode());assertTrue(codes.contains(200));assertTrue(codes.contains(409));}
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->{assertEquals(9,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=1",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_inventario_reposiciones WHERE id_venta=?",Integer.class,rollback));});}finally{TenantContext.clear();}
     String manipulated=compra(UUID.randomUUID().toString(),1,1).replace("\"medioPago\"","\"idUsuario\":1,\"tiendaId\":2,\"medioPago\"");
     var attempted=call("fixture-lima","POST","/api/ventas",manipulated,second);
     assertTrue(java.util.Set.of(200,400).contains(attempted.statusCode()));
     if(attempted.statusCode()==200){int id=tools.jackson.databind.json.JsonMapper.builder().build().readTree(attempted.body()).get("idVenta").asInt();assertEquals(200,call("fixture-lima","GET","/api/yape/pagos/mios/"+id,null,second).statusCode());assertEquals(404,call("fixture-lima","GET","/api/yape/pagos/mios/"+id,null,client).statusCode());}
     TenantContext.setCurrentTenant(2L);try{tx.executeWithoutResult(st->assertEquals(10,db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=2",Integer.class)));}finally{TenantContext.clear();}

    }
   }
  }finally{TenantContext.clear();}
 }
}






