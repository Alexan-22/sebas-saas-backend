package com.tiendasebas.tiendasebas.integration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;
import static com.tiendasebas.tiendasebas.integration.IsolatedAdminBrowserSession.request;
import static com.tiendasebas.tiendasebas.integration.YapeManualHttpScenario.*;
final class TransferenciaHttpScenario {
 static final String ROOT="/api/transferencias";
 static String account(boolean enabled,String number){return "{\"moneda\":\"PEN\",\"banco\":\"BCP\",\"titular\":\"Comercio ficticio\",\"numeroCuenta\":\""+number+"\",\"cci\":\"12345678901234567890\",\"habilitado\":"+enabled+"}";}
 static String buyBody(int variant,long account,UUID key){return body(variant,key).replace("\"YAPE\"","\"TRANSFERENCIA\"").replace("\"medioPago\"","\"cuentaBancariaId\":"+account+",\"medioPago\"");}
 static void verify(ConfigurableApplicationContext ctx,int port,long tenantA,long tenantB,String adminA,String adminB,String clientA,String clientB)throws Exception{
  var db=new JdbcTemplate(ctx.getBean(javax.sql.DataSource.class));
  String url=ROOT+"/admin/cuentas/BCP";
  assertEquals(401,request(port,A,url,null,"PUT",account(true,"1111111111")).status());
  assertEquals(403,request(port,A,url,clientA,"PUT",account(true,"1111111111")).status());
  assertEquals(401,request(port,A,url,adminB,"PUT",account(true,"1111111111")).status());
  assertEquals(400,request(port,A,url,adminA,"PUT",account(true,"1111111111").replace("PEN","USD")).status());
  assertEquals(200,request(port,A,url,adminA,"PUT",account(true,"1111111111")).status());
  assertEquals(200,request(port,B,url,adminB,"PUT",account(true,"2222222222")).status());
  long bankA=db.queryForObject("SELECT id FROM transferencia_cuentas_bancarias WHERE tienda_id=?",Long.class,tenantA);
  long bankB=db.queryForObject("SELECT id FROM transferencia_cuentas_bancarias WHERE tienda_id=?",Long.class,tenantB);
  var publicA=request(port,A,ROOT+"/publico",null,"GET","");assertEquals(200,publicA.status());assertTrue(publicA.wire().contains("1111111111"));assertFalse(publicA.wire().contains("2222222222"));
  int variant=db.queryForObject("SELECT pt.id_producto_talla FROM producto_tallas pt JOIN productos p ON p.id_producto=pt.id_producto WHERE p.tienda_id=? AND p.nombre='Polo aislado 0'",Integer.class,tenantA);
  int originalStock=stock(db,variant), originalSales=db.queryForObject("SELECT count(*) FROM ventas",Integer.class);
  assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",buyBody(variant,bankB,UUID.randomUUID())).status());
  assertEquals(200,request(port,A,url,adminA,"PUT",account(false,"1111111111")).status());
  assertFalse(request(port,A,ROOT+"/publico",null,"GET","").wire().contains("1111111111"));
  assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",buyBody(variant,bankA,UUID.randomUUID())).status());
  assertEquals(originalSales,db.queryForObject("SELECT count(*) FROM ventas",Integer.class));assertEquals(originalStock,stock(db,variant));
  assertEquals(200,request(port,A,url,adminA,"PUT",account(true,"1111111111")).status());
  String body=buyBody(variant,bankA,UUID.randomUUID());
  var bought=parallel(()->request(port,A,"/api/ventas",clientA,"POST",body),()->request(port,A,"/api/ventas",clientA,"POST",body));
  assertEquals(200,bought[0].status());assertEquals(200,bought[1].status());int sale=id(bought[0].wire());assertEquals(sale,id(bought[1].wire()));assertEquals(originalStock-1,stock(db,variant));
  assertEquals(409,request(port,A,"/api/ventas",clientA,"POST",body.replace("\"cantidad\":1","\"cantidad\":2")).status());
  String mine=ROOT+"/pagos/mios/"+sale, admin=ROOT+"/pagos/admin/"+sale;
  var payment=request(port,A,mine,clientA,"GET","");assertEquals(200,payment.status());assertTrue(payment.wire().contains("PENDIENTE"));assertTrue(payment.wire().contains("1111111111"));
  assertEquals(new java.math.BigDecimal("45.00"),db.queryForObject("SELECT importe FROM transferencia_pagos_manuales WHERE id_venta=?",java.math.BigDecimal.class,sale));
  request(port,A,url,adminA,"PUT",account(true,"3333333333"));assertTrue(request(port,A,mine,clientA,"GET","").wire().contains("1111111111"));
  assertEquals(404,request(port,B,mine,clientB,"GET","").status());assertEquals(404,request(port,B,admin+"/aprobar",adminB,"POST","").status());
  assertEquals(403,request(port,A,admin+"/aprobar",clientA,"POST","").status());
  assertEquals(409,request(port,A,"/api/ventas/admin/"+sale+"/estado",adminA,"PUT","{\"estadoPedido\":\"EN_PREPARACION\"}").status());
  var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=out.toByteArray();
  assertEquals(400,upload(port,A,mine+"/comprobante",clientA,new byte[]{1,2},null).status());
  assertEquals(400,upload(port,A,mine+"/comprobante",clientA,new byte[5242881],null).status());
  assertEquals(200,upload(port,A,mine+"/comprobante",clientA,png,null).status());
  assertEquals(409,upload(port,A,mine+"/comprobante",clientA,png,null).status());
  assertEquals(200,upload(port,A,mine+"/comprobante/reemplazo",clientA,png,"Imagen mas legible").status());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM transferencia_comprobantes_historial WHERE id_venta=?",Integer.class,sale));
  assertEquals(200,request(port,A,mine+"/comprobante/imagen",clientA,"GET","").status());
  assertEquals(401,request(port,A,mine+"/comprobante/imagen",null,"GET","").status());
  assertEquals(200,request(port,A,admin+"/comprobante/imagen",adminA,"GET","").status());
  assertEquals(404,request(port,B,admin+"/comprobante/imagen",adminB,"GET","").status());
  assertEquals(404,request(port,B,mine+"/comprobante/imagen",clientB,"GET","").status());
  assertEquals("PENDIENTE",db.queryForObject("SELECT estado FROM transferencia_pagos_manuales WHERE id_venta=?",String.class,sale));
  db.update("UPDATE tiendas SET whatsapp='900000001' WHERE id=?",tenantA);
  var contact=request(port,A,mine+"/contacto",clientA,"GET","");assertEquals(200,contact.status());assertTrue(contact.wire().contains("51900000001"));assertTrue(contact.wire().contains("45.00"));
  var approved=parallel(()->request(port,A,admin+"/aprobar",adminA,"POST",""),()->request(port,A,admin+"/aprobar",adminA,"POST",""));
  assertEquals(java.util.List.of(200,409),java.util.stream.Stream.of(approved).map(IsolatedAdminBrowserSession.Reply::status).sorted().toList());
  assertTrue(request(port,A,mine,clientA,"GET","").wire().contains("APROBADO"));assertEquals(originalStock-1,stock(db,variant));
  assertEquals(409,upload(port,A,mine+"/comprobante/reemplazo",clientA,png,"Otro").status());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM transferencia_pagos_auditoria WHERE id_venta=? AND evento='APROBADO'",Integer.class,sale));
  var second=request(port,A,"/api/ventas",clientA,"POST",buyBody(variant,bankA,UUID.randomUUID()));assertEquals(200,second.status());int rejected=id(second.wire());
  String rejection=ROOT+"/pagos/admin/"+rejected+"/rechazar",reason="{\"motivo\":\"Sin ingreso en prueba\"}";
  db.execute("ALTER TABLE transferencia_pagos_auditoria ADD CONSTRAINT transferencia_fixture_failure CHECK(evento<>'RECHAZADO')");
  try{assertEquals(500,request(port,A,rejection,adminA,"POST",reason).status());}finally{db.execute("ALTER TABLE transferencia_pagos_auditoria DROP CONSTRAINT transferencia_fixture_failure");}
  assertEquals(originalStock-2,stock(db,variant));assertEquals(0,db.queryForObject("SELECT count(*) FROM transferencia_stock_reposiciones WHERE id_venta=?",Integer.class,rejected));
  assertEquals("PENDIENTE",db.queryForObject("SELECT estado FROM transferencia_pagos_manuales WHERE id_venta=?",String.class,rejected));
  var rejectedResults=parallel(()->request(port,A,rejection,adminA,"POST",reason),()->request(port,A,rejection,adminA,"POST",reason));
  assertEquals(java.util.List.of(200,409),java.util.stream.Stream.of(rejectedResults).map(IsolatedAdminBrowserSession.Reply::status).sorted().toList());assertEquals(originalStock-1,stock(db,variant));
  assertEquals("CANCELADO",db.queryForObject("SELECT estado_pedido FROM ventas WHERE id_venta=?",String.class,rejected));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM transferencia_stock_reposiciones WHERE id_venta=?",Integer.class,rejected));
  // Last unit: different keys race under real PostgreSQL variant locks.
  db.update("UPDATE producto_tallas SET stock=1 WHERE id_producto_talla=?",variant);
  var last=parallel(()->request(port,A,"/api/ventas",clientA,"POST",buyBody(variant,bankA,UUID.randomUUID())),()->request(port,A,"/api/ventas",clientA,"POST",buyBody(variant,bankA,UUID.randomUUID())));
  assertEquals(java.util.List.of(200,409),java.util.stream.Stream.of(last).map(IsolatedAdminBrowserSession.Reply::status).sorted().toList());assertEquals(0,stock(db,variant));
  // Replenish test-only stock to leave a usable browser fixture, not commercial inventory.
  db.update("UPDATE producto_tallas SET stock=20 WHERE id_producto_talla=?",variant);
 }
 static IsolatedAdminBrowserSession.Reply upload(int port,String host,String route,String token,byte[] image,String reason)throws Exception{
  String boundary="sebas-transfer-test";var body=new java.io.ByteArrayOutputStream();
  body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"fixture.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(image);body.write("\r\n".getBytes(StandardCharsets.UTF_8));
  if(reason!=null)body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"motivo\"\r\n\r\n"+reason+"\r\n").getBytes(StandardCharsets.UTF_8));
  body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
  try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(30000);String h="POST "+route+" HTTP/1.1\r\nHost: "+host+"\r\nAuthorization: Bearer "+token+"\r\nContent-Type: multipart/form-data; boundary="+boundary+"\r\nContent-Length: "+body.size()+"\r\nConnection: close\r\n\r\n";socket.getOutputStream().write(h.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().write(body.toByteArray());socket.getOutputStream().flush();String wire=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);return new IsolatedAdminBrowserSession.Reply(Integer.parseInt(wire.split(" ",3)[1]),wire);}
 }
}
