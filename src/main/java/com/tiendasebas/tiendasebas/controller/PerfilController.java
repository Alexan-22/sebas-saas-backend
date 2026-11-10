
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.ActualizarPerfilRequest;
import com.tiendasebas.tiendasebas.dto.CambiarContrasenaRequest;
import com.tiendasebas.tiendasebas.dto.PerfilResponse;
import com.tiendasebas.tiendasebas.service.PerfilService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfil")
@PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    @GetMapping
    public PerfilResponse obtener() {
        return service.obtenerMiPerfil();
    }

    @PutMapping
    public PerfilResponse actualizar(
            @Valid @RequestBody ActualizarPerfilRequest datos
    ) {
        return service.actualizarMiPerfil(datos);
    }

    @PutMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(
            @Valid @RequestBody CambiarContrasenaRequest datos
    ) {
        service.cambiarMiContrasena(datos);
    }
}
