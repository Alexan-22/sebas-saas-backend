package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RbacAutorizacionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/empleado")
public class RbacEmpleadoAccesoController {

    private final RbacAutorizacionService rbac;

    public RbacEmpleadoAccesoController(
            RbacAutorizacionService rbac
    ) {
        this.rbac = rbac;
    }

    @GetMapping("/mis-permisos")
    @PreAuthorize("hasRole('EMPLEADO')")
    public List<String> misPermisos(
            Authentication authentication
    ) {
        return rbac.permisosActuales(authentication);
    }

    @GetMapping("/verificar-ventas")
    @PreAuthorize(
        "hasRole('EMPLEADO') and " +
        "@rbacAutorizacionService.tienePermiso(authentication, 'ventas.ver')"
    )
    public Map<String, Object> verificarVentas() {
        return Map.of(
            "autorizado", true,
            "permiso", "ventas.ver"
        );
    }
}