package com.tiendasebas.tiendasebas.yape;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.dto.CompraRequest;
import com.tiendasebas.tiendasebas.dto.CompraResponse;
import com.tiendasebas.tiendasebas.entity.Venta;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Pago Yape manual. NO es una pasarela ni comprueba depósitos automáticamente. */
@Service
public class PagoYapeManualService {
    private final JdbcTemplate jdbc;

    public PagoYapeManualService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EstadoPago(
            int idVenta,
            String estado,
            BigDecimal importe,
            String moneda,
            String numeroYape,
            String titularYape,
            String correoCliente,
            String qrUrl,
            Instant creadoEn,
            String verificadoPor,
            Instant verificadoEn,
            String motivoRechazo
    ) {}

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
                FROM public.yape_checkout_reintentos c
                JOIN public.ventas v ON v.tienda_id=c.tienda_id AND v.id_venta=c.id_venta
                WHERE c.tienda_id=? AND c.usuario_id=? AND c.clave=?
                """, (rs, row) -> new Anterior(rs.getString(1), new CompraResponse(rs.getInt(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6),
                rs.getString(7), "Pedido Yape ya registrado")), tienda, usuario.getIdUsuario(), clave);
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
                INSERT INTO public.yape_checkout_reintentos(tienda_id,usuario_id,clave,huella,id_venta)
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yape requiere claveIdempotencia UUID");
        }
    }

    static String huella(CompraRequest request) {
        var d = request.getDireccionEnvio();
        StringBuilder text = new StringBuilder();
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

    private record Cuenta(String numero, String titular, byte[] qr, String sha) {}

    private static long tenant() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no identificada");
        }
        return id;
    }

    private static void comprobarTienda(long tiendaId) {
        if (tiendaId != tenant()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda diferente");
        }
    }

    private static ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }

    private Cuenta cuentaHabilitada(long tiendaId) {
        List<Cuenta> cuentas = jdbc.query("""
                SELECT y.numero_yape, y.titular_yape, q.imagen, q.sha256
                FROM public.yape_configuracion_tienda y
                JOIN public.yape_qr_imagen q ON q.tienda_id = y.tienda_id
                JOIN public.tiendas t ON t.id = y.tienda_id
                WHERE y.tienda_id = ? AND y.habilitado = true AND t.estado = 'ACTIVA'
                """, (rs, fila) -> new Cuenta(
                        rs.getString(1), rs.getString(2), rs.getBytes(3), rs.getString(4)), tiendaId);
        if (cuentas.isEmpty()) {
            throw conflicto("Yape no esta habilitado para esta tienda");
        }
        Cuenta cuenta = cuentas.getFirst();
        if (cuenta.numero() == null || !cuenta.numero().matches("9[0-9]{8}") ||
                cuenta.titular() == null || cuenta.titular().isBlank() ||
                cuenta.qr() == null || cuenta.qr().length == 0) {
            throw conflicto("La configuracion Yape de esta tienda esta incompleta");
        }
        return cuenta;
    }

    @Transactional(readOnly = true)
    public void validarHabilitado(long tiendaId) {
        comprobarTienda(tiendaId);
        cuentaHabilitada(tiendaId);
    }

    /** Se invoca DENTRO de VentaService.realizarCompra @Transactional, después del saveAndFlush. */
    @Transactional
    public void registrarPendiente(Venta venta, Usuario usuario) {
        long tiendaId = tenant();
        if (venta == null || usuario == null || !tiendaIdEquals(venta.getTiendaId(), tiendaId)
                || !tiendaIdEquals(usuario.getTiendaId(), tiendaId)
                || venta.getIdVenta() == null || venta.getTotal() == null ||
                venta.getTotal().compareTo(BigDecimal.ZERO) <= 0 ||
                !"YAPE".equals(venta.getMedioPago())) {
            throw conflicto("Pedido Yape invalido");
        }
        Cuenta cuenta = cuentaHabilitada(tiendaId);
        List<Long> ids = jdbc.query("""
                INSERT INTO public.yape_pagos_manuales
                    (tienda_id, id_venta, usuario_id, correo_cliente, importe, numero_yape,
                     titular_yape, qr_imagen, qr_sha256, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE')
                RETURNING id
                """, (rs, row) -> rs.getLong(1),
                tiendaId, venta.getIdVenta(), usuario.getIdUsuario(), usuario.getCorreo(), venta.getTotal(),
                cuenta.numero(), cuenta.titular(), cuenta.qr(), cuenta.sha());
        if (ids.size() != 1) throw conflicto("No se pudo registrar el pago Yape");
        registrarEvento(tiendaId, ids.getFirst(), venta.getIdVenta(),
                "CREADO", null, "PENDIENTE", usuario.getCorreo(), null);
    }

    private static boolean tiendaIdEquals(Long value, long expected) {
        return value != null && value == expected;
    }

    private void registrarEvento(long tiendaId, long pagoId, int idVenta,
                               String evento, String antes, String despues,
                               String actor, String observacion) {
        jdbc.update("""
                INSERT INTO public.yape_pagos_auditoria
                  (tienda_id, pago_id, id_venta, evento, estado_anterior,
                   estado_nuevo, actor_correo, observacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, tiendaId, pagoId, idVenta, evento, antes, despues, actor, observacion);
    }

    private EstadoPago mapear(ResultSet rs, int fila) throws SQLException {
        return new EstadoPago(
                rs.getInt("id_venta"), rs.getString("estado"),
                rs.getBigDecimal("importe"), rs.getString("moneda"),
                rs.getString("numero_yape"), rs.getString("titular_yape"),
                rs.getString("correo_cliente"),
                "/api/yape/pagos/mios/" + rs.getInt("id_venta") + "/qr",
                instante(rs.getTimestamp("creado_en")), rs.getString("verificado_por"),
                instante(rs.getTimestamp("verificado_en")), rs.getString("motivo_rechazo"));
    }

    private static Instant instante(Timestamp valor) {
        return valor == null ? null : valor.toInstant();
    }

    private static final String CONSULTA = """
            SELECT id_venta, estado, importe, moneda, numero_yape,
                   titular_yape, correo_cliente, creado_en,
                   verificado_por, verificado_en, motivo_rechazo
            FROM public.yape_pagos_manuales
            """;

    @Transactional(readOnly = true)
    public EstadoPago consultarCliente(int idVenta, String correo) {
        long tiendaId = tenant();
        if (idVenta <= 0 || correo == null || correo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado");
        }
        List<EstadoPago> encontrados = jdbc.query(CONSULTA +
                        " WHERE tienda_id = ? AND id_venta = ? AND usuario_id IN " +
                        " (SELECT id_usuario FROM public.usuarios WHERE correo = ? AND tienda_id = ? AND estado = true)",
                this::mapear, tiendaId, idVenta, correo, tiendaId);
        if (encontrados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado");
        }
        return encontrados.getFirst();
    }

    @Transactional(readOnly = true)
    public byte[] qrDeCliente(int idVenta, String correo) {
        long tiendaId = tenant();
        List<byte[]> imagenes = jdbc.query("""
                SELECT p.qr_imagen
                FROM public.yape_pagos_manuales p
                JOIN public.tiendas t ON t.id = p.tienda_id
                JOIN public.yape_configuracion_tienda y ON y.tienda_id = p.tienda_id
                JOIN public.yape_qr_imagen q ON q.tienda_id = p.tienda_id
                WHERE p.tienda_id = ? AND p.id_venta = ?
                  AND p.usuario_id IN (SELECT id_usuario FROM public.usuarios
                       WHERE correo = ? AND tienda_id = p.tienda_id AND estado = true)
                  AND p.estado = 'PENDIENTE' AND t.estado = 'ACTIVA'
                  AND y.habilitado = true AND y.numero_yape = p.numero_yape
                  AND q.sha256 = p.qr_sha256
                """, (rs, row) -> rs.getBytes(1), tiendaId, idVenta, correo);
        if (imagenes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR no encontrado");
        }
        return imagenes.getFirst();
    }

    @Transactional(readOnly = true)
    public List<EstadoPago> listarAdmin() {
        return jdbc.query(CONSULTA +
                " WHERE tienda_id = ? ORDER BY creado_en DESC LIMIT 100",
                this::mapear, tenant());
    }

    public record EventoAuditoria(Instant ocurridoEn, String evento,
                                 String estadoAnterior, String estadoNuevo,
                                 String actorCorreo, String observacion) {}

    @Transactional(readOnly = true)
    public List<EventoAuditoria> auditoriaAdmin(int idVenta) {
        long tiendaId = tenant();
        return jdbc.query("""
                SELECT a.ocurrido_en, a.evento, a.estado_anterior,
                       a.estado_nuevo, a.actor_correo, a.observacion
                FROM public.yape_pagos_auditoria a
                JOIN public.yape_pagos_manuales p ON p.id = a.pago_id
                    AND p.tienda_id = a.tienda_id
                WHERE a.tienda_id = ? AND a.id_venta = ?
                ORDER BY a.ocurrido_en, a.id
                """, (rs, row) -> new EventoAuditoria(
                    instante(rs.getTimestamp(1)), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6)), tiendaId, idVenta);
    }

    /** Bloquea el avance logístico de pedidos Yape no aprobados. */
    @Transactional(readOnly = true)
    public void exigirAprobado(int idVenta) {
        List<String> estados = jdbc.query("""
                SELECT estado FROM public.yape_pagos_manuales
                WHERE tienda_id = ? AND id_venta = ?
                """, (rs, row) -> rs.getString(1), tenant(), idVenta);
        if (estados.isEmpty() || !"APROBADO".equals(estados.getFirst())) {
            throw conflicto("Debe verificarse el deposito Yape antes de preparar o enviar el pedido");
        }
    }


    /** Sale lock precedes variant locks; one ledger entry per aggregated variant. */
    private void reponerRechazado(long tienda, int venta, String actor) {
        record Item(int variante, int cantidad) {}
        var items = jdbc.query("""
                SELECT d.id_producto_talla, SUM(d.cantidad)::integer
                FROM public.detalle_venta d JOIN public.ventas v ON v.id_venta=d.id_venta
                WHERE v.tienda_id=? AND v.id_venta=? GROUP BY d.id_producto_talla ORDER BY d.id_producto_talla
                """, (rs,row) -> new Item(rs.getInt(1),rs.getInt(2)), tienda, venta);
        if (items.isEmpty()) throw conflicto("Pedido sin detalles de inventario");
        for (var item : items) {
            var stocks = jdbc.query("""
                    SELECT pt.stock FROM public.producto_tallas pt
                    JOIN public.productos p ON p.id_producto=pt.id_producto
                    WHERE pt.id_producto_talla=? AND p.tienda_id=? FOR UPDATE OF pt
                    """, (rs,row) -> rs.getInt(1), item.variante(), tienda);
            if (stocks.size()!=1 || item.cantidad()<=0) throw conflicto("Inventario de otra tienda o invalido");
            int antes=stocks.getFirst(), despues=Math.addExact(antes,item.cantidad());
            jdbc.update("UPDATE public.producto_tallas SET stock=? WHERE id_producto_talla=?",despues,item.variante());
            jdbc.update("""
                    INSERT INTO public.yape_inventario_reposiciones
                    (tienda_id,id_venta,id_producto_talla,cantidad,stock_anterior,stock_posterior,actor_correo)
                    VALUES (?,?,?,?,?,?,?)
                    """,tienda,venta,item.variante(),item.cantidad(),antes,despues,actor);
        }
    }

    @Transactional
    public EstadoPago decidir(int idVenta, String correoAdmin, boolean aprobar, String motivo) {
        long tiendaId = tenant();
        if (idVenta <= 0 || correoAdmin == null || correoAdmin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud invalida");
        }
        if (!aprobar && (motivo == null || motivo.isBlank() || motivo.length() > 250)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un motivo de rechazo");
        }
        // Orden consistente de bloqueos: primero la venta, despues el pago.
        List<String> ventas = jdbc.query("""
                SELECT estado_pedido FROM public.ventas
                WHERE id_venta = ? AND tienda_id = ? AND medio_pago = 'YAPE'
                FOR UPDATE
                """, (rs, row) -> rs.getString(1), idVenta, tiendaId);
        if (ventas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido Yape no encontrado");
        }
        if ("CANCELADO".equals(ventas.getFirst())) {
            throw conflicto("Un pedido cancelado no se puede aprobar");
        }
        record Bloqueo(long id, String estado) {}
        List<Bloqueo> pagos = jdbc.query("""
                SELECT id, estado FROM public.yape_pagos_manuales
                WHERE tienda_id = ? AND id_venta = ? FOR UPDATE
                """, (rs, row) -> new Bloqueo(rs.getLong(1), rs.getString(2)), tiendaId, idVenta);
        if (pagos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado");
        }
        Bloqueo pago = pagos.getFirst();
        if (!"PENDIENTE".equals(pago.estado())) {
            throw conflicto("El pago ya fue procesado y no puede volver a decidirse");
        }
        if (!"RECIBIDO".equals(ventas.getFirst()))
            throw conflicto("Solo se puede decidir un pedido recibido y sin despacho");
        if (!aprobar) reponerRechazado(tiendaId, idVenta, correoAdmin);
        String nuevo = aprobar ? "APROBADO" : "RECHAZADO";
        jdbc.update("""
                UPDATE public.yape_pagos_manuales
                SET estado = ?, verificado_en = now(), verificado_por = ?, motivo_rechazo = ?
                WHERE id = ? AND tienda_id = ? AND estado = 'PENDIENTE'
                """, nuevo, correoAdmin, aprobar ? null : motivo.trim(), pago.id(), tiendaId);
        jdbc.update("""
                UPDATE public.ventas SET estado = ?
                WHERE id_venta = ? AND tienda_id = ?
                """, aprobar ? "PAGO_CONFIRMADO" : "PAGO_RECHAZADO", idVenta, tiendaId);
        registrarEvento(tiendaId, pago.id(), idVenta,
                aprobar ? "APROBADO" : "RECHAZADO", "PENDIENTE", nuevo,
                correoAdmin, aprobar ? "Verificado por administrador" : motivo.trim());
        if (!aprobar) jdbc.update("UPDATE public.ventas SET estado_pedido='CANCELADO' WHERE tienda_id=? AND id_venta=?", tiendaId, idVenta);
        return jdbc.query(CONSULTA + " WHERE tienda_id = ? AND id_venta = ?",
                this::mapear, tiendaId, idVenta).getFirst();
    }
}
