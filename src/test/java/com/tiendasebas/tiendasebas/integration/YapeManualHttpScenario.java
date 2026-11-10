package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.entity.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tiendasebas.tiendasebas.integration.IsolatedAdminBrowserSession.request;

/** Real HTTP/JWT/host/JPA scenario, invoked only from the guarded Testcontainers startup test. */
final class YapeManualHttpScenario {
    static final String A="sebascollection.saas.test", B="boutiqueflorales.saas.test";
    static void verify(ConfigurableApplicationContext context,int port,long tenantA,long tenantB,
                       String adminA,String adminB,String clientA,String clientB) throws Exception {
        var db=new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        var manager=context.getBean(JpaTransactionManager.class);
        int[] variants=new TransactionTemplate(manager).execute(tx -> {
            EntityManager em=EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            Genero genero=new Genero();genero.setNombreGenero("Fixture Yape");em.persist(genero);
            Talla talla=new Talla();talla.setNombreTalla("M");em.persist(talla);
            int[] ids=new int[2];
            for(int i=0;i<2;i++) {
                long tenant=i==0?tenantA:tenantB;
                Categoria category=new Categoria();category.setNombreCategoria("Prueba Yape "+i);category.setTiendaId(tenant);em.persist(category);
                Producto product=new Producto();product.setNombre("Polo aislado "+i);product.setDescripcion("Producto ficticio, sin cobros reales");
                product.setTiendaId(tenant);product.setCategoria(category);product.setGenero(genero);product.setPrecio(new BigDecimal("35.00"));
                product.setImagen("/uploads/yape-fixture.png");em.persist(product);
                ProductoTalla variant=new ProductoTalla();variant.setProducto(product);variant.setTalla(talla);variant.setStock(20);em.persist(variant);em.flush();ids[i]=variant.getIdProductoTalla();
            }
            return ids;
        });
        var image=new java.awt.image.BufferedImage(200,200,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();try { graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,0,200,200); }finally{graphics.dispose();}
        javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(context.getEnvironment().getRequiredProperty("app.upload.dir"),"yape-fixture.png").toFile());
        var catalog=request(port,A,"/api/productos/public",null,"GET","");
        assertEquals(200,catalog.status());assertTrue(catalog.wire().contains("Polo aislado 0"));assertFalse(catalog.wire().contains("Polo aislado 1"));
        String body=body(variants[0],UUID.randomUUID());
        // Two independent HTTP requests and DB transactions retry the same order.
        var replies=parallel(() -> request(port,A,"/api/ventas",clientA,"POST",body),
                             () -> request(port,A,"/api/ventas",clientA,"POST",body));
        assertEquals(200,replies[0].status());assertEquals(200,replies[1].status());
        int sale=id(replies[0].wire());assertEquals(sale,id(replies[1].wire()));
        assertEquals(19,stock(db,variants[0]));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_pagos_manuales WHERE tienda_id=?",Integer.class,tenantA));
        assertEquals(new BigDecimal("45.00"),db.queryForObject("SELECT importe FROM yape_pagos_manuales WHERE id_venta=?",BigDecimal.class,sale));
        assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",body.replace("\"cantidad\":1","\"cantidad\":2")).status());
        String customer="/api/yape/pagos/mios/"+sale, admin="/api/yape/pagos/admin/"+sale;
        assertTrue(request(port,A,customer,clientA,"GET","").wire().contains("PENDIENTE"));
        assertEquals(200,request(port,A,customer+"/qr",clientA,"GET","").status());
        assertEquals(401,request(port,A,customer,null,"GET","").status());
        assertEquals(403,request(port,A,admin+"/aprobar",clientA,"POST","").status());
        assertEquals(401,request(port,A,admin+"/aprobar",adminB,"POST","").status());
        assertEquals(404,request(port,B,admin+"/aprobar",adminB,"POST","").status());
        assertEquals(404,request(port,B,customer,clientB,"GET","").status());
        assertEquals(409,request(port,A,"/api/ventas/admin/"+sale+"/estado",adminA,"PUT","{\"estadoPedido\":\"EN_PREPARACION\"}").status());
        var approved=parallel(() -> request(port,A,admin+"/aprobar",adminA,"POST",""),
                              () -> request(port,A,admin+"/aprobar",adminA,"POST",""));
        assertEquals(java.util.List.of(200,409),java.util.stream.Stream.of(approved).map(IsolatedAdminBrowserSession.Reply::status).sorted().toList());
        assertTrue(request(port,A,customer,clientA,"GET","").wire().contains("APROBADO"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_pagos_auditoria WHERE id_venta=? AND evento='APROBADO'",Integer.class,sale));
        assertEquals(19,stock(db,variants[0]));
        assertEquals(200,request(port,A,admin+"/auditoria",adminA,"GET","").status());
        assertEquals(200,request(port,A,"/api/ventas/admin/"+sale+"/estado",adminA,"PUT","{\"estadoPedido\":\"EN_PREPARACION\"}").status());
        // A failed rejection audit must roll back payment, sale, stock and replenishment ledger.
        int rejected=buy(port,clientA,variants[0]);assertEquals(18,stock(db,variants[0]));
        db.execute("ALTER TABLE yape_pagos_auditoria ADD CONSTRAINT yape_fixture_reject_failure CHECK (evento <> 'RECHAZADO')");
        try { assertEquals(500,reject(port,adminA,rejected).status()); }
        finally { db.execute("ALTER TABLE yape_pagos_auditoria DROP CONSTRAINT yape_fixture_reject_failure"); }
        assertEquals(18,stock(db,variants[0]));
        assertEquals("PENDIENTE",db.queryForObject("SELECT estado FROM yape_pagos_manuales WHERE id_venta=?",String.class,rejected));
        assertEquals("RECIBIDO",db.queryForObject("SELECT estado_pedido FROM ventas WHERE id_venta=?",String.class,rejected));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM yape_inventario_reposiciones WHERE id_venta=?",Integer.class,rejected));
        assertEquals(200,reject(port,adminA,rejected).status());assertEquals(19,stock(db,variants[0]));
        assertEquals(409,reject(port,adminA,rejected).status());assertEquals(19,stock(db,variants[0]));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_inventario_reposiciones WHERE id_venta=?",Integer.class,rejected));
        int race=buy(port,clientA,variants[0]);
        var raced=parallel(() -> request(port,A,"/api/yape/pagos/admin/"+race+"/aprobar",adminA,"POST",""),()->reject(port,adminA,race));
        assertEquals(java.util.List.of(200,409),java.util.stream.Stream.of(raced).map(IsolatedAdminBrowserSession.Reply::status).sorted().toList());
        String state=db.queryForObject("SELECT estado FROM yape_pagos_manuales WHERE id_venta=?",String.class,race);
        assertEquals("APROBADO".equals(state)?18:19,stock(db,variants[0]));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM yape_pagos_auditoria WHERE id_venta=? AND evento<>'CREADO'",Integer.class,race));
        int before=stock(db,variants[0]);
        int count=db.queryForObject("SELECT count(*) FROM ventas",Integer.class);
        assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",body(variants[1],UUID.randomUUID())).status());
        db.update("UPDATE yape_configuracion_tienda SET habilitado=false WHERE tienda_id=?",tenantA);
        try { assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",body(variants[0],UUID.randomUUID())).status()); }
        finally { db.update("UPDATE yape_configuracion_tienda SET habilitado=true WHERE tienda_id=?",tenantA); }
        assertEquals(count,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));assertEquals(before,stock(db,variants[0]));assertEquals(20,stock(db,variants[1]));
    }
    static int stock(JdbcTemplate db,int id){return db.queryForObject("SELECT stock FROM producto_tallas WHERE id_producto_talla=?",Integer.class,id);}
    static String body(int variant,UUID key){return "{\"claveIdempotencia\":\""+key+"\",\"medioPago\":\"YAPE\",\"items\":[{\"idProductoTalla\":"+variant+",\"cantidad\":1}],\"direccionEnvio\":{\"departamento\":\"Lima\",\"provincia\":\"Lima\",\"distrito\":\"Lima\",\"direccionExacta\":\"Calle de prueba 123\",\"referencia\":\"Fixture\"}}";}
    static int id(String wire){var m=java.util.regex.Pattern.compile("\"idVenta\"\\s*:\\s*([0-9]+)").matcher(wire);assertTrue(m.find(),"Respuesta sin ID de pedido");return Integer.parseInt(m.group(1));}
    static int buy(int port,String client,int variant)throws Exception{var r=request(port,A,"/api/ventas",client,"POST",body(variant,UUID.randomUUID()));assertEquals(200,r.status());return id(r.wire());}
    static IsolatedAdminBrowserSession.Reply reject(int port,String admin,int sale)throws Exception{return request(port,A,"/api/yape/pagos/admin/"+sale+"/rechazar",admin,"POST","{\"motivo\":\"Ingreso no recibido en prueba\"}");}
    static IsolatedAdminBrowserSession.Reply[] parallel(Callable<IsolatedAdminBrowserSession.Reply> a,Callable<IsolatedAdminBrowserSession.Reply> b)throws Exception{
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var f=pool.submit(()->{start.await();return a.call();});var g=pool.submit(()->{start.await();return b.call();});start.countDown();
            return new IsolatedAdminBrowserSession.Reply[]{f.get(45,TimeUnit.SECONDS),g.get(45,TimeUnit.SECONDS)};
        }
    }
}
