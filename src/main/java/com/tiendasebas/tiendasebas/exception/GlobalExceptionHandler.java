
package com.tiendasebas.tiendasebas.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
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
    // Se conserva el comportamiento anterior
    // para no alterar otros módulos existentes.
    // Revisaremos estas excepciones por tipo
    // durante una etapa de mejoras.

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>>
    manejarRuntimeException(
            RuntimeException exception
    ) {

        log.error(
                "Error de ejecución no clasificado",
                exception
        );

        String mensaje = exception.getMessage();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "No se pudo procesar la solicitud";
        }

        return respuesta(
                HttpStatus.BAD_REQUEST,
                "Error en la solicitud",
                mensaje
        );
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
                "Excepción inesperada en Spring Boot",
                exception
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
