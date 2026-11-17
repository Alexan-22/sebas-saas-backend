package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapComercialLecturaRlsTest {
 static javax.sql.DataSource source;
 static final java.util.concurrent.ConcurrentHashMap<Long,String> mailbox=new java.util.concurrent.ConcurrentHashMap<>();
 static int port;
 static java.net.http.HttpResponse<String> call(String host,String method,String route,String body,String token)throws Exception{
  var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+route)).timeout(java.time.Duration.ofSeconds(20)).header("Host",host+".example.test").header("Content-Type","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  return java.net.http.HttpClient.newHttpClient().send(builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
 }
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
    try(var insert=c.prepareStatement("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,estado,tienda_id) SELECT 'Fixture','Local','igual@example.test',?,r.id_rol,true,t.id FROM roles r CROSS JOIN tiendas t WHERE r.nombre_rol='ADMIN'")){insert.setString(1,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Fixture-Auth-2026"));insert.executeUpdate();}
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B025__auth_tenant_local.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B026__resolver_host_auth_local.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B027__reportes_ventas_rls_local.sql"));
    c.createStatement().execute("INSERT INTO direcciones_envio(tienda_id,departamento,provincia,distrito,direccion_exacta,costo_delivery) SELECT id,'Fixture','Fixture','Fixture','DIRECCION_'||id,0 FROM tiendas; INSERT INTO ventas(id_usuario,tipo_venta,fecha_venta,total,estado,estado_pedido,medio_pago,id_direccion_envio,tienda_id) SELECT u.id_usuario,'ONLINE',now(),10,'REGISTRADA','RECIBIDO','CONTRA_ENTREGA',d.id,u.tienda_id FROM usuarios u JOIN direcciones_envio d ON d.tienda_id=u.tienda_id; INSERT INTO detalle_venta(id_venta,id_producto_talla,cantidad,precio_unitario,subtotal) SELECT v.id_venta,pt.id_producto_talla,1,10,10 FROM ventas v JOIN productos p ON p.tienda_id=v.tienda_id JOIN producto_tallas pt ON pt.id_producto=p.id_producto;");
    c.setAutoCommit(false);try{assertThrows(SQLException.class,()->c.createStatement().execute("INSERT INTO detalle_venta(id_venta,id_producto_talla,cantidad,precio_unitario,subtotal) VALUES(1,2,1,10,10)"));c.rollback();}finally{c.setAutoCommit(true);}
    c.setAutoCommit(false);try{assertThrows(SQLException.class,()->c.createStatement().execute("UPDATE ventas SET id_direccion_envio=2 WHERE id_venta=1"));c.rollback();}finally{c.setAutoCommit(true);}

   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_auth_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    var application=new org.springframework.boot.SpringApplication(Config.class);application.setEnvironment(env);
    try(var context=application.run("--spring.config.location=optional:classpath:/http-rls-empty/","--server.address=127.0.0.1","--server.port=0","--server.forward-headers-strategy=none","--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.rls.auth-enabled=true","--app.jwt.secret="+UUID.randomUUID()+UUID.randomUUID(),"--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test","--app.tenant.base-domain=example.test","--app.storage.provider=local","--app.upload.dir=target/http-rls-uploads","--logging.level.root=WARN")){
     port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
     var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);assertEquals("sebas_auth_app",db.queryForObject("SELECT current_user",String.class));
     String a=login("fixture-lima","Fixture-Auth-2026"),b=login("fixture-cusco","Fixture-Auth-2026");
     for(int tenant=1;tenant<=2;tenant++){
      String host=tenant==1?"fixture-lima":"fixture-cusco",token=tenant==1?a:b;
      for(String route:List.of("/api/ventas/admin","/api/ventas/admin/reportes/detalles","/api/producto-tallas/admin/reportes/inventario")){
       var response=call(host,"GET",route,null,token);assertEquals(200,response.statusCode(),route);var rows=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());assertTrue(rows.isArray());assertEquals(1,rows.size(),route);
      }
      var own=call(host,"GET","/api/ventas/admin/"+tenant+"/detalle",null,token);assertEquals(200,own.statusCode());assertTrue(own.body().contains("DIRECCION_"+tenant));assertFalse(own.body().contains("DIRECCION_"+(tenant==1?2:1)));
      var foreign=call(host,"GET","/api/ventas/admin/"+(tenant==1?2:1)+"/detalle",null,token);assertEquals(409,foreign.statusCode());
     }
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager.class));
     for(long tenant:List.of(1L,2L)){TenantContext.setCurrentTenant(tenant);try{tx.executeWithoutResult(st->{assertEquals(1,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM detalle_venta",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM direcciones_envio",Integer.class));});}finally{TenantContext.clear();}}
     assertThrows(org.springframework.dao.DataAccessException.class,()->db.queryForList("SELECT * FROM yape_pagos_manuales"));
     assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE ventas SET estado='CANCELADA' WHERE id_venta=2"));
     try(var ex=Executors.newFixedThreadPool(2)){var f1=ex.submit(()->call("fixture-lima","GET","/api/ventas/admin",null,a));var f2=ex.submit(()->call("fixture-cusco","GET","/api/ventas/admin",null,b));assertEquals(200,f1.get().statusCode());assertEquals(200,f2.get().statusCode());}
     assertEquals(0,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));

    }
   }
  }finally{TenantContext.clear();}
 }
}



