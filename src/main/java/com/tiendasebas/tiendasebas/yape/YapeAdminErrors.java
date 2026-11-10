package com.tiendasebas.tiendasebas.yape;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = YapeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class YapeAdminErrors {
    private static final Logger log = LoggerFactory.getLogger(YapeAdminErrors.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Acceso denegado", "message", "No tienes permisos para esta operación"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> archivoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "Archivo demasiado grande", "message", "El QR no debe superar 2 MB"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> errorControlado(ResponseStatusException ex) {
        int codigo = ex.getStatusCode().value();
        if (codigo == 404) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "No encontrado", "message", "No existe un QR disponible para esta tienda"));
        }
        if (codigo == 403) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Acceso denegado", "message", "Operación no autorizada"));
        }
        if (codigo == 400) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Solicitud inválida", "message", "Revisa los datos de Yape y el archivo QR"));
        }
        log.error("Error inesperado de estado HTTP en Yape: {}", codigo, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error interno", "message", "No se pudo completar la operación Yape"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> errorPersistencia(DataAccessException ex) {
        log.error("Error de persistencia en Yape", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error interno", "message", "No se pudo completar la operación Yape"));
    }
}
