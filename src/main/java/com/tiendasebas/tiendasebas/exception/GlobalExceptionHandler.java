
package com.tiendasebas.tiendasebas.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    // ==========================================
    // 1. ERRORES DE COMPRA
    // ==========================================

    @ExceptionHandler(CompraException.class)
    public ResponseEntity<Map<String, Object>>
    manejarCompraException(CompraException exception) {

        return respuesta(
                HttpStatus.CONFLICT,
                "Error al procesar la compra",
                exception.getMessage()
        );
    }

    // ==========================================
    // 2. VALIDACIONES @VALID
    // ==========================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>>
    manejarValidacion(
            MethodArgumentNotValidException exception
    ) {

        String mensaje = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(
                        "Los datos ingresados no son válidos"
                );

        return respuesta(
                HttpStatus.BAD_REQUEST,
                "Error de validación",
                mensaje
        );
    }

    // ==========================================
    // 3. ERRORES HTTP EXPLICITOS
    // ==========================================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>>
    manejarResponseStatus(
            ResponseStatusException exception
    ) {

        HttpStatusCode estado = exception.getStatusCode();

        String mensaje = exception.getReason();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "No se pudo procesar la solicitud";
        }

        return respuesta(
                estado,
                "Error en la solicitud",
                mensaje
        );
    }

    // ==========================================
    // 4. METODO HTTP NO PERMITIDO
    // ==========================================

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<Map<String, Object>>
    manejarMetodoNoPermitido(
            HttpRequestMethodNotSupportedException exception
    ) {

        log.warn(
                "Método HTTP no permitido: {}",
                exception.getMethod()
        );

        return respuesta(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Método no permitido",
                "El endpoint no admite este método HTTP"
        );
    }

    // ==========================================
    // 5. JSON INVALIDO
    // ==========================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>>
    manejarJsonInvalido(
            HttpMessageNotReadableException exception
    ) {

        return respuesta(
                HttpStatus.BAD_REQUEST,
                "JSON inválido",
                "El cuerpo de la solicitud no es válido"
        );
    }

    // ==========================================
    // 6. OTROS ERRORES DE NEGOCIO
    // ==========================================
    // Los errores no clasificados devuelven 500 sin detalles internos.
    // Los errores esperados usan validaciones o ResponseStatusException.

    // RBAC: denegacion de permisos -> HTTP 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>>
    manejarAccesoDenegado(AccessDeniedException exception) {

        return respuesta(
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "No tiene permisos para realizar esta operacion"
        );
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String,Object>> manejarParametroInvalido(Exception ex){
        return respuesta(HttpStatus.BAD_REQUEST,"Parametros invalidos","Revisa los parametros de la solicitud.");
    }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String,Object>> manejarCuentaConcurrente(Exception ex){
        return respuesta(HttpStatus.CONFLICT,"La cuenta cambio","Actualiza los datos y vuelve a intentar.");
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> manejarConflictoDatos(Exception ex){
        return respuesta(HttpStatus.CONFLICT,"Conflicto de datos","La operacion entra en conflicto con los datos existentes.");
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String,Object>> manejarBaseNoDisponible(org.springframework.dao.DataAccessException ex){
        log.error("Acceso a datos no disponible, tipo={}",ex.getClass().getSimpleName());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE,"Servicio no disponible","No se pudo completar la solicitud. Intenta mas tarde.");
    }


    // ENDPOINT O RECURSO HTTP NO ENCONTRADO
    @ExceptionHandler(
            org.springframework.web.servlet.resource.NoResourceFoundException.class
    )
    public ResponseEntity<Map<String, Object>> manejarRecursoNoEncontrado(
            org.springframework.web.servlet.resource.NoResourceFoundException exception
    ) {
        return respuesta(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                "La ruta solicitada no existe en este servidor."
        );
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>>
    manejarRuntimeException(
            RuntimeException exception
    ) {

        log.error(
                "Error de ejecucion no clasificado, tipo={}",
                exception.getClass().getSimpleName()
        );

        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,"Error interno del servidor","No se pudo procesar la solicitud. Intenta nuevamente.");
    }

    // ==========================================
    // 7. ERRORES INESPERADOS
    // ==========================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>>
    manejarException(Exception exception) {

        // Registrar detalles solo en el servidor.
        // No exponer trazas al navegador.

        log.error(
                "Excepcion inesperada en Spring Boot, tipo={}",
                exception.getClass().getSimpleName()
        );

        return respuesta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor",
                "Ocurrió un error inesperado. "
                        + "Inténtalo nuevamente."
        );
    }

    // ==========================================
    // 8. RESPUESTA JSON ESTANDAR
    // ==========================================

    private ResponseEntity<Map<String, Object>>
    respuesta(
            HttpStatusCode status,
            String error,
            String mensaje
    ) {

        Map<String, Object> datos =
                new LinkedHashMap<>();

        datos.put(
                "timestamp",
                LocalDateTime.now()
        );

        datos.put(
                "status",
                status.value()
        );

        datos.put(
                "error",
                error
        );

        datos.put(
                "message",
                mensaje
        );

        return ResponseEntity
                .status(status)
                .body(datos);
    }
}
