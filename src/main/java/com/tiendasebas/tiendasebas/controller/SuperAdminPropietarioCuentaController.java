package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.SaasActividadService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

// ======================================================
// SEBAS COLLECTION SAAS V2
// ADMINISTRACION DE CUENTAS DE PROPIETARIOS
//
// Edicion de datos y estado de acceso.
//
// Seguridad:
// - Rutas protegidas por SUPER_ADMIN en SecurityConfig
// - Solo modifica el propietario asignado
// - Verifica que el usuario sea ADMIN
// - Verifica que pertenezca a la misma tienda
// - No modifica contrasenas, roles ni tienda_id
// - No elimina usuarios
// ======================================================

@RestController
@RequestMapping("/api/superadmin/propietarios/cuentas")
public class SuperAdminPropietarioCuentaController {

    private final JdbcTemplate jdbc;
    private final SaasActividadService actividad;

    public SuperAdminPropietarioCuentaController(
            JdbcTemplate jdbc,
            SaasActividadService actividad
    ) {
        this.jdbc = jdbc;
        this.actividad = actividad;
    }

    // ==================================================
    // DTO - EDITAR INFORMACION
    // ==================================================

    public record EditarCuentaRequest(
            String nombre,
            String apellido,
            String correo
    ) {}

    // ==================================================
    // DTO - ESTADO DEL ACCESO
    // ==================================================

    public record CambiarEstadoRequest(
            Boolean activo
    ) {}

    // ==================================================
    // DTO - RESPUESTA
    // ==================================================

    public record CuentaPropietarioResponse(
            Long tiendaId,
            Integer usuarioId,
            String nombre,
            String apellido,
            String correo,
            boolean activo
    ) {}

    // ==================================================
    // CONSULTAR CUENTA DE PROPIETARIO
    // ==================================================

    @GetMapping("/tiendas/{tiendaId}")
    public CuentaPropietarioResponse obtener(
            @PathVariable Long tiendaId
    ) {

        validarTiendaId(tiendaId);

        return consultarCuenta(tiendaId, false);
    }

    // ==================================================
    // EDITAR NOMBRE, APELLIDO Y CORREO
    // ==================================================

    @PutMapping("/tiendas/{tiendaId}")
    @Transactional
    public CuentaPropietarioResponse editar(
            @PathVariable Long tiendaId,
            @RequestBody EditarCuentaRequest request
    ) {

        validarTiendaId(tiendaId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Datos obligatorios"
            );
        }

        String nombre = validarTexto(
                request.nombre(),
                "Nombre",
                100
        );

        String apellido = validarTexto(
                request.apellido(),
                "Apellido",
                100
        );

        String correo = validarCorreo(
                request.correo()
        );

        // Bloquear la asignacion y cuenta durante
        // la transaccion para evitar cambios
        // simultaneos de propietario.

        CuentaPropietarioResponse actual =
                consultarCuenta(tiendaId, true);

        // El correo es globalmente unico.
        // Verificar tambien diferencias de mayusculas.

        Boolean correoOcupado = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.usuarios
                    WHERE LOWER(correo) = ?
                      AND id_usuario <> ?
                )
                """,
                Boolean.class,
                correo,
                actual.usuarioId()
        );

        if (Boolean.TRUE.equals(correoOcupado)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya pertenece a otra cuenta"
            );
        }

        try {

            int actualizados = jdbc.update(
                    """
                    UPDATE public.usuarios
                    SET
                        nombre = ?,
                        apellido = ?,
                        correo = ?
                    WHERE id_usuario = ?
                      AND tienda_id = ?
                    """,
                    nombre,
                    apellido,
                    correo,
                    actual.usuarioId(),
                    tiendaId
            );

            if (actualizados != 1) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No se pudo actualizar la cuenta"
                );
            }

            actividad.registrar("ACTUALIZAR", "PROPIETARIO", actual.usuarioId(),
                    "Se actualizaron los datos de un propietario");
            return consultarCuenta(tiendaId, false);

        } catch (DataIntegrityViolationException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya existe o los datos incumplen una restriccion",
                    ex
            );
        }
    }

    // ==================================================
    // ACTIVAR / DESACTIVAR ACCESO DE PROPIETARIO
    // ==================================================

    @PutMapping("/tiendas/{tiendaId}/estado")
    @Transactional
    public CuentaPropietarioResponse cambiarEstado(
            @PathVariable Long tiendaId,
            @RequestBody CambiarEstadoRequest request
    ) {

        validarTiendaId(tiendaId);

        if (
                request == null ||
                        request.activo() == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica el estado activo"
            );
        }

        // Solo permitimos modificar al ADMIN que
        // figura actualmente como propietario
        // de esta tienda.

        CuentaPropietarioResponse actual =
                consultarCuenta(tiendaId, true);

        int actualizados = jdbc.update(
                """
                UPDATE public.usuarios
                SET estado = ?
                WHERE id_usuario = ?
                  AND tienda_id = ?
                """,
                request.activo(),
                actual.usuarioId(),
                tiendaId
        );

        if (actualizados != 1) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo cambiar el estado"
            );
        }

        actividad.registrar(request.activo() ? "ACTIVAR" : "DESACTIVAR",
                "PROPIETARIO", actual.usuarioId(),
                "Se modificó el acceso de un propietario");
        return consultarCuenta(tiendaId, false);
    }

    // ==================================================
    // CONSULTA CENTRAL SEGURA
    // ==================================================

    private CuentaPropietarioResponse consultarCuenta(
            Long tiendaId,
            boolean bloquear
    ) {

        String sql = """
                SELECT
                    tp.tienda_id,
                    u.id_usuario,
                    u.nombre,
                    u.apellido,
                    u.correo,
                    u.estado

                FROM public.tienda_propietarios tp

                INNER JOIN public.usuarios u
                    ON u.id_usuario = tp.usuario_id
                   AND u.tienda_id = tp.tienda_id

                INNER JOIN public.roles r
                    ON r.id_rol = u.id_rol

                WHERE tp.tienda_id = ?
                  AND r.nombre_rol = 'ADMIN'
                """;

        if (bloquear) {
            sql += " FOR UPDATE OF tp, u";
        }

        List<CuentaPropietarioResponse> cuentas =
                jdbc.query(
                        sql,
                        (rs, fila) ->
                                new CuentaPropietarioResponse(
                                        rs.getLong("tienda_id"),
                                        rs.getInt("id_usuario"),
                                        rs.getString("nombre"),
                                        rs.getString("apellido"),
                                        rs.getString("correo"),
                                        rs.getBoolean("estado")
                                ),
                        tiendaId
                );

        if (cuentas.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No existe un propietario ADMIN asignado a esta tienda"
            );
        }

        return cuentas.get(0);
    }

    // ==================================================
    // VALIDACIONES
    // ==================================================

    private void validarTiendaId(Long tiendaId) {

        if (
                tiendaId == null ||
                        tiendaId <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de tienda invalido"
            );
        }
    }

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
                    campo + " debe tener entre 2 y " +
                            maximo + " caracteres"
            );
        }

        return texto;
    }

    private String validarCorreo(String valor) {

        String correo = valor == null
                ? ""
                : valor.trim().toLowerCase(
                Locale.ROOT
        );

        if (
                correo.isEmpty() ||
                        correo.length() > 150 ||
                        !correo.matches(
                                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correo electronico invalido"
            );
        }

        return correo;
    }
}
