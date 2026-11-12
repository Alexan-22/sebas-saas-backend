package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.ColorTiendaDto;
import com.tiendasebas.tiendasebas.dto.GuardarColorTiendaRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ColorTiendaService {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final JdbcTemplate jdbc;

    public ColorTiendaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ColorTiendaDto mapear(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new ColorTiendaDto(
                rs.getLong("id_color"),
                rs.getString("nombre"),
                rs.getString("codigo_hex"),
                rs.getBoolean("activo")
        );
    }

    @Transactional(readOnly = true)
    public List<ColorTiendaDto> listar() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return jdbc.query("""
                SELECT id_color, nombre, codigo_hex, activo
                  FROM public.saas_colores_tienda
                 WHERE tienda_id = ?
                 ORDER BY lower(nombre), id_color
                """, this::mapear, tiendaId);
    }

    @Transactional
    public ColorTiendaDto crear(GuardarColorTiendaRequest request) {
        Long tiendaId = TenantContext.getCurrentTenant();
        String nombre = validarNombre(request);
        String hex = validarHex(request.codigoHex());
        try {
            List<ColorTiendaDto> creados = jdbc.query("""
                    INSERT INTO public.saas_colores_tienda (tienda_id, nombre, codigo_hex, activo)
                    VALUES (?, ?, ?, TRUE)
                    RETURNING id_color, nombre, codigo_hex, activo
                    """, this::mapear, tiendaId, nombre, hex);
            return creados.getFirst();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe ese color en la tienda o sus datos no son validos", ex);
        }
    }

    @Transactional
    public ColorTiendaDto actualizar(Long idColor, GuardarColorTiendaRequest request) {
        Long tiendaId = TenantContext.getCurrentTenant();
        if (idColor == null || idColor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de color invalido");
        }
        String nombre = validarNombre(request);
        String hex = validarHex(request.codigoHex());
        boolean activo = request.activo() == null || request.activo();
        try {
            List<ColorTiendaDto> actualizados = jdbc.query("""
                    UPDATE public.saas_colores_tienda
                       SET nombre = ?, codigo_hex = ?, activo = ?
                     WHERE id_color = ? AND tienda_id = ?
                    RETURNING id_color, nombre, codigo_hex, activo
                    """, this::mapear, nombre, hex, activo, idColor, tiendaId);
            if (actualizados.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Color no encontrado en esta tienda");
            }
            return actualizados.getFirst();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe ese color en la tienda o sus datos no son validos", ex);
        }
    }

    private String validarNombre(GuardarColorTiendaRequest request) {
        if (request == null || request.nombre() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre obligatorio");
        }
        String nombre = request.nombre().trim();
        if (nombre.isBlank() || nombre.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre debe tener de 1 a 60 caracteres");
        }
        return nombre;
    }

    private String validarHex(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String hex = valor.trim().toUpperCase(Locale.ROOT);
        if (!HEX.matcher(hex).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Color HEX invalido; usa #RRGGBB");
        }
        return hex;
    }
}
