package com.tiendasebas.tiendasebas.pagos;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Preserve security/infrastructure status instead of the legacy generic runtime 400. */
@RestControllerAdvice(assignableTypes = PaymentAdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentAdminErrors {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(Map.of("status",403,"message","Acceso a pagos no autorizado"));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> database(DataAccessException exception) {
        return ResponseEntity.status(500).body(Map.of("status",500,"message","No se pudo consultar la persistencia de pagos"));
    }
}
