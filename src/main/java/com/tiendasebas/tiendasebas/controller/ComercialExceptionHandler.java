package com.tiendasebas.tiendasebas.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** No publica SQL, parámetros JDBC ni detalles internos al cliente. */
@RestControllerAdvice(assignableTypes = {SuperAdminPlanController.class,
        SuperAdminSuscripcionController.class, SuperAdminReporteSaasController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ComercialExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> negocio(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error",
                ex.getReason() == null ? "No se pudo completar la operación" : ex.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> integridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("error", "La operación entra en conflicto con los datos existentes"));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> infraestructura(DataAccessException ex) {
        return ResponseEntity.status(503).body(Map.of("error", "El servicio comercial no está disponible. No se confirmó la operación"));
    }
}
