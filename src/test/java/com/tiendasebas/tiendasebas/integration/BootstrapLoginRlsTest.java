package com.tiendasebas.tiendasebas.integration;
import org.junit.jupiter.api.Test;import java.sql.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.tiendasebas.tiendasebas.config.TenantContext;import com.tiendasebas.tiendasebas.service.CatalogoRlsLocalService;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapLoginRlsTest {
 static javax.sql.DataSource source;
 @Configuration @EnableTransactionManagement static class Config{
  @Bean javax.sql.DataSource dataSource(){return source;}
  @Bean org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(){return new org.springframework.jdbc.core.JdbcTemplate(source);}
  @Bean org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager(){return new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);}
  @Bean CatalogoRlsLocalService servicio(org.springframework.jdbc.core.JdbcTemplate db){return new CatalogoRlsLocalService(db);}
 }
 @Test void loginRealPoolRollbackYConcurrencia()throws Exception{
  try(var pg=new ComercialPostgresFixture()){
   pg.start();String password=UUID.randomUUID().toString();
   try(var c=DriverManager.getConnection(pg.verifiedUrl(),pg.getUsername(),pg.getPassword())){
    c.createStatement().execute("CREATE ROLE fixture_migrador NOLOGIN NOSUPERUSER NOBYPASSRLS; CREATE ROLE sebas_catalogo_app LOGIN PASSWORD '"+password+"' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE; CREATE ROLE anon; CREATE ROLE authenticated; GRANT USAGE,CREATE ON SCHEMA public TO fixture_migrador; SET ROLE fixture_migrador;");
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B000__historico_vacio.sql"));
    for(int n=1;n<=22;n++){final String prefix="V"+n+"__";try(var files=Files.list(Path.of("src/main/resources/db/"+(n<=12?"migration":"manual")))){BootstrapVacioPostgresTest.ejecutar(c,files.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow());}}
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B023__cerrar_acceso_publico.sql"));
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("docs/staging-bootstrap/fixtures-boutiques.sql"));
    c.createStatement().execute("INSERT INTO generos(nombre_genero) VALUES('Fixture'); INSERT INTO categorias(nombre_categoria,estado,tienda_id) SELECT 'Fixture',true,id FROM tiendas; INSERT INTO productos(nombre,precio,id_genero,id_categoria,tienda_id) SELECT 'Producto '||tienda_id,10,1,id_categoria,tienda_id FROM categorias; INSERT INTO producto_tallas(id_producto,stock) SELECT id_producto,10 FROM productos;");
    BootstrapVacioPostgresTest.ejecutar(c,Path.of("src/main/resources/db/bootstrap/B024__catalogo_rls_local.sql"));
   }
   try(var pool=new com.zaxxer.hikari.HikariDataSource()){
    pool.setJdbcUrl(pg.verifiedUrl());pool.setUsername("sebas_catalogo_app");pool.setPassword(password);pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);source=pool;
    try(var context=new AnnotationConfigApplicationContext(Config.class)){
     var svc=context.getBean(CatalogoRlsLocalService.class);var db=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
     assertEquals("sebas_catalogo_app",db.queryForObject("SELECT current_user",String.class));
     assertEquals(0,db.queryForObject("SELECT count(*) FROM productos",Integer.class));
     assertEquals(0,db.queryForObject("SELECT count(*) FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls)",Integer.class));
     for(long tenant:List.of(1L,2L)){TenantContext.setCurrentTenant(tenant);try{assertEquals(1,svc.productos().size());assertEquals(tenant,((Number)svc.productos().getFirst().get("tienda_id")).longValue());assertEquals(1,svc.stock().size());svc.categoria("Propia");assertThrows(IllegalStateException.class,svc::rollback);}finally{TenantContext.clear();}}
     assertEquals(0,db.queryForObject("SELECT count(*) FROM productos",Integer.class));
     assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO categorias(nombre_categoria,estado,tienda_id) VALUES('Cruzada',true,2)"));
     assertThrows(org.springframework.dao.DataAccessException.class,()->db.queryForList("SELECT * FROM usuarios"));
     var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.jdbc.datasource.DataSourceTransactionManager.class));
     tx.executeWithoutResult(s->{db.queryForObject("SELECT set_config('app.tenant_id','1',true)",String.class);assertEquals(0,db.queryForObject("SELECT count(*) FROM categorias WHERE nombre_categoria='REVERTIR'",Integer.class));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO categorias(nombre_categoria,estado,tienda_id) VALUES('Cruzada',true,2)"));s.setRollbackOnly();});
     try(var executor=Executors.newFixedThreadPool(2)){var futures=new ArrayList<Future<?>>();for(long tenant:List.of(1L,2L))futures.add(executor.submit(()->{for(int i=0;i<8;i++){TenantContext.setCurrentTenant(tenant);try{assertEquals(tenant,((Number)svc.productos().getFirst().get("tienda_id")).longValue());}finally{TenantContext.clear();}}}));for(var future:futures)future.get(30,TimeUnit.SECONDS);}
     assertEquals(0,db.queryForObject("SELECT count(*) FROM productos",Integer.class));
    }
   }
  }finally{TenantContext.clear();}
 }
}
