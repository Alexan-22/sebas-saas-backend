package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class B2bService {
    private final JdbcTemplate db;

    public B2bService(JdbcTemplate db) { this.db = db; }

    public record Config(boolean habilitado, int minimoPrendas, BigDecimal minimoMonto) {}
    public record Escala(int cantidadMinima, BigDecimal precioUnitario) {}
    public record EscalasRequest(List<Escala> escalas) {}

    private long tenant() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public Config obtenerConfig() {
        List<Config> filas = db.query("""
            SELECT habilitado, minimo_prendas, minimo_monto
              FROM public.saas_b2b_config WHERE tienda_id = ?
            """, (rs, n) -> new Config(rs.getBoolean(1), rs.getInt(2), rs.getBigDecimal(3)), tenant());
        return filas.isEmpty() ? new Config(false, 0, BigDecimal.ZERO) : filas.get(0);
    }

    @Transactional
    public Config guardarConfig(Config datos) {
        if (datos == null || datos.minimoPrendas() < 0 || datos.minimoPrendas() > 100000
                || datos.minimoMonto() == null || datos.minimoMonto().compareTo(BigDecimal.ZERO) < 0
                || datos.minimoMonto().compareTo(new BigDecimal("99999999.99")) > 0
                || datos.minimoMonto().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuración mayorista inválida");
        }
        db.update("""
            INSERT INTO public.saas_b2b_config
              (tienda_id, habilitado, minimo_prendas, minimo_monto)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (tienda_id) DO UPDATE
              SET habilitado=EXCLUDED.habilitado,
                  minimo_prendas=EXCLUDED.minimo_prendas,
                  minimo_monto=EXCLUDED.minimo_monto,
                  actualizado_en=now()
            """, tenant(), datos.habilitado(), datos.minimoPrendas(), datos.minimoMonto());
        return obtenerConfig();
    }

    @Transactional(readOnly = true)
    public List<Escala> listarEscalasAdmin(Integer productoId) {
        validarProductoPropio(productoId, false);
        return consultarEscalas(productoId);
    }

    @Transactional(readOnly = true)
    public List<Escala> listarEscalasPublico(Integer productoId) {
        if (!obtenerConfig().habilitado()) return List.of();
        validarProductoPropio(productoId, true);
        return consultarEscalas(productoId);
    }

    private List<Escala> consultarEscalas(Integer productoId) {
        return db.query("""
            SELECT cantidad_minima, precio_unitario
            FROM public.saas_b2b_escalas
            WHERE tienda_id=? AND id_producto=?
            ORDER BY cantidad_minima ASC
            """, (rs, n) -> new Escala(rs.getInt(1), rs.getBigDecimal(2)), tenant(), productoId);
    }

    @Transactional
    public List<Escala> guardarEscalas(Integer productoId, EscalasRequest request) {
        validarProductoPropio(productoId, false);
        if (request == null || request.escalas() == null || request.escalas().size() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requieren entre 0 y 30 escalas");
        }
        BigDecimal precioLista = db.queryForObject("""
            SELECT CASE WHEN precio_oferta IS NOT NULL AND precio_oferta > 0
                             AND precio_oferta < precio THEN precio_oferta ELSE precio END
            FROM public.productos WHERE id_producto=? AND tienda_id=?
            """, BigDecimal.class, productoId, tenant());
        if (precioLista == null || precioLista.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto no tiene precio válido");
        }
        List<Escala> ordenadas = new ArrayList<>(request.escalas());
        if (ordenadas.contains(null)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escala nula");
        ordenadas.sort(Comparator.comparingInt(Escala::cantidadMinima));
        Set<Integer> cantidades = new HashSet<>();
        BigDecimal previo = precioLista;
        for (Escala e : ordenadas) {
            if (e.cantidadMinima() < 2 || e.cantidadMinima() > 100000
                    || !cantidades.add(e.cantidadMinima())
                    || e.precioUnitario() == null
                    || e.precioUnitario().scale() > 2
                    || e.precioUnitario().compareTo(BigDecimal.ZERO) <= 0
                    || e.precioUnitario().compareTo(previo) >= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Escalas inválidas: cantidades únicas y precios estrictamente decrecientes");
            }
            previo = e.precioUnitario();
        }
        db.update("DELETE FROM public.saas_b2b_escalas WHERE tienda_id=? AND id_producto=?", tenant(), productoId);
        for (Escala e : ordenadas) {
            db.update("""
                INSERT INTO public.saas_b2b_escalas
                 (tienda_id, id_producto, cantidad_minima, precio_unitario)
                VALUES (?, ?, ?, ?)
                """, tenant(), productoId, e.cantidadMinima(), e.precioUnitario());
        }
        return consultarEscalas(productoId);
    }

    private void validarProductoPropio(Integer id, boolean debeActivo) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        String sql = "SELECT count(*) FROM public.productos WHERE id_producto=? AND tienda_id=?";
        if (debeActivo) sql += " AND estado = true";
        Integer count = db.queryForObject(sql, Integer.class, id, tenant());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado en esta tienda");
        }
    }
}
