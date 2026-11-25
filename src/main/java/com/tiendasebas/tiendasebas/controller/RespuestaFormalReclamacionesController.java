package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.RespuestaFormalReclamacionesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/** No permite respuestas antes de habilitar la función en staging. */
@RestController
@RequestMapping("/api/v1/reclamaciones/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RespuestaFormalReclamacionesController {
    private final RespuestaFormalReclamacionesService servicio;

    @Value("${app.reclamaciones.respuestas-habilitadas:false}")
    private boolean habilitadas;

    public RespuestaFormalReclamacionesController(RespuestaFormalReclamacionesService servicio) {
        this.servicio = servicio;
    }

    public record SolicitudRespuesta(String texto) {}

    @PostMapping("/{numeroHoja}/respuesta")
    public ResponseEntity<RespuestaFormalReclamacionesService.Resultado> responder(
            @PathVariable long numeroHoja, @RequestBody SolicitudRespuesta solicitud, Principal principal) {
        if (!habilitadas) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionalidad no habilitada");
        var resultado = servicio.responder(numeroHoja, solicitud == null ? null : solicitud.texto(),
                principal == null ? null : principal.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body(resultado);
    }
}
