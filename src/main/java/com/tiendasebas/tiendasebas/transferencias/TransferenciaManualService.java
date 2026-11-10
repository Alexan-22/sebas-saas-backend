package com.tiendasebas.tiendasebas.transferencias;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.CompraRequest;
import com.tiendasebas.tiendasebas.dto.CompraResponse;
import com.tiendasebas.tiendasebas.entity.DetalleVenta;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.repository.DetalleVentaRepository;
import com.tiendasebas.tiendasebas.repository.ProductoTallaRepository;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Transferencia bancaria manual: nunca valida un deposito de forma automatica. */
@Service
public class TransferenciaManualService {
    private final JdbcTemplate jdbc;
    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final ProductoTallaRepository inventario;

    public TransferenciaManualService(JdbcTemplate jdbc, VentaRepository ventas,
                                      DetalleVentaRepository detalles,
                                      ProductoTallaRepository inventario) {
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.detalles = detalles;
        this.inventario = inventario;
    }

    /** The transaction lock is held until VentaService commits, including stock and audit. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CompraResponse recuperarCompra(CompraRequest request, Usuario usuario) {
        long tienda = tenant();
        if (!tiendaIdEquals(usuario.getTiendaId(), tienda)) throw conflicto("Usuario de otra tienda");
        java.util.UUID clave = clave(request);
        String lock = tienda + ":" + usuario.getIdUsuario() + ":" + clave;
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, lock);
        record Anterior(String huella, CompraResponse respuesta) {}
        var anteriores = jdbc.query("""
                SELECT c.huella, v.id_venta, v.subtotal, v.costo_delivery, v.total, v.estado, v.medio_pago
                FROM public.transferencia_checkout_reintentos c
                JOIN public.ventas v ON v.tienda_id=c.tienda_id AND v.id_venta=c.id_venta
                WHERE c.tienda_id=? AND c.usuario_id=? AND c.clave=?
                """, (rs, row) -> new Anterior(rs.getString(1), new CompraResponse(rs.getInt(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6),
                rs.getString(7), "Pedido de transferencia ya registrado")), tienda, usuario.getIdUsuario(), clave);
        if (anteriores.isEmpty()) return null;
        if (!anteriores.getFirst().huella().equals(huella(request)))
            throw conflicto("La clave ya pertenece a otro contenido de compra");
        return anteriores.getFirst().respuesta();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordarCompra(CompraRequest request, Usuario usuario, Venta venta) {
        long tienda = tenant();
        if (!tiendaIdEquals(usuario.getTiendaId(), tienda) || !tiendaIdEquals(venta.getTiendaId(), tienda))
            throw conflicto("Pedido de otra tienda");
        jdbc.update("""
                INSERT INTO public.transferencia_checkout_reintentos(tienda_id,usuario_id,clave,huella,id_venta)
                VALUES (?,?,?,?,?)
                """, tienda, usuario.getIdUsuario(), clave(request), huella(request), venta.getIdVenta());
    }

    private static java.util.UUID clave(CompraRequest request) {
        try {
            String raw = request.getClaveIdempotencia();
            java.util.UUID id = java.util.UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transferencia requiere claveIdempotencia UUID");
        }
    }

    static String huella(CompraRequest request) {
        var d = request.getDireccionEnvio();
        StringBuilder text = new StringBuilder();
        text.append(request.getCuentaBancariaId()).append(';');
        request.getItems().forEach(i -> text.append(i.getIdProductoTalla()).append(':').append(i.getCantidad()).append(';'));
        for (String value : new String[]{request.getMedioPago(), d == null ? null : d.getDepartamento(),
                d == null ? null : d.getProvincia(), d == null ? null : d.getDistrito(),
                d == null ? null : d.getDireccionExacta(), d == null ? null : d.getReferencia()}) {
            text.append(value == null ? -1 : value.length()).append(':').append(value).append(';');
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static boolean tiendaIdEquals(Long actual,long expected){return actual!=null && actual==expected;}

    public record Cuenta(Long id, String moneda, String banco, String titular, String numeroCuenta,
                         String cci, boolean habilitado) {}
    public record Pago(int idVenta, String estado, java.math.BigDecimal importe,
                       String moneda, String banco, String titular,
                       String numeroCuenta, String cci, String correoCliente,
                       Instant creadoEn, String verificadoPor, Instant verificadoEn,
                       String motivoRechazo) {}
    public record Evento(Instant ocurridoEn, String evento, String estadoAnterior,
                         String estadoNuevo, String actorCorreo, String observacion) {}
    public record ComprobanteInfo(int idVenta, String mimeTipo, int tamanoBytes,
                                  String sha256, Instant enviadoEn) {}
    public record Imagen(byte[] bytes, String mime) {}
    public record Whatsapp(String numeroWhatsapp, String url) {}
    record Limpiada(byte[] bytes, String mime) {}

    private static long tenant() {
        Long t = TenantContext.getCurrentTenant();
        if (t == null || t <= 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no identificada");
        return t;
    }
    private static ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }
    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }
    private static String bancoValido(String banco) {
        if (banco == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un banco");
        String b = banco.strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("BCP", "BBVA", "INTERBANK", "SCOTIABANK").contains(b)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banco no admitido");
        }
        return b;
    }

    private static final String CUENTAS = """
            SELECT banco,titular,numero_cuenta,cci,habilitado,id,moneda
            FROM public.transferencia_cuentas_bancarias
            """;

    @Transactional(readOnly = true)
    public List<Cuenta> cuentasPublicas() {
        return jdbc.query(CUENTAS + " WHERE tienda_id=? AND habilitado=true ORDER BY banco",
                (r,i) -> new Cuenta(r.getLong(6), r.getString(7), r.getString(1), r.getString(2), r.getString(3), r.getString(4),r.getBoolean(5)), tenant());
    }
    @Transactional(readOnly = true)
    public List<Cuenta> cuentasAdmin() {
        return jdbc.query(CUENTAS + " WHERE tienda_id=? ORDER BY banco",
                (r,i) -> new Cuenta(r.getLong(6), r.getString(7), r.getString(1), r.getString(2), r.getString(3), r.getString(4),r.getBoolean(5)), tenant());
    }
    @Transactional
    public Cuenta guardarCuenta(String banco, Cuenta entrada) {
        long t = tenant();
        String b = bancoValido(banco);
        if (entrada == null || !"PEN".equals(entrada.moneda()) || entrada.titular()==null || entrada.titular().strip().length()<2
                || entrada.titular().strip().length()>140 || entrada.numeroCuenta()==null
                || !entrada.numeroCuenta().matches("[0-9]{7,30}")
                || (entrada.cci()!=null && !entrada.cci().isBlank() && !entrada.cci().matches("[0-9]{20}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cuenta, titular o CCI incorrectos");
        }
        jdbc.update("""
                INSERT INTO public.transferencia_cuentas_bancarias
                (tienda_id,banco,titular,numero_cuenta,cci,habilitado)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (tienda_id,banco) DO UPDATE SET
                  titular=excluded.titular, numero_cuenta=excluded.numero_cuenta,
                  cci=excluded.cci, habilitado=excluded.habilitado, actualizado_en=now()
                """, t,b,entrada.titular().strip(),entrada.numeroCuenta(),
                entrada.cci()==null || entrada.cci().isBlank()?null:entrada.cci(),entrada.habilitado());
        return jdbc.query(CUENTAS+" WHERE tienda_id=? AND banco=?",
                (r,i)->new Cuenta(r.getLong(6), r.getString(7), r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5)),t,b).getFirst();
    }
    private Cuenta cuentaDisponible(Long cuentaId) {
        if(cuentaId==null || cuentaId<=0) throw conflicto("Selecciona una cuenta bancaria");
        List<Cuenta> cuentas = jdbc.query(CUENTAS+" WHERE tienda_id=? AND id=? AND habilitado=true AND moneda='PEN' FOR SHARE",
                (r,i)->new Cuenta(r.getLong(6), r.getString(7), r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5)),tenant(),cuentaId);
        if(cuentas.isEmpty()) throw conflicto("No hay cuenta activa del banco elegido en esta tienda");
        return cuentas.getFirst();
    }
    @Transactional(readOnly = true)
    public void validarHabilitado(Long cuentaId) { cuentaDisponible(cuentaId); }

    /** Invocar dentro de la misma transaccion en VentaService tras saveAndFlush. */
    @Transactional
    public void registrarPendiente(Venta venta, Usuario usuario, Long cuentaId) {
        long t=tenant();
        if(venta==null || usuario==null || venta.getIdVenta()==null || venta.getTotal()==null
                || venta.getTotal().signum()<=0 || !"TRANSFERENCIA".equals(venta.getMedioPago())
                || venta.getTiendaId()==null || venta.getTiendaId()!=t
                || usuario.getTiendaId()==null || usuario.getTiendaId()!=t) {
            throw conflicto("Venta bancaria invalida");
        }
        Cuenta c=cuentaDisponible(cuentaId);
        List<Long> ids = jdbc.query("""
                INSERT INTO public.transferencia_pagos_manuales
                  (tienda_id,id_venta,usuario_id,correo_cliente,importe,banco,titular,numero_cuenta,cci,cuenta_bancaria_id)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,(r,i)->r.getLong(1), t,venta.getIdVenta(),usuario.getIdUsuario(),usuario.getCorreo(),
                venta.getTotal(),c.banco(),c.titular(),c.numeroCuenta(),c.cci(),c.id());
        if(ids.size()!=1) throw conflicto("No se pudo registrar la transferencia");
        evento(t,ids.getFirst(),venta.getIdVenta(),"CREADO",null,"PENDIENTE",usuario.getCorreo(),null);
    }
    private void evento(long t,long pago,int idVenta,String tipo,String anterior,String nuevo,String actor,String nota) {
        jdbc.update("""
            INSERT INTO public.transferencia_pagos_auditoria
              (tienda_id,pago_id,id_venta,evento,estado_anterior,estado_nuevo,actor_correo,observacion)
            VALUES(?,?,?,?,?,?,?,?)
            """,t,pago,idVenta,tipo,anterior,nuevo,actor,nota);
    }
    private static Instant instant(Timestamp t) {return t==null?null:t.toInstant();}
    private static final String PAGO_SELECT = """
            SELECT id_venta,estado,importe,moneda,banco,titular,numero_cuenta,cci,
                   correo_cliente,creado_en,verificado_por,verificado_en,motivo_rechazo
            FROM public.transferencia_pagos_manuales
            """;
    private static Pago mapear(java.sql.ResultSet r,int i) throws java.sql.SQLException {
        return new Pago(r.getInt(1),r.getString(2),r.getBigDecimal(3),r.getString(4),
                r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),
                instant(r.getTimestamp(10)),r.getString(11),instant(r.getTimestamp(12)),r.getString(13));
    }
    private static final String CLIENTE = """
            WHERE tienda_id=? AND id_venta=? AND usuario_id IN
              (SELECT id_usuario FROM public.usuarios WHERE tienda_id=? AND correo=? AND estado=true)
            """;
    private String estadoCliente(int venta,String correo) {
        if(venta<=0 || correo==null || correo.isBlank()) throw noEncontrado();
        List<String> estados = jdbc.query("SELECT estado FROM public.transferencia_pagos_manuales "+CLIENTE,
                (r,i)->r.getString(1),tenant(),venta,tenant(),correo);
        if(estados.isEmpty()) throw noEncontrado();
        return estados.getFirst();
    }
    @Transactional(readOnly = true)
    public Pago consultarCliente(int idVenta,String correo) {
        estadoCliente(idVenta,correo);
        return jdbc.query(PAGO_SELECT+CLIENTE,TransferenciaManualService::mapear,tenant(),idVenta,tenant(),correo).getFirst();
    }
    @Transactional(readOnly = true)
    public List<Pago> listarAdmin() {
        return jdbc.query(PAGO_SELECT+" WHERE tienda_id=? ORDER BY creado_en DESC LIMIT 100",
                TransferenciaManualService::mapear,tenant());
    }
    @Transactional(readOnly = true)
    public List<Evento> auditoriaAdmin(int idVenta) {
        return jdbc.query("""
          SELECT ocurrido_en,evento,estado_anterior,estado_nuevo,actor_correo,observacion
          FROM public.transferencia_pagos_auditoria WHERE tienda_id=? AND id_venta=?
          ORDER BY ocurrido_en,id
          """,(r,i)->new Evento(instant(r.getTimestamp(1)),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),tenant(),idVenta);
    }
    @Transactional(readOnly = true)
    public void exigirAprobado(int idVenta) {
        List<String> estados=jdbc.query("SELECT estado FROM public.transferencia_pagos_manuales WHERE tienda_id=? AND id_venta=?",
                (r,i)->r.getString(1),tenant(),idVenta);
        if(estados.isEmpty()||!"APROBADO".equals(estados.getFirst())) throw conflicto("Verifica el abono bancario antes de preparar o enviar");
    }

    @Transactional
    public Pago decidir(int idVenta,String correoAdmin,boolean aprobar,String motivo) {
        if(idVenta<=0||correoAdmin==null||correoAdmin.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solicitud invalida");
        if(!aprobar&&(motivo==null||motivo.isBlank()||motivo.length()>250))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica un motivo de rechazo");
        long t=tenant();
        Venta venta=ventas.buscarPorIdYTiendaConBloqueo(idVenta,t).orElseThrow(TransferenciaManualService::noEncontrado);
        if(!"TRANSFERENCIA".equals(venta.getMedioPago())) throw noEncontrado();
        if(venta.getEstadoPedido()!=null && "CANCELADO".equals(venta.getEstadoPedido().name()))
            throw conflicto("Pedido cancelado");
        record Lock(long id,String estado) {}
        List<Lock> pagos=jdbc.query("""
          SELECT id,estado FROM public.transferencia_pagos_manuales
          WHERE tienda_id=? AND id_venta=? FOR UPDATE
          """,(r,i)->new Lock(r.getLong(1),r.getString(2)),t,idVenta);
        if(pagos.isEmpty()) throw noEncontrado();
        Lock p=pagos.getFirst();
        if(!"PENDIENTE".equals(p.estado())) throw conflicto("Este pago ya fue decidido");
        if(venta.getEstadoPedido()!=com.tiendasebas.tiendasebas.entity.EstadoPedido.RECIBIDO) throw conflicto("Pedido fuera de estado recibido");
        if(!aprobar) { reponerStock(venta,t,idVenta); venta.setEstadoPedido(com.tiendasebas.tiendasebas.entity.EstadoPedido.CANCELADO); }
        String estado=aprobar?"APROBADO":"RECHAZADO";
        jdbc.update("""
          UPDATE public.transferencia_pagos_manuales SET estado=?,verificado_por=?,verificado_en=now(),motivo_rechazo=?
          WHERE tienda_id=? AND id_venta=? AND estado='PENDIENTE'
          """,estado,correoAdmin,aprobar?null:motivo.strip(),t,idVenta);
        venta.setEstado(aprobar?"PAGO_CONFIRMADO":"PAGO_RECHAZADO");
        ventas.saveAndFlush(venta);
        evento(t,p.id(),idVenta,estado,"PENDIENTE",estado,correoAdmin,aprobar?"Verificado por administrador":motivo.strip());
        return jdbc.query(PAGO_SELECT+" WHERE tienda_id=? AND id_venta=?",TransferenciaManualService::mapear,t,idVenta).getFirst();
    }
    private void reponerStock(Venta venta,long t,int idVenta) {
        List<DetalleVenta> renglones=detalles.findByVentaOrderByIdDetalleVentaAsc(venta);
        if(renglones.isEmpty()) throw conflicto("Pedido sin detalle; revisar inventario manualmente");
        Map<Integer,Integer> porVariante=new TreeMap<>();
        for(DetalleVenta d:renglones) {
            if(d.getProductoTalla()==null||d.getCantidad()==null||d.getCantidad()<=0) throw conflicto("Detalle invalido");
            try {porVariante.merge(d.getProductoTalla().getIdProductoTalla(),d.getCantidad(),Math::addExact);}
            catch(ArithmeticException e){throw conflicto("Cantidad de reposicion excesiva");}
        }
        for(var e:porVariante.entrySet()) {
            ProductoTalla v=inventario.buscarPorIdConBloqueo(e.getKey(),t).orElseThrow(TransferenciaManualService::noEncontrado);
            if(v.getStock()==null || v.getStock()<0) throw conflicto("Stock actual invalido");
            int antes=v.getStock();
            int despues;
            try {despues=Math.addExact(antes,e.getValue());}
            catch(ArithmeticException ex){throw conflicto("Overflow de stock");}
            v.setStock(despues);inventario.saveAndFlush(v);
            int escritos=jdbc.update("""
              INSERT INTO public.transferencia_stock_reposiciones
                (tienda_id,id_venta,id_producto_talla,cantidad,stock_anterior,stock_posterior)
              VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING
              """,t,idVenta,e.getKey(),e.getValue(),antes,despues);
            if(escritos!=1) throw conflicto("La reposicion ya se registro");
        }
    }

    @Transactional
    public ComprobanteInfo subirComprobante(int idVenta,String correo,MultipartFile archivo) {
        return guardarComprobante(idVenta,correo,archivo,null);
    }
    @Transactional
    public ComprobanteInfo reemplazarComprobante(int idVenta,String correo,MultipartFile archivo,String motivo) {
        if(motivo==null || motivo.isBlank() || motivo.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica motivo de reemplazo (maximo 100)");
        return guardarComprobante(idVenta,correo,archivo,motivo.strip());
    }
    private ComprobanteInfo guardarComprobante(int idVenta,String correo,MultipartFile archivo,String motivo) {
        Limpiada limpia=limpiar(archivo);
        long t=tenant();
        if(idVenta<=0||correo==null||correo.isBlank()) throw noEncontrado();
        List<Long> ids=jdbc.query("""
          SELECT id FROM public.transferencia_pagos_manuales
          """+CLIENTE+" FOR UPDATE",(r,i)->r.getLong(1),t,idVenta,t,correo);
        if(ids.isEmpty()) throw noEncontrado();
        if(!"PENDIENTE".equals(estadoCliente(idVenta,correo))) throw conflicto("Este pedido ya no esta pendiente");
        String sha=sha256(limpia.bytes());
        if(motivo!=null) {
            int saved=jdbc.update("""
                INSERT INTO public.transferencia_comprobantes_historial(tienda_id,id_venta,imagen,mime_tipo,sha256,enviado_en)
                SELECT tienda_id,id_venta,imagen,mime_tipo,sha256,enviado_en FROM public.transferencia_comprobantes
                WHERE tienda_id=? AND id_venta=?
                """,t,idVenta);
            if(saved!=1) throw conflicto("No hay comprobante anterior para reemplazar");
            jdbc.update("UPDATE public.transferencia_comprobantes SET imagen=?,mime_tipo=?,tamano_bytes=?,sha256=?,enviado_en=now() WHERE tienda_id=? AND id_venta=?",
                limpia.bytes(),limpia.mime(),limpia.bytes().length,sha,t,idVenta);
            evento(t,ids.getFirst(),idVenta,"CAPTURA_REEMPLAZADA","PENDIENTE","PENDIENTE",correo,motivo+" SHA256="+sha);
            return infoCliente(idVenta,correo);
        }
        int filas=jdbc.update("""
          INSERT INTO public.transferencia_comprobantes
            (tienda_id,id_venta,usuario_id,imagen,mime_tipo,tamano_bytes,sha256)
          SELECT ?,?,p.usuario_id,?,?,?,?
          FROM public.transferencia_pagos_manuales p
          WHERE p.tienda_id=? AND p.id_venta=? AND p.estado='PENDIENTE'
          ON CONFLICT (tienda_id,id_venta) DO NOTHING
          """,t,idVenta,limpia.bytes(),limpia.mime(),limpia.bytes().length,sha,t,idVenta);
        if(filas!=1) throw conflicto("Comprobante ya registrado o pedido cerrado");
        evento(t,ids.getFirst(),idVenta,"CAPTURA_RECIBIDA","PENDIENTE","PENDIENTE",correo,"Captura recibida; no confirma deposito");
        return infoCliente(idVenta,correo);
    }
    private static ComprobanteInfo info(java.sql.ResultSet r,int i)throws java.sql.SQLException {
        return new ComprobanteInfo(r.getInt(1),r.getString(2),r.getInt(3),r.getString(4),instant(r.getTimestamp(5)));
    }
    @Transactional(readOnly = true)
    public ComprobanteInfo infoCliente(int idVenta,String correo){
        estadoCliente(idVenta,correo);
        List<ComprobanteInfo> l=jdbc.query("""
          SELECT id_venta,mime_tipo,tamano_bytes,sha256,enviado_en FROM public.transferencia_comprobantes
          WHERE tienda_id=? AND id_venta=?
          """,TransferenciaManualService::info,tenant(),idVenta);
        if(l.isEmpty()) throw noEncontrado();return l.getFirst();
    }
    @Transactional(readOnly = true)
    public ComprobanteInfo infoAdmin(int idVenta){
        List<ComprobanteInfo> l=jdbc.query("""
          SELECT id_venta,mime_tipo,tamano_bytes,sha256,enviado_en FROM public.transferencia_comprobantes
          WHERE tienda_id=? AND id_venta=?
          """,TransferenciaManualService::info,tenant(),idVenta);
        if(l.isEmpty()) throw noEncontrado();return l.getFirst();
    }
    @Transactional(readOnly = true)
    public Imagen imagenCliente(int idVenta,String correo) {estadoCliente(idVenta,correo);return imagenAdmin(idVenta);}
    @Transactional(readOnly = true)
    public Imagen imagenAdmin(int idVenta){
        List<Imagen> l=jdbc.query("SELECT imagen,mime_tipo FROM public.transferencia_comprobantes WHERE tienda_id=? AND id_venta=?",
                (r,i)->new Imagen(r.getBytes(1),r.getString(2)),tenant(),idVenta);
        if(l.isEmpty()) throw noEncontrado();return l.getFirst();
    }
    @Transactional(readOnly = true)
    public Whatsapp whatsapp(int idVenta,String correo){
        estadoCliente(idVenta,correo);
        List<String> nums=jdbc.query("SELECT COALESCE(NULLIF(trim(whatsapp),''),NULLIF(trim(telefono_soporte),'')) FROM public.tiendas WHERE id=?",
                (r,i)->r.getString(1),tenant());
        String n=nums.isEmpty()?null:nums.getFirst();
        if(n==null) return new Whatsapp(null,null);
        n=n.replaceAll("[^0-9]","");
        if(n.matches("9[0-9]{8}"))n="51"+n;
        if(!n.matches("519[0-9]{8}"))return new Whatsapp(null,null);
        String msg="Hola, envie el comprobante de transferencia bancaria del pedido #"+idVenta+" por PEN "+consultarCliente(idVenta,correo).importe().toPlainString()+". Por favor, verifiquen el abono en su cuenta.";
        return new Whatsapp(n,"https://wa.me/"+n+"?text="+URLEncoder.encode(msg,StandardCharsets.UTF_8));
    }

    /** Valida bytes reales, dimensiones y re-encodea para eliminar metadatos. */
    static Limpiada limpiar(MultipartFile archivo){
        if(archivo==null||archivo.isEmpty()||archivo.getSize()>5*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PNG/JPG maximo 5 MB");
        String nombre=archivo.getOriginalFilename()==null?"":archivo.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        boolean png=nombre.endsWith(".png") && "image/png".equals(archivo.getContentType());
        boolean jpg=(nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) && "image/jpeg".equals(archivo.getContentType());
        if(!png && !jpg) throw imagenInvalida();
        try{
            byte[] original=archivo.getBytes();
            try(ImageInputStream entrada=ImageIO.createImageInputStream(new ByteArrayInputStream(original))){
                if(entrada==null)throw imagenInvalida();
                Iterator<ImageReader> it=ImageIO.getImageReaders(entrada);
                if(!it.hasNext())throw imagenInvalida();
                ImageReader lector=it.next();
                try{
                    lector.setInput(entrada,true,true);
                    int w=lector.getWidth(0),h=lector.getHeight(0);
                    String formato=lector.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if(w<=0||h<=0||w>4096||h>4096||!(formato.equals("png")||formato.equals("jpeg")||formato.equals("jpg")))throw imagenInvalida();
                    if(png != formato.equals("png")) throw imagenInvalida();
                    BufferedImage img=lector.read(0);
                    BufferedImage rgb=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=rgb.createGraphics();
                    try{g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(img,0,0,null);}
                    finally{g.dispose();}
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    // JPEG para normalizar metadatos y no almacenar material arbitrario.
                    if(!ImageIO.write(rgb,"jpeg",out))throw imagenInvalida();
                    byte[] limpio=out.toByteArray();
                    if(limpio.length<=0||limpio.length>5*1024*1024)throw imagenInvalida();
                    return new Limpiada(limpio,"image/jpeg");
                }finally{lector.dispose();}
            }
        }catch(IOException ex){throw imagenInvalida();}
    }
    private static ResponseStatusException imagenInvalida(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagen PNG o JPG invalida");}
    private static String sha256(byte[] b){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 no disponible",e);}
    }
}
