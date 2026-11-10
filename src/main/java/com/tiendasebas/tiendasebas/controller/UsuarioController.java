package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.UsuarioAdminResponse;
import com.tiendasebas.tiendasebas.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // =========================================================
    // ADMIN - LISTAR USUARIOS
    // =========================================================
    @GetMapping("/admin")
    public ResponseEntity<List<UsuarioAdminResponse>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    // =========================================================
    // ADMIN - BUSCAR USUARIO
    // =========================================================
    @GetMapping("/admin/{idUsuario}")
    public ResponseEntity<UsuarioAdminResponse> buscar(@PathVariable Integer idUsuario) {
        return ResponseEntity.ok(usuarioService.buscarPorId(idUsuario));
    }

    // =========================================================
    // ADMIN - ACTIVAR / DESACTIVAR
    // =========================================================
    @PutMapping("/admin/{idUsuario}/estado")
    public ResponseEntity<UsuarioAdminResponse> cambiarEstado(@PathVariable Integer idUsuario, @RequestParam Boolean estado) {
        return ResponseEntity.ok(usuarioService.cambiarEstado(idUsuario, estado));
    }
}