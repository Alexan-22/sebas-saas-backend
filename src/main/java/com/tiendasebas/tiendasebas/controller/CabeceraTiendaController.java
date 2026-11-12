package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.CabeceraTiendaService;
import com.tiendasebas.tiendasebas.service.CabeceraTiendaService.CabeceraConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tiendas")
public class CabeceraTiendaController {
    private final CabeceraTiendaService service;
    public CabeceraTiendaController(CabeceraTiendaService service) { this.service = service; }

    @GetMapping("/cabecera")
    public ResponseEntity<CabeceraConfig> publica() { return ResponseEntity.ok(service.obtener()); }

    @GetMapping("/admin/cabecera")
    public ResponseEntity<CabeceraConfig> admin() { return ResponseEntity.ok(service.obtener()); }

    @PutMapping("/admin/cabecera")
    public ResponseEntity<CabeceraConfig> guardar(@RequestBody CabeceraConfig datos) {
        return ResponseEntity.ok(service.guardar(datos));
    }
}
