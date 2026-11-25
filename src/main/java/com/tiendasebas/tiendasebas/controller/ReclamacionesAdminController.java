package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.ReclamacionesAdminService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Solo administradores autenticados de la tienda resuelta por el servidor. */
@RestController
@RequestMapping("/api/v1/reclamaciones/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ReclamacionesAdminController {
    private final ReclamacionesAdminService servicio;

    public ReclamacionesAdminController(ReclamacionesAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<ReclamacionesAdminService.Resumen>> listar(
            @RequestParam(defaultValue = "30") int limite,
            @RequestParam(defaultValue = "0") long antesDe) {
        return privado(servicio.listar(limite, antesDe));
    }

    @GetMapping("/{numeroHoja}")
    public ResponseEntity<ReclamacionesAdminService.Detalle> detalle(@PathVariable long numeroHoja) {
        return privado(servicio.detalle(numeroHoja));
    }

    public record NuevaNota(String texto) {}

    @PostMapping("/{numeroHoja}/notas")
    public ResponseEntity<Void> nota(@PathVariable long numeroHoja,
                                     @RequestBody NuevaNota nota, Principal usuario) {
        servicio.agregarNota(numeroHoja, nota == null ? null : nota.texto(),
                usuario == null ? null : usuario.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .build();
    }

    private static <T> ResponseEntity<T> privado(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body(body);
    }
}
