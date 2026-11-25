package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.DespachoCorreoReclamacionesService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operacion manual protegida. No se habilita envio automatico en produccion. */
@RestController
@RequestMapping("/api/v1/reclamaciones/admin/notificaciones")
@PreAuthorize("hasRole('ADMIN')")
public class DespachoCorreoReclamacionesController {
    private final DespachoCorreoReclamacionesService servicio;
    public DespachoCorreoReclamacionesController(DespachoCorreoReclamacionesService servicio) {
        this.servicio = servicio;
    }
    @PostMapping("/procesar")
    public ResponseEntity<DespachoCorreoReclamacionesService.Resultado> procesar(
            @RequestParam(defaultValue="10") int limite) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body(servicio.procesar(limite));
    }
}
