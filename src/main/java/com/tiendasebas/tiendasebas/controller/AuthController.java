package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.LoginRequest;
import com.tiendasebas.tiendasebas.dto.LoginResponse;
import com.tiendasebas.tiendasebas.dto.RegistroRequest;
import com.tiendasebas.tiendasebas.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<String> registrar(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.ok(authService.registrar(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}