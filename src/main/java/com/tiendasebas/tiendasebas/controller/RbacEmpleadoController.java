
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RbacEmpleadoService;
import com.tiendasebas.tiendasebas.service.RbacEmpleadoService.EmpleadoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rbac/admin/empleados")
@PreAuthorize("hasRole('ADMIN')")
public class RbacEmpleadoController {

    private final RbacEmpleadoService servicio;

    public RbacEmpleadoController(RbacEmpleadoService servicio) {
        this.servicio = servicio;
    }

    public record CrearEmpleadoRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Size(max = 100) String apellido,
            @NotBlank @Email @Size(max = 150) String correo,
            @NotBlank @Size(min = 10, max = 72) String password
    ) {}

    public record EditarEmpleadoRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Size(max = 100) String apellido,
            @NotBlank @Email @Size(max = 150) String correo
    ) {}

    public record CambiarEstadoRequest(
            @NotNull Boolean activo
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpleadoResponse crear(
            @Valid @RequestBody CrearEmpleadoRequest request
    ) {
        return servicio.crear(
                request.nombre(),
                request.apellido(),
                request.correo(),
                request.password()
        );
    }

    @GetMapping
    public List<EmpleadoResponse> listar() {
        return servicio.listar();
    }

    @PutMapping("/{usuarioId}")
    public EmpleadoResponse editar(
            @PathVariable Integer usuarioId,
            @Valid @RequestBody EditarEmpleadoRequest request
    ) {
        return servicio.editar(
                usuarioId,
                request.nombre(),
                request.apellido(),
                request.correo()
        );
    }

    @PatchMapping("/{usuarioId}/estado")
    public EmpleadoResponse cambiarEstado(
            @PathVariable Integer usuarioId,
            @Valid @RequestBody CambiarEstadoRequest request
    ) {
        return servicio.cambiarEstado(
                usuarioId,
                request.activo()
        );
    }
}
