package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.B2bService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/b2b")
public class B2bController {
    private final B2bService service;
    public B2bController(B2bService service) { this.service = service; }

    // Public: solo configuración y escalas de la tienda resuelta por su dominio.
    @GetMapping("/public/config")
    public B2bService.Config configuracionPublica() { return service.obtenerConfig(); }

    @GetMapping("/public/productos/{id}/escalas")
    public List<B2bService.Escala> escalasPublicas(@PathVariable Integer id) {
        return service.listarEscalasPublico(id);
    }

    // Protegido en SecurityConfig con hasRole("ADMIN").
    @GetMapping("/admin/config")
    public B2bService.Config configAdmin() { return service.obtenerConfig(); }

    @PutMapping("/admin/config")
    public B2bService.Config guardarConfig(@RequestBody B2bService.Config config) {
        return service.guardarConfig(config);
    }

    @GetMapping("/admin/productos/{id}/escalas")
    public List<B2bService.Escala> escalasAdmin(@PathVariable Integer id) {
        return service.listarEscalasAdmin(id);
    }

    @PutMapping("/admin/productos/{id}/escalas")
    public ResponseEntity<List<B2bService.Escala>> guardarEscalas(
            @PathVariable Integer id, @RequestBody B2bService.EscalasRequest request) {
        return ResponseEntity.ok(service.guardarEscalas(id, request));
    }
}
