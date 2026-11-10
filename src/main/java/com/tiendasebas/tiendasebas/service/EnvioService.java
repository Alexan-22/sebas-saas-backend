package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.controller.EnvioController;
import com.tiendasebas.tiendasebas.entity.TipoVenta;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gestion aislada de envios, sin alterar stock, caja o estados de Venta. */
@Service
public class EnvioService {

    public record EnvioDto(Long id, Integer idVenta, String operador, String modalidad,
                           String estado, String numeroGuia, String codigoRastreo,
                           String observaciones, OffsetDateTime creadoEn,
                           OffsetDateTime actualizadoEn) {}

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
        "PENDIENTE", Set.of("PREPARANDO", "CANCELADO"),
        "PREPARANDO", Set.of("DESPACHADO", "CANCELADO"),
        "DESPACHADO", Set.of("EN_TRANSITO", "DISPONIBLE_RETIRO", "ENTREGADO", "INCIDENCIA"),
        "EN_TRANSITO", Set.of("DISPONIBLE_RETIRO", "ENTREGADO", "INCIDENCIA"),
        "DISPONIBLE_RETIRO", Set.of("ENTREGADO", "INCIDENCIA"),
        "INCIDENCIA", Set.of("PREPARANDO", "DESPACHADO", "EN_TRANSITO", "ENTREGADO", "CANCELADO"),
        "ENTREGADO", Set.of(),
        "CANCELADO", Set.of()
    );

    private static final String COLUMNAS = "id, id_venta, operador, modalidad, estado, " +
            "numero_guia, codigo_rastreo, observaciones, creado_en, actualizado_en";

    private static final RowMapper<EnvioDto> MAPPER = (rs, n) -> new EnvioDto(
            rs.getLong("id"), rs.getInt("id_venta"), rs.getString("operador"),
            rs.getString("modalidad"), rs.getString("estado"),
            rs.getString("numero_guia"), rs.getString("codigo_rastreo"),
            rs.getString("observaciones"),
            rs.getObject("creado_en", OffsetDateTime.class),
            rs.getObject("actualizado_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;
    private final VentaRepository ventas;

    public EnvioService(JdbcTemplate jdbc, VentaRepository ventas) {
        this.jdbc = jdbc;
        this.ventas = ventas;
    }

    /** Opciones verificadas directamente contra el tipo_venta persistido, por tienda. */
    @Transactional(readOnly = true)
    public List<Integer> listarVentasOnlineElegibles() {
        return jdbc.queryForList("SELECT id_venta FROM public.ventas " +
                        "WHERE tienda_id=? AND tipo_venta='ONLINE' " +
                        "ORDER BY id_venta DESC LIMIT 5000",
                Integer.class, TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public List<EnvioDto> listar() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM public.saas_envios " +
                "WHERE tienda_id=? ORDER BY actualizado_en DESC LIMIT 200", MAPPER,
                TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public EnvioDto consultarAdmin(Integer ventaId) {
        validarVentaOnline(ventaId);
        return buscar(ventaId, false);
    }

    @Transactional(readOnly = true)
    public EnvioDto consultarPropio(Integer ventaId, String correo) {
        Long tienda = TenantContext.getCurrentTenant();
        if (ventaId == null || ventaId <= 0 || correo == null || correo.isBlank() ||
                ventas.findByIdVentaAndUsuarioCorreoAndTiendaId(ventaId, correo, tienda).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        return buscar(ventaId, false);
    }

    @Transactional
    public EnvioDto guardar(Integer ventaId, EnvioController.GuardarEnvio request) {
        validarVentaOnline(ventaId);
        Long tienda = TenantContext.getCurrentTenant();
        Integer cod = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.saas_cobros_contraentrega WHERE tienda_id=? AND id_venta=?",
                Integer.class, tienda, ventaId);
        if (cod != null && cod > 0 &&
                (!"PROPIO".equals(request.operador()) || !"DOMICILIO".equals(request.modalidad()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido contra entrega requiere reparto propio a domicilio");
        }
        int modificados = jdbc.update("""
            INSERT INTO public.saas_envios
               (tienda_id,id_venta,operador,modalidad,numero_guia,codigo_rastreo,observaciones)
            SELECT v.tienda_id,v.id_venta,?,?,?,?,?
              FROM public.ventas v
             WHERE v.tienda_id=? AND v.id_venta=? AND v.tipo_venta='ONLINE'
            ON CONFLICT (tienda_id,id_venta) DO UPDATE SET
               operador=EXCLUDED.operador,
               modalidad=EXCLUDED.modalidad,
               numero_guia=EXCLUDED.numero_guia,
               codigo_rastreo=EXCLUDED.codigo_rastreo,
               observaciones=EXCLUDED.observaciones,
               actualizado_en=now()
            WHERE saas_envios.estado IN ('PENDIENTE','PREPARANDO')
            """, request.operador(), request.modalidad(), normalizar(request.numeroGuia()),
                normalizar(request.codigoRastreo()), normalizar(request.observaciones()),
                tienda, ventaId);
        if (modificados != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede editar antes de despachar el envio");
        }
        return buscar(ventaId, false);
    }

    @Transactional
    public EnvioDto cambiarEstado(Integer ventaId, String estadoNuevo) {
        validarVentaOnline(ventaId);
        if ("CANCELADO".equals(estadoNuevo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se cancela un envio aislado sin cancelar la venta y reponer inventario. Usa el flujo de cancelacion COD pendiente.");
        }
        EnvioDto previo = buscar(ventaId, true);
        if (!TRANSICIONES.getOrDefault(previo.estado(), Set.of()).contains(estadoNuevo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transicion de estado no permitida: " + previo.estado() + " -> " + estadoNuevo);
        }
        if ("ENTREGADO".equals(estadoNuevo)) {
            List<String> cod = jdbc.query(
                    "SELECT estado FROM public.saas_cobros_contraentrega WHERE tienda_id=? AND id_venta=?",
                    (rs,i) -> rs.getString("estado"), TenantContext.getCurrentTenant(), ventaId);
            if (!cod.isEmpty() && !"COBRADO".equals(cod.get(0))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Confirma el dinero recibido antes de marcar el reparto como entregado");
            }
        }
        if ((estadoNuevo.equals("DESPACHADO") || estadoNuevo.equals("EN_TRANSITO"))
                && !previo.operador().equals("PROPIO")
                && (previo.numeroGuia() == null || previo.numeroGuia().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Se requiere numero de guia de Shalom u Olva antes de despachar");
        }
        int n = jdbc.update("UPDATE public.saas_envios SET estado=?, actualizado_en=now() " +
                        "WHERE tienda_id=? AND id_venta=? AND estado=?",
                estadoNuevo, TenantContext.getCurrentTenant(), ventaId, previo.estado());
        if (n != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "El envio cambio mientras se editaba");
        return buscar(ventaId, false);
    }

    private Venta validarVentaOnline(Integer ventaId) {
        Long tienda = TenantContext.getCurrentTenant();
        if (ventaId == null || ventaId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de venta no valido");
        }
        Venta venta = ventas.findByIdVentaAndTiendaId(ventaId, tienda)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        if (venta.getTipoVenta() != TipoVenta.ONLINE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta debe ser ONLINE");
        }
        return venta;
    }

    private EnvioDto buscar(Integer ventaId, boolean bloquear) {
        String sql = "SELECT " + COLUMNAS + " FROM public.saas_envios WHERE tienda_id=? AND id_venta=?" +
                (bloquear ? " FOR UPDATE" : "");
        List<EnvioDto> encontrados = jdbc.query(sql, MAPPER,
                TenantContext.getCurrentTenant(), ventaId);
        if (encontrados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guia aun no registrada");
        }
        return encontrados.get(0);
    }

    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }
}
