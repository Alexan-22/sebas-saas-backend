package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RbacGestionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RbacGestionController {

    private final RbacGestionService servicio;

    public RbacGestionController(RbacGestionService servicio) {
        this.servicio = servicio;
    }

    public record CrearRolRequest(
            @NotBlank
            @Size(max = 80)
            String nombre,

            @Size(max = 200)
            String descripcion,

            @NotNull
            @Size(max = 50)
            List<String> permisos
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(
            @Valid @RequestBody CrearRolRequest request
    ) {
        return servicio.crearRol(
                request.nombre(),
                request.descripcion(),
                request.permisos()
        );
    }
}