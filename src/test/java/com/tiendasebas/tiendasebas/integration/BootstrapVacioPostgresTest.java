package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.nio.file.*;import java.util.*;import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapVacioPostgresTest {
 static javax.sql.DataSource source;
 @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
 @org.springframework.context.annotation.ComponentScan(basePackages="com.tiendasebas.tiendasebas",excludeFilters=@org.springframework.context.annotation.ComponentScan.Filter(type=org.springframework.context.annotation.FilterType.REGEX,pattern=".*(integration|Test|TiendaSebasApplication).*"))
 static class App {
  @org.springframework.context.annotation.Bean javax.sql.DataSource dataSource(){return source;}
  @org.springframework.context.annotation.Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(source);f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
  @org.springframework.context.annotation.Bean org.springframework.orm.jpa.JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new org.springframework.orm.jpa.JpaTransactionManager(f);}
 }

 @Test void inicializaDesdeCeroYValidaHibernate() throws Exception {
  try(var pg=new ComercialPostgresFixture()){
   pg.start();var ds=new DriverManagerDataSource(pg.verifiedUrl(),pg.getUsername(),pg.getPassword());
   try(var c=ds.getConnection()){
    assertEquals(0,scalar(c,"SELECT count(*) FROM pg_tables WHERE schemaname='public'"));
    ejecutar(c,Path.of("src/main/resources/db/bootstrap/B000__historico_vacio.sql"));
    c.createStatement().execute("CREATE ROLE anon; CREATE ROLE authenticated;");
    int count=0;
    for(int version=1;version<=22;version++){
     final String prefix="V"+version+"__";Path folder=Path.of("src/main/resources/db/"+(version<=12?"migration":"manual"));
     Path file;try(var list=Files.list(folder)){file=list.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();}
     ejecutar(c,file);count++;System.out.println("BOOTSTRAP OK "+file.getFileName());
    }
    assertEquals(22,count);
    ejecutar(c,Path.of("src/main/resources/db/bootstrap/B023__cerrar_acceso_publico.sql"));
    assertEquals(0,scalar(c,"SELECT count(*) FROM pg_tables WHERE schemaname='public' AND NOT rowsecurity"));
    assertEquals(0,scalar(c,"SELECT count(*) FROM information_schema.role_table_grants WHERE grantee IN ('anon','authenticated','PUBLIC') AND table_schema='public'"));
   }
   var emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(ds);emf.setPackagesToScan("com.tiendasebas.tiendasebas.entity");emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
   emf.afterPropertiesSet();try {assertEquals(17,emf.getObject().getMetamodel().getEntities().size());}finally{emf.destroy();}
   try(var c=ds.getConnection()){
    ejecutar(c,Path.of("docs/staging-bootstrap/fixtures-boutiques.sql"));
    assertEquals(2,scalar(c,"SELECT count(*) FROM tiendas"));
    assertEquals(2,scalar(c,"SELECT count(DISTINCT color_primario) FROM tiendas"));
    c.createStatement().execute("INSERT INTO roles(nombre_rol) VALUES('ADMIN'),('EMPLEADO'),('CLIENTE'),('SUPER_ADMIN');");
    String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
    try(var insert=c.prepareStatement("INSERT INTO usuarios(nombre,apellido,correo,password_hash,id_rol,tienda_id) SELECT 'Fixture','Local','admin@example.test',?,r.id_rol,t.id FROM roles r CROSS JOIN tiendas t WHERE r.nombre_rol='ADMIN'")){insert.setString(1,hash);assertEquals(2,insert.executeUpdate());}
    assertEquals(2,scalar(c,"SELECT count(*) FROM usuarios WHERE correo='admin@example.test'"));
    c.createStatement().execute("INSERT INTO generos(nombre_genero) VALUES('Fixture'); INSERT INTO categorias(nombre_categoria,tienda_id) SELECT 'Fixture',id FROM tiendas;");
    c.createStatement().execute("INSERT INTO productos(nombre,precio,id_genero,id_categoria,tienda_id) SELECT 'Producto ficticio',10,g.id_genero,c.id_categoria,c.tienda_id FROM categorias c CROSS JOIN generos g;");
    assertEquals(2,scalar(c,"SELECT count(DISTINCT tienda_id) FROM productos"));
    var userA=c.createStatement();
    userA.execute("UPDATE usuarios SET password_hash='"+new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Fixture-Bootstrap-2026")+"'");
    source=ds;var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);env.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    var app=new org.springframework.boot.SpringApplication(App.class);app.setEnvironment(env);
    try(var context=app.run("--spring.config.location=optional:classpath:/bootstrap-no-config/","--server.address=127.0.0.1","--server.port=0","--spring.sql.init.mode=never","--app.jwt.secret="+UUID.randomUUID()+UUID.randomUUID(),"--app.jwt.expiration=600000","--app.cors.allowed-origins=http://fixture.test","--app.tenant.base-domain=example.test","--app.upload.dir=target/bootstrap-uploads","--app.storage.provider=local","--logging.level.root=WARN")){
     int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
     var http=java.net.http.HttpClient.newHttpClient();
     String token=null;
     for(String tenant:List.of("fixture-lima","fixture-cusco")){
      var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Host",tenant+".example.test").header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"correo\":\"admin@example.test\",\"password\":\"Fixture-Bootstrap-2026\"}")).build();
      var response=http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode(),response.body());
      var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
      String current=json.get("token").asText();if(token==null)token=current;
      var config=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/v1/tiendas/config")).header("Host",tenant+".example.test").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,config.statusCode());assertTrue(config.body().contains(tenant));
     }
     var cross=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/ventas/admin")).header("Host","fixture-cusco.example.test").header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertTrue(cross.statusCode()==401||cross.statusCode()==403);
     assertEquals(2,context.getBean(com.tiendasebas.tiendasebas.repository.ProductoRepository.class).count());
    }



   }
  }
 }
 static long scalar(Connection c,String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
 static void ejecutar(Connection c,Path file)throws Exception{
  String sql=Files.readString(file).replace("\uFEFF","");
  c.setAutoCommit(false);try(var s=c.createStatement()){s.execute(sql);c.commit();}catch(Exception ex){c.rollback();throw new IllegalStateException("Falló "+file,ex);}finally{c.setAutoCommit(true);}
 }
}
