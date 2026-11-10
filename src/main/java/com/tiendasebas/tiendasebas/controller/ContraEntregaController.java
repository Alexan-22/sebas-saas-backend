package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.ContraEntregaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/ventas")
public class ContraEntregaController {
    private final ContraEntregaService service;

    public ContraEntregaController(ContraEntregaService service) { this.service = service; }

    /** Requiere sesion autenticada (anyRequest().authenticated()). */
    @GetMapping("/contraentrega/config")
    public ResponseEntity<ContraEntregaService.Config> config() {
        return ResponseEntity.ok(service.consultarConfig());
    }

    /** Seguridad ADMIN: /api/ventas/admin/** ya esta restringido en SecurityConfig. */
    @GetMapping("/admin/contraentrega")
    public ResponseEntity<List<ContraEntregaService.Cobro>> listar() {
        return ResponseEntity.ok(service.listarAdmin());
    }

    public record ConfirmarCobro(
            @NotBlank @Pattern(regexp="EFECTIVO|YAPE") String metodo,
            @Size(max=100) String referencia,
            @AssertTrue(message="Debes confirmar que el dinero fue recibido") boolean dineroRecibido
    ) {}

    @PatchMapping("/admin/contraentrega/{ventaId}/cobrar")
    public ResponseEntity<ContraEntregaService.Cobro> cobrar(
            @PathVariable Integer ventaId, @Valid @RequestBody ConfirmarCobro datos,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(service.confirmarCobro(
                ventaId, datos.metodo(), datos.referencia(), authentication.getName()));
    }

    /** Devuelve un estado minimo; valida propiedad real de la compra y tienda. */
    @GetMapping("/{ventaId}/contraentrega")
    public ResponseEntity<ContraEntregaService.EstadoCliente> consultarPropio(
            @PathVariable Integer ventaId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(service.consultarPropio(ventaId, authentication.getName()));
    }
}
