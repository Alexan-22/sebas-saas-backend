package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RbacAsignacionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/admin/empleados")
@PreAuthorize("hasRole('ADMIN')")
public class RbacAsignacionController {

    private final RbacAsignacionService servicio;

    public RbacAsignacionController(
            RbacAsignacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/{usuarioId}/roles/{rolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void asignar(
            @PathVariable Integer usuarioId,
            @PathVariable Long rolId) {
        servicio.asignar(usuarioId, rolId);
    }

    @GetMapping("/{usuarioId}/roles")
    public List<Map<String, Object>> listar(
            @PathVariable Integer usuarioId) {
        return servicio.rolesDeEmpleado(usuarioId);
    }

    @DeleteMapping("/{usuarioId}/roles/{rolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(
            @PathVariable Integer usuarioId,
            @PathVariable Long rolId) {
        servicio.revocar(usuarioId, rolId);
    }
}