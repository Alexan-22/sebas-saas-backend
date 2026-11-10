package com.tiendasebas.tiendasebas.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminPerfilController {

    @GetMapping("/me")
    public Map<String, String> obtenerPerfil(
            Authentication authentication
    ) {
        return Map.of(
                "correo", authentication.getName(),
                "rol", "SUPER_ADMIN"
        );
    }
}