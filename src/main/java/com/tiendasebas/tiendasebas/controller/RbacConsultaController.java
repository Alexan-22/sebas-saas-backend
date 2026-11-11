package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RbacConsultaService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RbacConsultaController {

    private final RbacConsultaService servicio;

    public RbacConsultaController(RbacConsultaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/permisos")
    public List<Map<String, Object>> permisos() {
        return servicio.listarPermisos();
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return servicio.listarRoles();
    }

    @GetMapping("/roles/{id}/permisos")
    public List<Map<String, Object>> permisosDeRol(
            @PathVariable Long id
    ) {
        return servicio.permisosDeRol(id);
    }
}