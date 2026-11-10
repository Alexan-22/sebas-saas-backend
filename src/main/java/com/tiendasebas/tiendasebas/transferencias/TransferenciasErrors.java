package com.tiendasebas.tiendasebas.transferencias;

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

import java.util.Map;

@RestControllerAdvice(assignableTypes = TransferenciasController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransferenciasErrors {
    private static final Logger logger = LoggerFactory.getLogger(TransferenciasErrors.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "No tienes permiso para esta operacion"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> errorSql(DataAccessException ex) {
        logger.error("Fallo de persistencia en transferencias", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "No se pudo procesar el pago Yape"));
    }
}
