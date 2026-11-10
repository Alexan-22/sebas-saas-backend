package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.SaasActividadService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// ============================================
// SEBAS COLLECTION SAAS V2
// CONTROLADOR GLOBAL DE TIENDAS
// ============================================

@RestController
@RequestMapping("/api/superadmin/tiendas")
public class SuperAdminTiendaController {

    private final JdbcTemplate jdbcTemplate;
    private final SaasActividadService actividad;

    // ========================================
    // VALIDACIONES
    // ========================================

    private static final Pattern SUBDOMINIO_VALIDO =
            Pattern.compile(
                    "^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$"
            );

    private static final Pattern COLOR_VALIDO =
            Pattern.compile(
                    "^#[0-9a-fA-F]{6}$"
            );

    private static final String ACTIVA = "ACTIVA";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String SUSPENDIDA = "SUSPENDIDA";

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public SuperAdminTiendaController(
            JdbcTemplate jdbcTemplate,
            SaasActividadService actividad
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.actividad = actividad;
    }

    // ========================================
    // DTO - TIENDA REAL
    // ========================================

    public record TiendaResumen(
            Long id,
            String nombre,
            String subdominio,
            String colorPrimario,
            String logoUrl,
            LocalDateTime creadaEn,
            String estado
    ) {}

    // ========================================
    // DTO - LISTADO PAGINADO
    // ========================================

    public record PaginaTiendas(
            List<TiendaResumen> tiendas,
            long total,
            int pagina,
            int tamano
    ) {}

    // ========================================
    // DTO - CREAR TIENDA
    // ========================================

    public record CrearTiendaRequest(
            String nombre,
            String subdominio
    ) {}

    // ========================================
    // DTO - EDITAR TIENDA
    // ========================================

    public record ActualizarTiendaRequest(
            String nombre,
            String subdominio,
            String colorPrimario,
            String logoUrl
    ) {}

    // ========================================
    // GET - LISTAR TIENDAS
    // ========================================

    @GetMapping
    public PaginaTiendas listar(
            @RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {

        if (pagina < 0 || pagina > 100000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PÃ¡gina invÃ¡lida"
            );
        }

        if (tamano < 1 || tamano > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tamaÃ±o debe estar entre 1 y 100"
            );
        }

        String busqueda = buscar == null
                ? ""
                : buscar.trim().toLowerCase(Locale.ROOT);

        if (busqueda.length() > 80) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La bÃºsqueda es demasiado larga"
            );
        }

        String filtro = "";
        List<Object> parametros = new ArrayList<>();

        // BUSCAR POR NOMBRE O SUBDOMINIO

        if (!busqueda.isEmpty()) {

            filtro = """
                    WHERE LOWER(nombre) LIKE ?
                       OR LOWER(subdominio) LIKE ?
                    """;

            String patron = "%" + busqueda + "%";

            parametros.add(patron);
            parametros.add(patron);
        }

        // CONTAR RESULTADOS

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.tiendas " + filtro,
                Long.class,
                parametros.toArray()
        );

        // PREPARAR PAGINACION

        List<Object> parametrosLista =
                new ArrayList<>(parametros);

        parametrosLista.add(tamano);
        parametrosLista.add((long) pagina * tamano);

        // CONSULTAR TIENDAS Y SU ESTADO REAL

        String sql = """
                SELECT
                    id,
                    nombre,
                    subdominio,
                    color_primario,
                    logo_url,
                    creado_en,
                    estado
                FROM public.tiendas
                """
                + filtro
                + """
                 ORDER BY id ASC
                 LIMIT ? OFFSET ?
                """;

        List<TiendaResumen> tiendas = jdbcTemplate.query(
                sql,
                this::mapearTienda,
                parametrosLista.toArray()
        );

        return new PaginaTiendas(
                tiendas,
                total == null ? 0L : total,
                pagina,
                tamano
        );
    }

    // ========================================
    // POST - CREAR TIENDA
    // ========================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TiendaResumen crear(
            @RequestBody CrearTiendaRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Datos de tienda obligatorios"
            );
        }

        String nombre = validarNombre(
                request.nombre()
        );

        String subdominio = validarSubdominio(
                request.subdominio()
        );

        // DETECTAR DUPLICADOS
        // PostgreSQL tambiÃ©n garantiza unicidad
        // mediante el Ã­ndice creado anteriormente.

        Long existentes = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.tiendas
                WHERE LOWER(subdominio) = ?
                """,
                Long.class,
                subdominio
        );

        if (existentes != null && existentes > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El subdominio ya estÃ¡ registrado"
            );
        }

        // El estado inicial serÃ¡ ACTIVA.
        // PostgreSQL ya tiene ese DEFAULT.

        String sql = """
                INSERT INTO public.tiendas (
                    nombre,
                    subdominio
                )
                VALUES (?, ?)
                RETURNING
                    id,
                    nombre,
                    subdominio,
                    color_primario,
                    logo_url,
                    creado_en,
                    estado
                """;

        try {

            TiendaResumen creada =
                    jdbcTemplate.queryForObject(
                            sql,
                            this::mapearTienda,
                            nombre,
                            subdominio
                    );

            if (creada == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo recuperar la tienda creada"
                );
            }

            actividad.registrar("CREAR", "TIENDA", creada.id(),
                    "Se creó una tienda");
            return creada;

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El subdominio ya estÃ¡ registrado",
                    ex
            );
        }
    }

    // ========================================
    // PUT - EDITAR TIENDA
    // ========================================

    @PutMapping("/{id}")
    @Transactional
    public TiendaResumen actualizar(
            @PathVariable Long id,
            @RequestBody ActualizarTiendaRequest request
    ) {

        validarId(id);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Datos de tienda obligatorios"
            );
        }

        String nombre = validarNombre(
                request.nombre()
        );

        String subdominio = validarSubdominio(
                request.subdominio()
        );

        String colorPrimario = validarColor(
                request.colorPrimario()
        );

        String logoUrl = validarLogo(
                request.logoUrl()
        );

        // No modificamos el estado durante
        // la ediciÃ³n de informaciÃ³n comercial.

        String sql = """
                UPDATE public.tiendas
                SET
                    nombre = ?,
                    subdominio = ?,
                    color_primario = ?,
                    logo_url = ?
                WHERE id = ?
                RETURNING
                    id,
                    nombre,
                    subdominio,
                    color_primario,
                    logo_url,
                    creado_en,
                    estado
                """;

        try {

            List<TiendaResumen> actualizadas =
                    jdbcTemplate.query(
                            sql,
                            this::mapearTienda,
                            nombre,
                            subdominio,
                            colorPrimario,
                            logoUrl,
                            id
                    );

            if (actualizadas.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tienda no encontrada"
                );
            }

            actividad.registrar("ACTUALIZAR", "TIENDA", id,
                    "Se actualizaron los datos de una tienda");
            return actualizadas.get(0);

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El subdominio pertenece a otra tienda",
                    ex
            );
        }
    }

    // ========================================
    // PUT - ACTIVAR TIENDA
    // ========================================

    @PutMapping("/{id}/activar")
    @Transactional
    public TiendaResumen activar(
            @PathVariable Long id
    ) {

        return cambiarEstado(
                id,
                ACTIVA
        );
    }

    // ========================================
    // PUT - SUSPENDER TIENDA
    // ========================================

    @PutMapping("/{id}/suspender")
    @Transactional
    public TiendaResumen suspender(
            @PathVariable Long id
    ) {

        return cambiarEstado(
                id,
                SUSPENDIDA
        );
    }

    // ========================================
    // PUT - MARCAR PENDIENTE
    // ========================================

    @PutMapping("/{id}/pendiente")
    @Transactional
    public TiendaResumen marcarPendiente(
            @PathVariable Long id
    ) {

        return cambiarEstado(
                id,
                PENDIENTE
        );
    }

    // ========================================
    // CAMBIO REAL DE ESTADO EN SUPABASE
    // ========================================

    private TiendaResumen cambiarEstado(
            Long id,
            String nuevoEstado
    ) {

        validarId(id);

        // El estado solo puede provenir
        // de los mÃ©todos activar, suspender
        // o marcarPendiente.

        String sql = """
                UPDATE public.tiendas
                SET estado = ?
                WHERE id = ?
                RETURNING
                    id,
                    nombre,
                    subdominio,
                    color_primario,
                    logo_url,
                    creado_en,
                    estado
                """;

        List<TiendaResumen> resultado =
                jdbcTemplate.query(
                        sql,
                        this::mapearTienda,
                        nuevoEstado,
                        id
                );

        if (resultado.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tienda no encontrada"
            );
        }

        TiendaResumen tienda = resultado.get(0);
        actividad.registrar(
                nuevoEstado.equals(ACTIVA) ? "ACTIVAR" :
                nuevoEstado.equals(SUSPENDIDA) ? "SUSPENDER" : "PENDIENTE",
                "TIENDA", id, "Estado de tienda establecido en " + nuevoEstado
        );
        return tienda;
    }

    // ========================================
    // VALIDAR IDENTIFICADOR
    // ========================================

    private void validarId(
            Long id
    ) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de tienda invÃ¡lido"
            );
        }
    }

    // ========================================
    // VALIDAR NOMBRE
    // ========================================

    private String validarNombre(
            String valor
    ) {

        String nombre = valor == null
                ? ""
                : valor.trim();

        if (
                nombre.length() < 3 ||
                        nombre.length() > 100
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre debe tener entre 3 y 100 caracteres"
            );
        }

        return nombre;
    }

    // ========================================
    // VALIDAR SUBDOMINIO
    // ========================================

    private String validarSubdominio(
            String valor
    ) {

        String subdominio = valor == null
                ? ""
                : valor.trim().toLowerCase(Locale.ROOT);

        if (!SUBDOMINIO_VALIDO.matcher(
                subdominio
        ).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Subdominio invÃ¡lido"
            );
        }

        return subdominio;
    }

    // ========================================
    // VALIDAR COLOR
    // ========================================

    private String validarColor(
            String valor
    ) {

        String color = valor == null ||
                valor.isBlank()
                ? "#000000"
                : valor.trim();

        if (!COLOR_VALIDO.matcher(color).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El color debe tener formato #RRGGBB"
            );
        }

        return color.toUpperCase(Locale.ROOT);
    }

    // ========================================
    // VALIDAR URL DEL LOGO
    // ========================================

    private String validarLogo(
            String valor
    ) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        String logoUrl = valor.trim();

        if (logoUrl.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La URL del logo es demasiado larga"
            );
        }

        try {

            URI uri = URI.create(logoUrl);

            if (
                    !"https".equalsIgnoreCase(uri.getScheme()) ||
                            uri.getHost() == null ||
                            uri.getUserInfo() != null
            ) {
                throw new IllegalArgumentException(
                        "URL no permitida"
                );
            }

            return logoUrl;

        } catch (IllegalArgumentException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El logo debe utilizar una URL HTTPS vÃ¡lida"
            );
        }
    }

    // ========================================
    // MAPEAR RESULTADOS DE POSTGRESQL
    // ========================================

    private TiendaResumen mapearTienda(
            ResultSet rs,
            int numeroFila
    ) throws SQLException {

        Timestamp fecha = rs.getTimestamp(
                "creado_en"
        );

        LocalDateTime creadaEn = fecha == null
                ? null
                : fecha.toLocalDateTime();

        return new TiendaResumen(
                rs.getLong("id"),
                rs.getString("nombre"),
                rs.getString("subdominio"),
                rs.getString("color_primario"),
                rs.getString("logo_url"),
                creadaEn,
                rs.getString("estado")
        );
    }
}
