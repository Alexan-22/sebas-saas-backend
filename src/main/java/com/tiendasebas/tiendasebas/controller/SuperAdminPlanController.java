package com.tiendasebas.tiendasebas.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import com.tiendasebas.tiendasebas.service.PlanComercialService;
import com.tiendasebas.tiendasebas.service.SaasActividadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Sebas Collection SaaS V2 - Catálogo real de planes.
 * Las rutas /api/superadmin/** requieren SUPER_ADMIN en SecurityConfig.
 * No gestiona cobros ni altera las suscripciones existentes.
 */
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/superadmin/planes")
public class SuperAdminPlanController {

    private static final String CONSULTA_BASE = """
            SELECT p.id, p.codigo, p.nombre, p.descripcion,
                   p.precio_mensual, p.moneda, p.duracion_meses,
                   p.limite_productos, p.limite_usuarios,
                   p.limite_sucursales, p.beneficios::text AS beneficios_json,
                   p.activo, p.creado_en, p.actualizado_en
              FROM public.planes_saas p
            """;

    private static final BigDecimal PRECIO_MAXIMO =
            new BigDecimal("9999999999.99");

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    private final PlanComercialService comercial;
    private final SaasActividadService actividad;

    public SuperAdminPlanController(JdbcTemplate jdbc, JsonMapper jsonMapper, PlanComercialService comercial, SaasActividadService actividad) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.comercial = comercial;
        this.actividad = actividad;
    }

    public record CrearPlanRequest(
            String codigo,
            String nombre,
            String descripcion,
            BigDecimal precioMensual,
            Integer duracionMeses,
            Integer limiteProductos,
            Integer limiteUsuarios,
            Integer limiteSucursales,
            List<String> beneficios
    ) {}

    public record EditarPlanRequest(
            String nombre,
            String descripcion,
            BigDecimal precioMensual,
            Integer duracionMeses,
            Integer limiteProductos,
            Integer limiteUsuarios,
            Integer limiteSucursales,
            List<String> beneficios
    ) {}

    public record PlanResponse(
            Long id,
            String codigo,
            String nombre,
            String descripcion,
            BigDecimal precioMensual,
            String moneda,
            Integer duracionMeses,
            Integer limiteProductos,
            Integer limiteUsuarios,
            Integer limiteSucursales,
            List<String> beneficios,
            boolean activo,
            Instant creadoEn,
            Instant actualizadoEn
    ) {}

    private record DatosValidados(
            String nombre,
            String descripcion,
            BigDecimal precioMensual,
            int duracionMeses,
            Integer limiteProductos,
            Integer limiteUsuarios,
            Integer limiteSucursales,
            String beneficiosJson
    ) {}

    // GET /api/superadmin/planes?activo=true (filtro opcional).
    @GetMapping
    public List<PlanResponse> listar(@RequestParam(required = false) Boolean activo) {
        if (activo == null) {
            return jdbc.query(CONSULTA_BASE + " ORDER BY p.id", this::mapear);
        }
        return jdbc.query(
                CONSULTA_BASE + " WHERE p.activo = ? ORDER BY p.id",
                this::mapear,
                activo
        );
    }

    // GET /api/superadmin/planes/{id}
    @GetMapping("/{id}")
    public PlanResponse obtener(@PathVariable Long id) {
        validarId(id);
        List<PlanResponse> encontrados = jdbc.query(
                CONSULTA_BASE + " WHERE p.id = ?",
                this::mapear,
                id
        );
        if (encontrados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe el plan solicitado");
        }
        return encontrados.get(0);
    }

    // POST /api/superadmin/planes
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PlanResponse crear(@RequestBody CrearPlanRequest solicitud) {
        if (solicitud == null) {
            throw badRequest("El cuerpo de la solicitud es obligatorio");
        }

        String codigo = validarCodigo(solicitud.codigo());
        DatosValidados d = validarDatos(
                solicitud.nombre(), solicitud.descripcion(),
                solicitud.precioMensual(), solicitud.duracionMeses(),
                solicitud.limiteProductos(), solicitud.limiteUsuarios(),
                solicitud.limiteSucursales(), solicitud.beneficios()
        );

        comercial.bloquearCatalogo(true);
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO public.planes_saas (
                        codigo, nombre, descripcion, precio_mensual,
                        moneda, duracion_meses, limite_productos,
                        limite_usuarios, limite_sucursales, beneficios, activo
                    ) VALUES (
                        ?, ?, ?, ?, 'PEN', ?, ?, ?, ?, CAST(? AS jsonb), TRUE
                    ) RETURNING id
                    """, Long.class,
                    codigo, d.nombre(), d.descripcion(), d.precioMensual(),
                    d.duracionMeses(), d.limiteProductos(), d.limiteUsuarios(),
                    d.limiteSucursales(), d.beneficiosJson()
            );
            actividad.registrar("CREAR", "PLAN", id, "Se creó el plan " + id);
            return obtener(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El código del plan ya existe o incumple una restricción", ex);
        }
    }

    // PUT /api/superadmin/planes/{id}
    // El código es inmutable para mantener referencias estables.
    @PutMapping("/{id}")
    @Transactional
    public PlanResponse editar(
            @PathVariable Long id,
            @RequestBody EditarPlanRequest solicitud
    ) {
        validarId(id);
        if (solicitud == null) {
            throw badRequest("El cuerpo de la solicitud es obligatorio");
        }

        DatosValidados d = validarDatos(
                solicitud.nombre(), solicitud.descripcion(),
                solicitud.precioMensual(), solicitud.duracionMeses(),
                solicitud.limiteProductos(), solicitud.limiteUsuarios(),
                solicitud.limiteSucursales(), solicitud.beneficios()
        );

        comercial.bloquearCatalogo(true);
        int actualizados;
        try {
            actualizados = jdbc.update("""
                    UPDATE public.planes_saas
                       SET nombre = ?, descripcion = ?, precio_mensual = ?,
                           duracion_meses = ?, limite_productos = ?,
                           limite_usuarios = ?, limite_sucursales = ?,
                           beneficios = CAST(? AS jsonb),
                           actualizado_en = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """,
                    d.nombre(), d.descripcion(), d.precioMensual(),
                    d.duracionMeses(), d.limiteProductos(), d.limiteUsuarios(),
                    d.limiteSucursales(), d.beneficiosJson(), id
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Los datos incumplen una restricción de la base de datos", ex);
        }
        if (actualizados != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe el plan solicitado");
        }
        actividad.registrar("ACTUALIZAR", "PLAN", id, "Se actualizó el catálogo del plan " + id);
        return obtener(id);
    }

    // PUT /api/superadmin/planes/{id}/activar
    @PutMapping("/{id}/activar")
    @Transactional
    public PlanResponse activar(@PathVariable Long id) {
        return cambiarEstado(id, true);
    }

    // PUT /api/superadmin/planes/{id}/desactivar
    @PutMapping("/{id}/desactivar")
    @Transactional
    public PlanResponse desactivar(@PathVariable Long id) {
        return cambiarEstado(id, false);
    }

    private PlanResponse cambiarEstado(Long id, boolean activo) {
        validarId(id);
        comercial.bloquearCatalogo(true);
        PlanResponse anterior = obtener(id);
        if (anterior.activo() == activo) return anterior;
        if (!activo) comercial.comprobarDesactivacion(id);
        int filas = jdbc.update("""
                UPDATE public.planes_saas
                   SET activo = ?, actualizado_en = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, activo, id);
        if (filas != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe el plan solicitado");
        }
        actividad.registrar(activo ? "ACTIVAR" : "DESACTIVAR", "PLAN", id, "Se cambió la disponibilidad del plan " + id);
        return obtener(id);
    }

    private PlanResponse mapear(java.sql.ResultSet rs, int numeroFila)
            throws java.sql.SQLException {
        String[] beneficios = jsonMapper.readValue(
                rs.getString("beneficios_json"), String[].class
        );
        return new PlanResponse(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                rs.getBigDecimal("precio_mensual"),
                rs.getString("moneda").trim(),
                rs.getInt("duracion_meses"),
                (Integer) rs.getObject("limite_productos"),
                (Integer) rs.getObject("limite_usuarios"),
                (Integer) rs.getObject("limite_sucursales"),
                List.of(beneficios),
                rs.getBoolean("activo"),
                rs.getTimestamp("creado_en").toInstant(),
                rs.getTimestamp("actualizado_en").toInstant()
        );
    }

    private DatosValidados validarDatos(
            String nombre,
            String descripcion,
            BigDecimal precio,
            Integer duracion,
            Integer limiteProductos,
            Integer limiteUsuarios,
            Integer limiteSucursales,
            List<String> beneficios
    ) {
        String nombreLimpio = obligatorio(nombre, "nombre", 2, 100);
        String descripcionLimpia = descripcion == null ? null : descripcion.trim();
        if (descripcionLimpia != null && descripcionLimpia.length() > 1000) {
            throw badRequest("La descripción no puede superar 1000 caracteres");
        }
        if (descripcionLimpia != null && descripcionLimpia.isBlank()) {
            descripcionLimpia = null;
        }
        if (precio == null || precio.signum() < 0 ||
                precio.compareTo(PRECIO_MAXIMO) > 0) {
            throw badRequest("El precio mensual debe estar entre 0 y 9999999999.99");
        }
        BigDecimal precioNormalizado;
        try {
            precioNormalizado = precio.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw badRequest("El precio mensual admite como máximo 2 decimales");
        }
        if (duracion == null || duracion < 1 || duracion > 36) {
            throw badRequest("La duración debe estar entre 1 y 36 meses");
        }
        validarLimite(limiteProductos, "productos");
        validarLimite(limiteUsuarios, "usuarios");
        validarLimite(limiteSucursales, "sucursales");

        List<String> beneficiosLimpios = new ArrayList<>();
        if (beneficios != null) {
            if (beneficios.size() > 30) {
                throw badRequest("Se permiten como máximo 30 beneficios");
            }
            LinkedHashSet<String> unicos = new LinkedHashSet<>();
            for (String beneficio : beneficios) {
                if (beneficio == null || beneficio.isBlank() ||
                        beneficio.trim().length() > 200) {
                    throw badRequest("Cada beneficio debe tener entre 1 y 200 caracteres");
                }
                unicos.add(beneficio.trim());
            }
            beneficiosLimpios.addAll(unicos);
        }

        return new DatosValidados(
                nombreLimpio, descripcionLimpia, precioNormalizado,
                duracion, limiteProductos, limiteUsuarios, limiteSucursales,
                jsonMapper.writeValueAsString(beneficiosLimpios)
        );
    }

    private String validarCodigo(String codigo) {
        String limpio = codigo == null ? "" :
                codigo.trim().toUpperCase(Locale.ROOT);
        if (!limpio.matches("[A-Z0-9_]{3,50}")) {
            throw badRequest("El código requiere 3-50 caracteres: A-Z, 0-9 o _");
        }
        return limpio;
    }

    private String obligatorio(String texto, String campo, int min, int max) {
        String valor = texto == null ? "" : texto.trim();
        if (valor.length() < min || valor.length() > max) {
            throw badRequest("El campo " + campo + " requiere entre " +
                    min + " y " + max + " caracteres");
        }
        return valor;
    }

    private void validarLimite(Integer valor, String nombre) {
        if (valor != null && (valor < 1 || valor > 1_000_000)) {
            throw badRequest("El límite de " + nombre +
                    " debe ser positivo (máximo 1000000) o null para ilimitado");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("Identificador de plan inválido");
        }
    }

    private ResponseStatusException badRequest(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
