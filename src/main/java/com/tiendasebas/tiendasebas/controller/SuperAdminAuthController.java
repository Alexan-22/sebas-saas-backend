package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.LoginRequest;
import com.tiendasebas.tiendasebas.dto.LoginResponse;
import com.tiendasebas.tiendasebas.service.AuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/superadmin")
public class SuperAdminAuthController {

    private final AuthService authService;

    public SuperAdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request
    ) {
        LoginResponse response =
                authService.loginSuperAdmin(request);

        return ResponseEntity.ok(response);
    }
}
