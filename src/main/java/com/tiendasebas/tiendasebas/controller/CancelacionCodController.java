package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.CancelacionCodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ventas/admin/contraentrega")
public class CancelacionCodController {

    private final CancelacionCodService cancelaciones;

    public CancelacionCodController(CancelacionCodService cancelaciones) {
        this.cancelaciones = cancelaciones;
    }

    public record MotivoCancelacion(@NotBlank @Size(min=10,max=300) String motivo) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{ventaId}/cancelar")
    public ResponseEntity<CancelacionCodService.Resultado> cancelar(
            @PathVariable Integer ventaId,
            @Valid @RequestBody MotivoCancelacion request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(cancelaciones.cancelar(
                ventaId, request.motivo(), authentication.getName()));
    }
}
