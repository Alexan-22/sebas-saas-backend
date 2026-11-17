package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapHttpAuthRlsTest {
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
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("docs/staging-auth-rls/integracion/06-auth-staging-borrador.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B026__resolver_host_auth_local.sql"));
   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_auth_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    var application=new org.springframework.boot.SpringApplication(Config.class);application.setEnvironment(env);
    try(var context=application.run("--spring.config.location=optional:classpath:/http-rls-empty/","--server.address=127.0.0.1","--server.port=0","--server.forward-headers-strategy=none","--spring.sql.init.mode=never","--spring.jpa.open-in-view=false","--app.rls.auth-enabled=true","--app.jwt.secret="+UUID.randomUUID()+UUID.randomUUID(),"--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test","--app.tenant.base-domain=example.test","--app.storage.provider=local","--app.upload.dir=target/http-rls-uploads","--logging.level.root=WARN")){
     port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
     var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager.class));
     assertEquals("sebas_auth_app",db.queryForObject("SELECT current_user",String.class));
     String a=login("fixture-lima","Fixture-Auth-2026"),b=login("fixture-cusco","Fixture-Auth-2026");
     assertEquals(200,call("fixture-lima","GET","/api/perfil",null,a).statusCode());
     assertEquals(401,call("fixture-cusco","GET","/api/perfil",null,a).statusCode());
     assertEquals(401,call("fixture-lima","POST","/api/auth/login","{\"correo\":\"igual@example.test\",\"password\":\"Incorrecta-2026\"}",null).statusCode());
     assertEquals(204,call("fixture-lima","POST","/api/auth/logout","{}",a).statusCode());
     assertEquals(401,call("fixture-lima","GET","/api/perfil",null,a).statusCode());
     assertEquals(200,call("fixture-cusco","GET","/api/perfil",null,b).statusCode());
     assertEquals(404,call("desconocido","POST","/api/auth/login","{}",null).statusCode());
     assertTrue(java.util.Set.of(401,403).contains(call("fixture-cusco","GET","/api/superadmin/tiendas",null,b).statusCode()));
     String activeA=login("fixture-lima","Fixture-Auth-2026");
     assertEquals(202,call("fixture-lima","POST","/api/auth/password/forgot","{\"correo\":\"igual@example.test\"}",null).statusCode());
     String recovery=mailbox.get(1L);assertNotNull(recovery);
     String reset="{\"token\":\""+recovery+"\",\"passwordNueva\":\"Nueva-Fixture-2026\"}";
     assertEquals(400,call("fixture-cusco","POST","/api/auth/password/reset",reset,null).statusCode());
     assertEquals(204,call("fixture-lima","POST","/api/auth/password/reset",reset,null).statusCode());
     assertEquals(400,call("fixture-lima","POST","/api/auth/password/reset",reset,null).statusCode());
     assertEquals(401,call("fixture-lima","GET","/api/perfil",null,activeA).statusCode());
     String fresh=login("fixture-lima","Nueva-Fixture-2026");
     var claims=context.getBean(com.tiendasebas.tiendasebas.security.JwtService.class).getClaims(fresh);
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->db.update("UPDATE auth_sesiones SET creada_en=clock_timestamp()-interval '2 hours',vence_en=clock_timestamp()-interval '1 second' WHERE id=?",UUID.fromString(claims.getId())));}finally{TenantContext.clear();}
     assertEquals(401,call("fixture-lima","GET","/api/perfil",null,fresh).statusCode());
     assertEquals(202,call("fixture-lima","POST","/api/auth/password/forgot","{\"correo\":\"igual@example.test\"}",null).statusCode());
     String expired=mailbox.get(1L);
     TenantContext.setCurrentTenant(1L);try{tx.executeWithoutResult(st->db.update("UPDATE auth_recuperaciones SET vence_en=clock_timestamp()-interval '1 second' WHERE consumida_en IS NULL"));}finally{TenantContext.clear();}
     assertEquals(400,call("fixture-lima","POST","/api/auth/password/reset","{\"token\":\""+expired+"\",\"passwordNueva\":\"Otra-Fixture-2026\"}",null).statusCode());
     var spoof=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Host","desconocido.example.test").header("Forwarded","host=fixture-lima.example.test;proto=https").header("X-Forwarded-Host","fixture-lima.example.test").header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"correo\":\"igual@example.test\",\"password\":\"Nueva-Fixture-2026\"}")).build();
     assertEquals(404,java.net.http.HttpClient.newHttpClient().send(spoof,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
     String tampered=b.substring(0,b.length()-8)+"abcdefgh";assertEquals(401,call("fixture-cusco","GET","/api/superadmin/tiendas",null,tampered).statusCode());
     pool.setMaximumPoolSize(2);TenantContext.setCurrentTenant(1L);
     var rate=context.getBean(com.tiendasebas.tiendasebas.security.AuthRateLimit.class);
     try{assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(st->{rate.comprobar("nested-fixture","fixture",1);throw new IllegalStateException("rollback exterior");}));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->rate.comprobar("nested-fixture","fixture",1));}finally{TenantContext.clear();pool.setMaximumPoolSize(1);}

     assertEquals(200,call("fixture-cusco","GET","/api/perfil",null,b).statusCode());
     try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
      var f1=executor.submit(()->login("fixture-lima","Nueva-Fixture-2026"));var f2=executor.submit(()->login("fixture-cusco","Fixture-Auth-2026"));assertNotNull(f1.get());assertNotNull(f2.get());
     }
    }
   }
  }finally{TenantContext.clear();}
 }
}



