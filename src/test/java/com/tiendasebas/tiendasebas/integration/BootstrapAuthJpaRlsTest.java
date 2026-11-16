package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapAuthJpaRlsTest {
 static javax.sql.DataSource source;
 @Configuration @EnableTransactionManagement @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.tiendasebas.tiendasebas.repository")
 @Import({com.tiendasebas.tiendasebas.service.AuthService.class,com.tiendasebas.tiendasebas.security.JwtService.class,com.tiendasebas.tiendasebas.security.SesionService.class,com.tiendasebas.tiendasebas.security.AuthRateLimit.class})
 static class Config{
  @Bean javax.sql.DataSource dataSource(){return source;}
  @Bean org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(){return new org.springframework.jdbc.core.JdbcTemplate(source);}
  @Bean org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean entityManagerFactory(){var f=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();f.setDataSource(source);f.setPackagesToScan("com.tiendasebas.tiendasebas.entity");f.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
  @Bean com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager(f,source);}
  @Bean org.springframework.security.crypto.password.PasswordEncoder passwords(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();}
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
   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_auth_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    try(var context=new AnnotationConfigApplicationContext()){
     context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("fixture",Map.of("app.jwt.secret",UUID.randomUUID().toString()+UUID.randomUUID(),"app.jwt.expiration","600000","app.rls.auth-enabled","true")));
     context.register(Config.class);context.refresh();
     var auth=context.getBean(com.tiendasebas.tiendasebas.service.AuthService.class);var sessions=context.getBean(com.tiendasebas.tiendasebas.security.SesionService.class);var jwt=context.getBean(com.tiendasebas.tiendasebas.security.JwtService.class);var users=context.getBean(com.tiendasebas.tiendasebas.repository.UsuarioRepository.class);var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager.class));
     var request=new com.tiendasebas.tiendasebas.dto.LoginRequest();request.setCorreo("igual@example.test");request.setPassword("Fixture-Auth-2026");
     assertEquals("sebas_auth_app",db.queryForObject("SELECT current_user",String.class));
     String tokenA=null;
     for(long tenant:List.of(1L,2L)){
      TenantContext.setCurrentTenant(tenant);var response=auth.login(request);String token=response.getToken();if(tenant==1)tokenA=token;
      var claims=jwt.getClaims(token);var user=users.findByCorreoAndTiendaId(request.getCorreo(),tenant).orElseThrow();
      assertTrue(jwt.sesionVigente(claims,user));
      sessions.revocar(claims.getId());assertFalse(jwt.sesionVigente(claims,user));
      var exp=jwt.getClaims(auth.login(request).getToken());tx.executeWithoutResult(st->db.update("UPDATE auth_sesiones SET creada_en=clock_timestamp()-interval '2 hours',vence_en=clock_timestamp()-interval '1 second' WHERE id=?",UUID.fromString(exp.getId())));assertFalse(jwt.sesionVigente(exp,user));
      request.setPassword("Incorrecta-2026");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.login(request));request.setPassword("Fixture-Auth-2026");
      assertEquals(1,users.findAll().size());
      assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(st->{var u=users.findAll().getFirst();u.setNombre("ROLLBACK");users.saveAndFlush(u);throw new IllegalStateException("fixture rollback");}));assertEquals("Fixture",users.findAll().getFirst().getNombre());
      var foreign=jwt.getClaims(token);TenantContext.setCurrentTenant(tenant==1?2L:1L);assertFalse(sessions.validar(foreign.getId(),user.getIdUsuario(),tenant));TenantContext.clear();
     }
     assertEquals(0,users.findAll().size());
     TenantContext.setCurrentTenant(1L);
     tx.executeWithoutResult(st->{db.update("UPDATE producto_tallas SET stock=11 WHERE id_producto=1");});
     assertThrows(org.springframework.dao.DataAccessException.class,()->tx.executeWithoutResult(st->db.update("UPDATE producto_tallas SET id_producto=2 WHERE id_producto=1")));
     TenantContext.clear();
    }
   }
  }finally{TenantContext.clear();}
 }
}

