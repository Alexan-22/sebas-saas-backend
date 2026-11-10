package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.B2bCotizacionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accesible solo a ADMIN de la tienda: ya protegido por /api/b2b/admin/**. */
@RestController
@RequestMapping("/api/b2b/admin")
public class B2bCotizacionController {
    private final B2bCotizacionService servicio;

    public B2bCotizacionController(B2bCotizacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/cotizar")
    public B2bCotizacionService.Cotizacion cotizar(
            @RequestBody B2bCotizacionService.Solicitud solicitud) {
        return servicio.cotizar(solicitud);
    }
}
