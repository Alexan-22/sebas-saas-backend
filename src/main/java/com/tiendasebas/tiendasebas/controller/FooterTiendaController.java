package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.FooterTiendaService;
import com.tiendasebas.tiendasebas.service.FooterTiendaService.FooterConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tiendas")
public class FooterTiendaController {
    private final FooterTiendaService servicio;
    public FooterTiendaController(FooterTiendaService servicio) { this.servicio = servicio; }

    @GetMapping("/footer")
    public ResponseEntity<FooterConfig> publico() { return ResponseEntity.ok(servicio.obtener()); }

    @GetMapping("/admin/footer")
    public ResponseEntity<FooterConfig> admin() { return ResponseEntity.ok(servicio.obtener()); }

    @PutMapping("/admin/footer")
    public ResponseEntity<FooterConfig> guardar(@RequestBody FooterConfig datos) {
        return ResponseEntity.ok(servicio.guardar(datos));
    }
}
