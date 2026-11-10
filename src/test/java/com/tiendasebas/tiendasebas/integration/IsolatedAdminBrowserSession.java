package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.entity.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures only for the datasource already validated by IsolatedApplicationStartupTest. */
final class IsolatedAdminBrowserSession {
    record Fixture(long tenant, int sale, UUID payment, String email) {}
    record Reply(int status, String wire) {}
    static void verify(ConfigurableApplicationContext context, int port) throws Exception {
        String password=UUID.randomUUID().toString();
        var manager=context.getBean(JpaTransactionManager.class);
        var encoder=context.getBean(PasswordEncoder.class);
        var db=new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        asegurarV5EnPostgresAislado(context);
        asegurarV7EnPostgresAislado(context);
        asegurarB223EnPostgresAislado(context);
        Fixture[] fixtures=new TransactionTemplate(manager).execute(tx -> {
            EntityManager em=EntityManagerFactoryUtils.getTransactionalEntityManager(manager.getEntityManagerFactory());
            Rol admin=new Rol(); admin.setNombreRol("ADMIN"); em.persist(admin);
            Rol client=new Rol(); client.setNombreRol("CLIENTE"); em.persist(client);
            Fixture[] result=new Fixture[2];
            for(int i=0;i<2;i++) {
                Tienda shop=new Tienda(); shop.setNombre("Tienda aislada "+i);
                shop.setSubdominio(i==0?"sebascollection":"boutiqueflorales"); em.persist(shop); em.flush();
                String email="admin"+i+"@example.test";
                user(em,admin,shop.getId(),email,encoder.encode(password));
                user(em,client,shop.getId(),"cliente"+i+"@example.test",encoder.encode(password));
                Venta sale=new Venta(); sale.setTiendaId(shop.getId()); sale.setTipoVenta(TipoVenta.ONLINE);
                sale.setMedioPago("SIMULADO"); sale.setEstado("PENDIENTE"); sale.setEstadoPedido(EstadoPedido.RECIBIDO);
                sale.setTotal(BigDecimal.TEN); sale.setFechaVenta(LocalDateTime.now()); em.persist(sale); em.flush();
                result[i]=new Fixture(shop.getId(),sale.getIdVenta(),UUID.randomUUID(),email);
            }
            return result;
        });
        for(var f:fixtures) {
            db.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor,habilitado) VALUES (?,'SANDBOX',false)",f.tenant());
            db.update("INSERT INTO pagos_simulados(id,tienda_id,id_venta,clave_idempotencia,importe,moneda,estado) VALUES (?,?,?,'browser-fixture',10,'PEN','PENDIENTE')",f.payment(),f.tenant(),f.sale());
            db.update("INSERT INTO pagos_simulados_eventos(pago_id,estado) VALUES (?,'PENDIENTE')",f.payment());
        }
        // Dos configuraciones deliberadamente diferentes para probar aislamiento por tienda.
        db.update("INSERT INTO public.yape_configuracion_tienda " +
                  "(tienda_id, habilitado, numero_yape, titular_yape, qr_url) " +
                  "VALUES (?,false,?,?,?)",
                fixtures[0].tenant(), "900000001", "Comercio A", "https://example.test/qr-a.png");
        db.update("INSERT INTO public.yape_configuracion_tienda " +
                  "(tienda_id, habilitado, numero_yape, titular_yape, qr_url) " +
                  "VALUES (?,false,?,?,?)",
                fixtures[1].tenant(), "900000002", "Comercio B", "https://example.test/qr-b.png");
        String hostA="sebascollection.saas.test",hostB="boutiqueflorales.saas.test";
        String a=login(port,hostA,fixtures[0].email(),password), b=login(port,hostB,fixtures[1].email(),password);
        String client=login(port,hostA,"cliente0@example.test",password);
        String root="/api/pagos/admin";
        // B.2.2.2: verificar ruta, rol y tenant con JWT reales de la sesion aislada.
        String pagosYape="/api/yape/pagos";
        assertEquals(401, request(port,hostA,pagosYape+"/admin",null,"GET","").status());
        assertEquals(403, request(port,hostA,pagosYape+"/admin",client,"GET","").status());
        assertEquals(401, request(port,hostA,pagosYape+"/admin",b,"GET","").status());
        assertEquals(200, request(port,hostA,pagosYape+"/admin",a,"GET","").status());
        assertEquals(200, request(port,hostB,pagosYape+"/admin",b,"GET","").status());
        assertEquals(404, request(port,hostA,pagosYape+"/mios/999999",client,"GET","").status());
        assertEquals(401,request(port,hostA,root,null,"GET","").status());
        assertEquals(401,request(port,hostA,root,"invalid","GET","").status());
        assertEquals(403,request(port,hostA,root,client,"GET","").status());
        assertEquals(401,request(port,hostA,root,b,"GET","").status());
        assertEquals(200,request(port,hostA,root+"/configuracion",a,"GET","").status());
        var page=request(port,hostA,root+"?pagina=0",a,"GET","");
        assertEquals(200,page.status()); assertTrue(page.wire().contains(fixtures[0].payment().toString())); assertFalse(page.wire().contains(fixtures[1].payment().toString()));
        assertEquals(200,request(port,hostA,root+"/"+fixtures[0].payment(),a,"GET","").status());
        assertEquals(404,request(port,hostA,root+"/"+fixtures[1].payment(),a,"GET","").status());
        var pageB=request(port,hostB,root+"?pagina=0",b,"GET","");
        assertEquals(200,pageB.status()); assertTrue(pageB.wire().contains(fixtures[1].payment().toString())); assertFalse(pageB.wire().contains(fixtures[0].payment().toString()));
        // Comprobar GET ADMIN de Yape en ambos hosts/tenants.
        String yapeRoot="/api/yape/admin/configuracion";
        assertEquals(401,request(port,hostA,yapeRoot,null,"GET","").status());
        assertEquals(403,request(port,hostA,yapeRoot,client,"GET","").status());
        assertEquals(401,request(port,hostA,yapeRoot,b,"GET","").status());
        var yapeA=request(port,hostA,yapeRoot,a,"GET","");
        assertEquals(200,yapeA.status(),"GET configuracion Yape tienda A");
        assertTrue(yapeA.wire().contains("900000001"));
        assertFalse(yapeA.wire().contains("900000002"));
        var yapeB=request(port,hostB,yapeRoot,b,"GET","");
        assertEquals(200,yapeB.status(),"GET configuracion Yape tienda B");
        assertTrue(yapeB.wire().contains("900000002"));
        assertFalse(yapeB.wire().contains("900000001"));
        // Prueba de subida QR real: autorización y almacenamiento aislado por tenant.
        byte[] imagenPrueba = qrPrueba();
        assertEquals(401, subirQr(port, hostA, null, imagenPrueba).status());
        assertEquals(403, subirQr(port, hostA, client, imagenPrueba).status());
        assertEquals(404, request(port, hostA, "/api/yape/admin/qr", a, "GET", "").status());
        assertEquals(200, subirQr(port, hostA, a, java.util.Arrays.copyOf(imagenPrueba, 2 * 1024 * 1024)).status(), "PNG exacto 2 MiB");
        assertEquals(400, subirQr(port, hostA, a, java.util.Arrays.copyOf(imagenPrueba, 2 * 1024 * 1024 + 1)).status());
        assertEquals(200, subirQr(port, hostA, a, imagenPrueba).status());
        assertEquals(200, request(port, hostA, "/api/yape/admin/qr", a, "GET", "").status());
        assertEquals(404, request(port, hostB, "/api/yape/admin/qr", b, "GET", "").status());
        assertEquals(403, request(port, hostA, "/api/yape/admin/qr", client, "GET", "").status());
        assertEquals(200, request(port, hostA, yapeRoot, a, "PUT",
                "{\"habilitado\":true,\"numeroYape\":\"900000001\",\"titularYape\":\"Comercio A\"}").status());
        assertEquals(400, request(port, hostB, yapeRoot, b, "PUT",
                "{\"habilitado\":true,\"numeroYape\":\"900000002\",\"titularYape\":\"Comercio B\"}").status());
        YapeManualHttpScenario.verify(context,port,fixtures[0].tenant(),fixtures[1].tenant(),a,b,client,
                login(port,hostB,"cliente1@example.test",password));
        // B.2.2.4 - Pruebas HTTP de transferencias bancarias.
        TransferenciaHttpScenario.verify(
                context,
                port,
                fixtures[0].tenant(),
                fixtures[1].tenant(),
                a,
                b,
                client,
                login(port, hostB, "cliente1@example.test", password)
        );
        // Fail a SELECT deliberately, only inside this disposable container, then restore access.
        db.execute("ALTER TABLE pagos_simulados RENAME TO pagos_simulados_browser_probe");
        try { assertEquals(500,request(port,hostA,root,a,"GET","").status()); }
        finally { db.execute("ALTER TABLE pagos_simulados_browser_probe RENAME TO pagos_simulados"); }
        assertEquals(200,request(port,hostA,root,a,"GET","").status());
        if(Boolean.getBoolean("sebas.browser.session")) {
            Path directory=Files.createTempDirectory("sebas-browser-");
            Path marker=directory.resolve("SESSION_ACTIVE"); Files.writeString(marker,"Disposable session; remove this file to finish.");
            Files.writeString(directory.resolve("proxy.json"),"{\"/api/**\":{\"target\":\"http://127.0.0.1:"+port+"\",\"secure\":false,\"changeOrigin\":false}}");
            Files.writeString(directory.resolve("ACCESO-LOCAL.txt"),"SOLO CONTENEDOR TEMPORAL\nADMIN A: "+fixtures[0].email()+"\nADMIN B: "+fixtures[1].email()+"\nPassword temporal: "+password+"\nFrontend: puerto 4201, dominios sebascollection.saas.test y boutiqueflorales.saas.test (resolver a 127.0.0.1).\nNo usar frontend comercial 4200.\n");
            System.out.println("Sesion aislada lista durante 30 minutos. Archivos locales: "+directory);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(30);
            try { while(Files.exists(marker) && System.nanoTime()<deadline) Thread.sleep(1000); }
            finally { Files.deleteIfExists(directory.resolve("ACCESO-LOCAL.txt")); Files.deleteIfExists(marker); }
        }
    }
    /** Solo prepara V5 dentro de la base desechable de Testcontainers. Nunca en Supabase. */
    private static void asegurarV5EnPostgresAislado(ConfigurableApplicationContext context) throws Exception {
        DataSource dataSource=context.getBean(DataSource.class);
        try (Connection conn=dataSource.getConnection()) {
            String jdbcUrl=conn.getMetaData().getURL();
            if (!jdbcUrl.matches("^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/startup_diagnostic(?:\\?.*)?$")) {
                throw new IllegalStateException("Se rechazo migracion de prueba: conexion no aislada");
            }
            boolean existe;
            try (var statement=conn.createStatement();
                 var result=statement.executeQuery(
                     "SELECT to_regclass('public.yape_configuracion_tienda') IS NOT NULL")) {
                if (!result.next()) throw new IllegalStateException("No se pudo comprobar tabla Yape");
                existe=result.getBoolean(1);
            }
            if (!existe) {
                ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V5__yape_configuracion_por_tienda.sql"));
            }
            boolean v6Existe;
            try (var statement=conn.createStatement();
                 var result=statement.executeQuery(
                     "SELECT to_regclass('public.yape_qr_imagen') IS NOT NULL")) {
                if (!result.next()) throw new IllegalStateException("No se pudo comprobar tabla de imágenes QR");
                v6Existe = result.getBoolean(1);
            }
            if (!v6Existe) {
                ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V6__yape_qr_imagen_por_tienda.sql"));
            }
        }
    }


    /** Carga V7 solamente en el contenedor startup_diagnostic local. */
    private static void asegurarV7EnPostgresAislado(ConfigurableApplicationContext context) throws Exception {
        DataSource datasource = context.getBean(DataSource.class);
        try (Connection conn = datasource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (!url.matches("^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/startup_diagnostic(?:\\?.*)?$")) {
                throw new IllegalStateException("Conexion no aislada: se rechazo V7");
            }
            boolean existe;
            try (var statement = conn.createStatement();
                 var result = statement.executeQuery("SELECT to_regclass('public.yape_pagos_manuales') IS NOT NULL")) {
                if (!result.next()) throw new IllegalStateException("No se pudo inspeccionar V7");
                existe = result.getBoolean(1);
            }
            if (!existe) {
                ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V7__yape_pagos_manuales.sql"));
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V8__yape_checkout_idempotencia.sql"));
            }

            // B.2.2.4: V9 se comprueba independientemente de V7.
            boolean v9Existe;
            try (var statement = conn.createStatement();
                 var result = statement.executeQuery(
                     "SELECT to_regclass('public.transferencia_cuentas_bancarias') IS NOT NULL")) {
                if (!result.next()) {
                    throw new IllegalStateException("No se pudo verificar V9");
                }
                v9Existe = result.getBoolean(1);
            }

            if (!v9Existe) {
                ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V9__transferencias_bancarias.sql"));
            }
        }
    }

    static void user(EntityManager em,Rol role,long tenant,String email,String hash) {
        Usuario u=new Usuario();u.setNombre("Fixture");u.setApellido("Local");u.setCorreo(email);u.setPasswordHash(hash);u.setRol(role);u.setTiendaId(tenant);em.persist(u);
    }
    static String login(int port,String host,String email,String password) throws Exception {
        var response=request(port,host,"/api/auth/login",null,"POST","{\"correo\":\""+email+"\",\"password\":\""+password+"\"}");
        assertEquals(200,response.status());
        var matcher=java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.wire());
        assertTrue(matcher.find(),"Login no devolvio token");return matcher.group(1);
    }
    private static byte[] qrPrueba() throws Exception {
        var img = new java.awt.image.BufferedImage(220, 220, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE); g.fillRect(0,0,220,220);
            g.setColor(java.awt.Color.BLACK); g.fillRect(20,20,180,180);
        } finally { g.dispose(); }
        var salida = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", salida);
        return salida.toByteArray();
    }

    private static Reply subirQr(int port, String host, String token, byte[] imagen) throws Exception {
        String frontera = "---sebas-browser-qr-aislado";
        byte[] antes = ("--"+frontera+"\r\n"+
            "Content-Disposition: form-data; name=\"archivo\"; filename=\"qr.png\"\r\n"+
            "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] despues = ("\r\n--"+frontera+"--\r\n").getBytes(StandardCharsets.US_ASCII);
        var carga = new java.io.ByteArrayOutputStream();
        carga.write(antes); carga.write(imagen); carga.write(despues);
        byte[] datos = carga.toByteArray();
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(30000);
            String cabeceras = "POST /api/yape/admin/qr HTTP/1.1\r\nHost: "+host+
                "\r\nContent-Type: multipart/form-data; boundary="+frontera+
                "\r\nContent-Length: "+datos.length+"\r\nConnection: close\r\n"+
                (token == null ? "" : "Authorization: Bearer "+token+"\r\n")+"\r\n";
            socket.getOutputStream().write(cabeceras.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(datos);
            socket.getOutputStream().flush();
            String respuesta = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Reply(Integer.parseInt(respuesta.split(" ", 3)[1]), respuesta);
        }
    }

    static Reply request(int port,String host,String path,String token,String method,String json) throws Exception {
        try(var socket=new Socket("127.0.0.1",port)) {
            socket.setSoTimeout(30000);byte[] body=json.getBytes(StandardCharsets.UTF_8);
            String headers=method+" "+path+" HTTP/1.1\r\nHost: "+host+"\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n"+(token==null?"":"Authorization: Bearer "+token+"\r\n")+"\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
            String wire=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            return new Reply(Integer.parseInt(wire.split(" ",3)[1]),wire);
        }
    }

    /** B223: SOLO PostgreSQL Testcontainers startup_diagnostic local. */
    private static void asegurarB223EnPostgresAislado(ConfigurableApplicationContext context) throws Exception {
        DataSource ds = context.getBean(DataSource.class);
        try (Connection conn = ds.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (!url.matches("^jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/startup_diagnostic([?].*)?$")) {
                throw new IllegalStateException("Se rechazo B223 fuera de Postgres aislado: " + url);
            }
            boolean existe;
            try (var st = conn.createStatement();
                 var rs = st.executeQuery("SELECT to_regclass('public.yape_comprobantes') IS NOT NULL")) {
                if (!rs.next()) throw new IllegalStateException("No se pudo inspeccionar B223");
                existe = rs.getBoolean(1);
            }
            if (!existe) {
                ScriptUtils.executeSqlScript(conn,
                        new ClassPathResource("isolated-only/B223__yape_comprobantes.sql"));
            }
        }
    }

}
