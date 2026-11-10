package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.SaasActividadService;
import com.tiendasebas.tiendasebas.entity.Rol;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.RolRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

// ============================================
// SEBAS COLLECTION SAAS V2
// CONTROLADOR GLOBAL DE PROPIETARIOS
//
// Solo SUPER_ADMIN puede acceder a estas rutas
// mediante SecurityConfig.
//
// Un propietario utiliza una cuenta ADMIN
// vinculada a su propia tienda.
// ============================================

@RestController
@RequestMapping("/api/superadmin/propietarios")
public class SuperAdminPropietarioController {

    // ========================================
    // DEPENDENCIAS
    // ========================================

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SaasActividadService actividad;

    public SuperAdminPropietarioController(
            JdbcTemplate jdbc,
            UsuarioRepository usuarios,
            RolRepository roles,
            PasswordEncoder passwordEncoder,
            SaasActividadService actividad
    ) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.actividad = actividad;
    }

    // ========================================
    // CONSULTA BASE
    // ========================================

    private static final String SQL_PROPIETARIOS = """
            SELECT
                t.id AS tienda_id,
                t.nombre AS tienda_nombre,
                t.subdominio,
                t.estado AS estado_tienda,

                u.id_usuario AS usuario_id,
                u.nombre AS propietario_nombre,
                u.apellido AS propietario_apellido,
                u.correo AS propietario_correo,
                u.estado AS propietario_activo,

                r.nombre_rol AS propietario_rol,

                tp.asignado_en

            FROM public.tiendas t

            LEFT JOIN public.tienda_propietarios tp
                ON tp.tienda_id = t.id

            LEFT JOIN public.usuarios u
                ON u.id_usuario = tp.usuario_id

            LEFT JOIN public.roles r
                ON r.id_rol = u.id_rol
            """;

    // ========================================
    // DTO - PROPIETARIO POR TIENDA
    // ========================================

    public record PropietarioResumen(
            Long tiendaId,
            String tiendaNombre,
            String subdominio,
            String estadoTienda,

            Integer usuarioId,
            String nombre,
            String apellido,
            String correo,
            Boolean activo,
            String rol,

            OffsetDateTime asignadoEn
    ) {}

    // ========================================
    // DTO - ADMIN DISPONIBLE
    // ========================================

    public record CandidatoPropietario(
            Integer usuarioId,
            String nombre,
            String apellido,
            String correo,
            boolean asignado
    ) {}

    // ========================================
    // DTO - CREAR CUENTA
    // ========================================

    public record CrearPropietarioRequest(
            Long tiendaId,
            String nombre,
            String apellido,
            String correo,
            String contrasena
    ) {}

    // ========================================
    // DTO - ASIGNAR USUARIO EXISTENTE
    // ========================================

    public record AsignarPropietarioRequest(
            Integer usuarioId
    ) {}

    // ========================================
    // GET - LISTAR TODAS LAS TIENDAS
    // CON SUS PROPIETARIOS
    // ========================================

    @GetMapping
    public List<PropietarioResumen> listar() {

        return jdbc.query(
                SQL_PROPIETARIOS + """
                    ORDER BY t.id ASC
                    """,
                this::mapearPropietario
        );
    }

    // ========================================
    // GET - CONSULTAR PROPIETARIO DE TIENDA
    // ========================================

    @GetMapping("/tiendas/{tiendaId}")
    public PropietarioResumen obtener(
            @PathVariable Long tiendaId
    ) {

        validarTienda(tiendaId);

        return obtenerPorTienda(tiendaId);
    }

    // ========================================
    // GET - CANDIDATOS EXISTENTES
    // ========================================

    // Devuelve exclusivamente usuarios:
    // - Activos
    // - Con rol ADMIN
    // - De la tienda solicitada
    //
    // No devuelve contraseÃ±as.

    @GetMapping("/candidatos")
    public List<CandidatoPropietario> candidatos(
            @RequestParam Long tiendaId
    ) {

        validarTienda(tiendaId);

        String sql = """
                SELECT
                    u.id_usuario,
                    u.nombre,
                    u.apellido,
                    u.correo,

                    CASE
                        WHEN tp.usuario_id IS NULL
                        THEN FALSE
                        ELSE TRUE
                    END AS asignado

                FROM public.usuarios u

                INNER JOIN public.roles r
                    ON r.id_rol = u.id_rol

                LEFT JOIN public.tienda_propietarios tp
                    ON tp.tienda_id = u.tienda_id
                    AND tp.usuario_id = u.id_usuario

                WHERE u.tienda_id = ?
                  AND u.estado = TRUE
                  AND r.nombre_rol = 'ADMIN'

                ORDER BY
                    u.nombre ASC,
                    u.apellido ASC,
                    u.id_usuario ASC
                """;

        return jdbc.query(
                sql,
                (rs, fila) -> new CandidatoPropietario(
                        rs.getInt("id_usuario"),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("correo"),
                        rs.getBoolean("asignado")
                ),
                tiendaId
        );
    }

    // ========================================
    // POST - CREAR CUENTA DE PROPIETARIO
    // ========================================

    // La cuenta y su asignaciÃ³n se registran
    // dentro de una misma transacciÃ³n.
    //
    // Si la asignaciÃ³n falla, tambiÃ©n se
    // revierte la creaciÃ³n del usuario.
    //
    // Nunca aceptamos un rol enviado
    // desde Angular.

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PropietarioResumen crear(
            @RequestBody CrearPropietarioRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Datos obligatorios"
            );
        }

        Long tiendaId = request.tiendaId();

        validarTienda(tiendaId);

        String nombre = validarTexto(
                request.nombre(),
                "nombre",
                100
        );

        String apellido = validarTexto(
                request.apellido(),
                "apellido",
                100
        );

        String correo = validarCorreo(
                request.correo()
        );

        validarContrasena(
                request.contrasena()
        );

        // ====================================
        // NO REEMPLAZAR UN PROPIETARIO
        // POR ACCIDENTE
        // ====================================

        if (tienePropietario(tiendaId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La tienda ya tiene un propietario asignado"
            );
        }

        // ====================================
        // CORREO GLOBALMENTE UNICO
        // ====================================

        Long correosExistentes = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.usuarios
                WHERE LOWER(correo) = ?
                """,
                Long.class,
                correo
        );

        if (
                correosExistentes != null &&
                        correosExistentes > 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya estÃ¡ registrado"
            );
        }

        // ====================================
        // USAR ROL ADMIN EXISTENTE
        // ====================================

        Rol rolAdmin = roles
                .findByNombreRol("ADMIN")
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No existe el rol ADMIN"
                        )
                );

        // ====================================
        // CREAR CUENTA DE USUARIO
        // ====================================

        Usuario usuario = new Usuario();

        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setCorreo(correo);

        usuario.setPasswordHash(
                passwordEncoder.encode(
                        request.contrasena()
                )
        );

        usuario.setRol(rolAdmin);
        usuario.setTiendaId(tiendaId);
        usuario.setEstado(true);

        try {

            Usuario creado = usuarios.save(usuario);

            // =================================
            // VINCULAR CON LA MISMA TIENDA
            // =================================

            // ON CONFLICT DO NOTHING evita
            // sustituir una asignaciÃ³n existente.
            //
            // Si otro administrador realizÃ³ una
            // asignaciÃ³n simultÃ¡neamente, se
            // rechaza y se revierte la transacciÃ³n.

            int asignaciones = jdbc.update(
                    """
                    INSERT INTO public.tienda_propietarios (
                        tienda_id,
                        usuario_id
                    )
                    VALUES (?, ?)
                    ON CONFLICT (tienda_id)
                    DO NOTHING
                    """,
                    tiendaId,
                    creado.getIdUsuario()
            );

            if (asignaciones != 1) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La tienda ya tiene propietario"
                );
            }

            actividad.registrar("CREAR", "PROPIETARIO", creado.getIdUsuario(),
                    "Se creó un propietario para la tienda ID " + tiendaId);
            return obtenerPorTienda(tiendaId);

        } catch (DataIntegrityViolationException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo crear la cuenta: revisa los datos y el correo",
                    ex
            );
        }
    }

    // ========================================
    // PUT - ASIGNAR UN ADMIN EXISTENTE
    // ========================================

    // Solo se puede asignar:
    // - Un usuario real
    // - Activo
    // - Rol ADMIN
    // - De la MISMA tienda
    //
    // Si hay un propietario anterior, se
    // reemplaza su asignaciÃ³n principal.
    //
    // No cambia ni elimina las cuentas.

    @PutMapping("/tiendas/{tiendaId}")
    @Transactional
    public PropietarioResumen asignar(
            @PathVariable Long tiendaId,
            @RequestBody AsignarPropietarioRequest request
    ) {

        validarTienda(tiendaId);

        if (
                request == null ||
                        request.usuarioId() == null ||
                        request.usuarioId() <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuario obligatorio"
            );
        }

        Integer usuarioId = request.usuarioId();

        // ====================================
        // COMPROBAR QUE PERTENECE
        // A LA TIENDA Y ES ADMIN ACTIVO
        // ====================================

        Boolean candidatoValido = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.usuarios u

                    INNER JOIN public.roles r
                        ON r.id_rol = u.id_rol

                    WHERE u.id_usuario = ?
                      AND u.tienda_id = ?
                      AND u.estado = TRUE
                      AND r.nombre_rol = 'ADMIN'
                )
                """,
                Boolean.class,
                usuarioId,
                tiendaId
        );

        if (!Boolean.TRUE.equals(candidatoValido)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario no es un ADMIN activo de esta tienda"
            );
        }

        // ====================================
        // INSERTAR O ACTUALIZAR ASIGNACION
        // ====================================

        // La base de datos tambiÃ©n tiene una
        // FK compuesta (tienda_id, usuario_id)
        // que impide vÃ­nculos entre tiendas.

        jdbc.update(
                """
                INSERT INTO public.tienda_propietarios (
                    tienda_id,
                    usuario_id
                )
                VALUES (?, ?)

                ON CONFLICT (tienda_id)
                DO UPDATE SET
                    usuario_id = EXCLUDED.usuario_id,
                    asignado_en = CURRENT_TIMESTAMP
                """,
                tiendaId,
                usuarioId
        );

        actividad.registrar("ASIGNAR", "PROPIETARIO", usuarioId,
                "Se asignó un propietario a la tienda ID " + tiendaId);
        return obtenerPorTienda(tiendaId);
    }

    // ========================================
    // CONSULTA DE UNA TIENDA
    // ========================================

    private PropietarioResumen obtenerPorTienda(
            Long tiendaId
    ) {

        List<PropietarioResumen> resultado =
                jdbc.query(
                        SQL_PROPIETARIOS + """
                            WHERE t.id = ?
                            """,
                        this::mapearPropietario,
                        tiendaId
                );

        if (resultado.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tienda no encontrada"
            );
        }

        return resultado.get(0);
    }

    // ========================================
    // COMPROBAR ASIGNACION EXISTENTE
    // ========================================

    private boolean tienePropietario(
            Long tiendaId
    ) {

        Boolean existe = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.tienda_propietarios
                    WHERE tienda_id = ?
                )
                """,
                Boolean.class,
                tiendaId
        );

        return Boolean.TRUE.equals(existe);
    }

    // ========================================
    // VALIDAR EXISTENCIA DE TIENDA
    // ========================================

    private void validarTienda(
            Long tiendaId
    ) {

        if (tiendaId == null || tiendaId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID de tienda invÃ¡lido"
            );
        }

        Boolean existe = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.tiendas
                    WHERE id = ?
                )
                """,
                Boolean.class,
                tiendaId
        );

        if (!Boolean.TRUE.equals(existe)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "La tienda no existe"
            );
        }
    }

    // ========================================
    // VALIDAR NOMBRE Y APELLIDO
    // ========================================

    private String validarTexto(
            String valor,
            String campo,
            int maximo
    ) {

        String texto = valor == null
                ? ""
                : valor.trim();

        if (
                texto.length() < 2 ||
                        texto.length() > maximo
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El campo " + campo +
                            " debe tener entre 2 y " +
                            maximo + " caracteres"
            );
        }

        return texto;
    }

    // ========================================
    // VALIDAR CORREO
    // ========================================

    private String validarCorreo(
            String valor
    ) {

        String correo = valor == null
                ? ""
                : valor.trim().toLowerCase(
                Locale.ROOT
        );

        if (
                correo.length() > 150 ||
                        !correo.matches(
                                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correo electrÃ³nico invÃ¡lido"
            );
        }

        return correo;
    }

    // ========================================
    // VALIDAR CONTRASEÃ‘A
    // ========================================

    private void validarContrasena(
            String contrasena
    ) {

        if (contrasena == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ContraseÃ±a obligatoria"
            );
        }

        int bytes = contrasena.getBytes(
                StandardCharsets.UTF_8
        ).length;

        // BCrypt utiliza como mÃ¡ximo
        // los primeros 72 bytes.
        // No aceptamos truncamiento.

        if (
                bytes < 12 ||
                        bytes > 72
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseÃ±a debe contener entre 12 y 72 bytes UTF-8"
            );
        }
    }

    // ========================================
    // MAPEAR PROPIETARIOS
    // ========================================

    private PropietarioResumen mapearPropietario(
            ResultSet rs,
            int fila
    ) throws SQLException {

        Number usuarioId = (Number)
                rs.getObject("usuario_id");

        Boolean activo = (Boolean)
                rs.getObject("propietario_activo");

        OffsetDateTime fecha = rs.getObject(
                "asignado_en",
                OffsetDateTime.class
        );

        return new PropietarioResumen(
                rs.getLong("tienda_id"),
                rs.getString("tienda_nombre"),
                rs.getString("subdominio"),
                rs.getString("estado_tienda"),

                usuarioId == null
                        ? null
                        : usuarioId.intValue(),

                rs.getString("propietario_nombre"),
                rs.getString("propietario_apellido"),
                rs.getString("propietario_correo"),
                activo,
                rs.getString("propietario_rol"),

                fecha
        );
    }
}
