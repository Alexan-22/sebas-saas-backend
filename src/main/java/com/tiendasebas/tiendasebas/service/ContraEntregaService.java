package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.TipoVenta;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.exception.CompraException;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fase 3C: pago contra entrega SOLO para reparto propio local.
 * Nunca trata el registro del pedido como pago confirmado.
 */
@Service
public class ContraEntregaService {

    public record Config(boolean habilitado, String cobertura) {}
    public record Cobro(Integer idVenta, BigDecimal monto, String estado,
                        String metodoCobro, String referenciaCobro,
                        OffsetDateTime cobradoEn, OffsetDateTime creadoEn,
                        String estadoEnvio) {}
    public record EstadoCliente(Integer idVenta, String estado, BigDecimal monto) {}

    private final JdbcTemplate jdbc;
    private final VentaRepository ventas;

    public ContraEntregaService(JdbcTemplate jdbc, VentaRepository ventas) {
        this.jdbc = jdbc;
        this.ventas = ventas;
    }

    @Transactional(readOnly = true)
    public Config consultarConfig() {
        Long tienda = TenantContext.getCurrentTenant();
        List<Boolean> flags = jdbc.queryForList(
                "SELECT habilitado FROM public.saas_delivery_config WHERE tienda_id=?",
                Boolean.class, tienda);
        return new Config(!flags.isEmpty() && Boolean.TRUE.equals(flags.get(0)), "LIMA_LIMA");
    }

    /** Se invoca desde la MISMA transaccion de VentaService.realizarCompra. */
    @Transactional
    public void registrarPendiente(Venta venta) {
        Long tienda = TenantContext.getCurrentTenant();
        if (!consultarConfig().habilitado()) {
            throw new CompraException("Pago contra entrega no disponible para esta tienda");
        }
        if (venta == null || venta.getIdVenta() == null ||
                !tienda.equals(venta.getTiendaId()) || venta.getTipoVenta() != TipoVenta.ONLINE ||
                !"CONTRA_ENTREGA".equals(venta.getMedioPago()) || venta.getDireccionEnvio() == null) {
            throw new CompraException("Pedido contra entrega no valido");
        }
        var direccion = venta.getDireccionEnvio();
        if (!"lima".equalsIgnoreCase(direccion.getDepartamento().trim()) ||
                !"lima".equalsIgnoreCase(direccion.getProvincia().trim())) {
            throw new CompraException("Contra entrega solo disponible para Lima Metropolitana en esta fase");
        }
        if (venta.getTotal() == null || venta.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CompraException("El monto contra entrega debe ser positivo");
        }
        int creado = jdbc.update("""
            INSERT INTO public.saas_cobros_contraentrega(tienda_id,id_venta,monto,estado)
            SELECT v.tienda_id,v.id_venta,v.total,'PENDIENTE'
              FROM public.ventas v
             WHERE v.tienda_id=? AND v.id_venta=? AND v.tipo_venta='ONLINE'
               AND v.medio_pago='CONTRA_ENTREGA'
            """, tienda, venta.getIdVenta());
        if (creado != 1) throw new CompraException("No se registro el cobro pendiente");

        int envio = jdbc.update("""
            INSERT INTO public.saas_envios (tienda_id,id_venta,operador,modalidad,estado,observaciones)
            SELECT v.tienda_id,v.id_venta,'PROPIO','DOMICILIO','PENDIENTE',
                   'Pago contra entrega: cobrar al momento de entregar'
              FROM public.ventas v WHERE v.tienda_id=? AND v.id_venta=?
            """, tienda, venta.getIdVenta());
        if (envio != 1) throw new CompraException("No se registro el reparto propio");
    }

    @Transactional(readOnly = true)
    public List<Cobro> listarAdmin() {
        return jdbc.query("""
            SELECT c.id_venta, c.monto, c.estado, c.metodo_cobro, c.referencia_cobro,
                   c.cobrado_en, c.creado_en, e.estado AS estado_envio
              FROM public.saas_cobros_contraentrega c
              JOIN public.saas_envios e ON e.tienda_id=c.tienda_id AND e.id_venta=c.id_venta
             WHERE c.tienda_id=? ORDER BY c.creado_en DESC LIMIT 200
            """, (rs,i) -> new Cobro(
                    rs.getInt("id_venta"), rs.getBigDecimal("monto"), rs.getString("estado"),
                    rs.getString("metodo_cobro"), rs.getString("referencia_cobro"),
                    rs.getObject("cobrado_en", OffsetDateTime.class),
                    rs.getObject("creado_en", OffsetDateTime.class), rs.getString("estado_envio")),
                TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public EstadoCliente consultarPropio(Integer ventaId, String correo) {
        Long tienda = TenantContext.getCurrentTenant();
        if (ventaId == null || ventaId <= 0 || correo == null || correo.isBlank() ||
                ventas.findByIdVentaAndUsuarioCorreoAndTiendaId(ventaId, correo, tienda).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        List<EstadoCliente> lista = jdbc.query("""
            SELECT id_venta, estado, monto FROM public.saas_cobros_contraentrega
             WHERE tienda_id=? AND id_venta=?
            """, (rs,i) -> new EstadoCliente(rs.getInt("id_venta"),
                rs.getString("estado"), rs.getBigDecimal("monto")), tienda, ventaId);
        if (lista.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No es contra entrega");
        return lista.get(0);
    }

    @Transactional
    public Cobro confirmarCobro(Integer ventaId, String metodo, String referencia, String correoAdmin) {
        Long tienda = TenantContext.getCurrentTenant();
        if (ventaId == null || ventaId <= 0 || correoAdmin == null || correoAdmin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de cobro invalidos");
        }
        // Bloquea este cobro: dos administradores no pueden cobrar dos veces.
        List<String> estados = jdbc.query("""
            SELECT estado FROM public.saas_cobros_contraentrega
             WHERE tienda_id=? AND id_venta=? FOR UPDATE
            """, (rs,i) -> rs.getString("estado"), tienda, ventaId);
        if (estados.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobro no encontrado");
        if (!"PENDIENTE".equals(estados.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cobro ya procesado");
        }
        List<String> envio = jdbc.query("""
            SELECT estado FROM public.saas_envios
             WHERE tienda_id=? AND id_venta=? AND operador='PROPIO' AND modalidad='DOMICILIO'
            """, (rs,i) -> rs.getString("estado"), tienda, ventaId);
        if (envio.isEmpty() || !("DESPACHADO".equals(envio.get(0)) ||
                "EN_TRANSITO".equals(envio.get(0)) || "ENTREGADO".equals(envio.get(0)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Se debe despachar antes de registrar dinero recibido");
        }
        if (!"EFECTIVO".equals(metodo) && !"YAPE".equals(metodo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metodo de cobro no valido");
        }
        String ref = referencia == null ? null : referencia.trim();
        if ("YAPE".equals(metodo) && (ref == null || ref.length() < 4)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica referencia del pago Yape");
        }
        if (ref != null && ref.isBlank()) ref = null;
        int n = jdbc.update("""
            UPDATE public.saas_cobros_contraentrega
               SET estado='COBRADO', metodo_cobro=?, referencia_cobro=?,
                   registrado_por=?, cobrado_en=now(), actualizado_en=now()
             WHERE tienda_id=? AND id_venta=? AND estado='PENDIENTE'
            """, metodo, ref, correoAdmin, tienda, ventaId);
        if (n != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "El cobro cambio de estado");
        return listarAdmin().stream().filter(c -> c.idVenta().equals(ventaId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobro no encontrado"));
    }
}
