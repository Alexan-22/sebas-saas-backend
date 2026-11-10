package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.TiendaConfigRequest;
import com.tiendasebas.tiendasebas.dto.TiendaPublicaResponse;
import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;
import com.tiendasebas.tiendasebas.service.TiendaIdentidadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tiendas")
public class TiendaController {
    private final TiendaRepository tiendas;
    private final TiendaIdentidadService identidad;

    public TiendaController(TiendaRepository tiendas, TiendaIdentidadService identidad) {
        this.tiendas = tiendas;
        this.identidad = identidad;
    }

    @GetMapping("/config")
    public ResponseEntity<TiendaPublicaResponse> config() {
        return tiendas.findById(TenantContext.getCurrentTenant())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/admin/config")
    public ResponseEntity<TiendaPublicaResponse> configAdmin() {
        return config();
    }

    @PutMapping("/admin/config")
    public TiendaPublicaResponse actualizar(@RequestBody TiendaConfigRequest request) {
        return toResponse(identidad.actualizar(request));
    }

    @PostMapping(value = "/admin/logo", consumes = "multipart/form-data")
    public TiendaPublicaResponse subirLogo(@RequestParam("imagen") MultipartFile archivo) {
        return toResponse(identidad.subirLogo(archivo));
    }

    private TiendaPublicaResponse toResponse(Tienda t) {
        return new TiendaPublicaResponse(
                t.getId(), t.getNombre(), t.getSubdominio(),
                t.getColorPrimario(), t.getLogoUrl(),
                t.getColorSecundario(), t.getTelefonoSoporte(),
                t.getWhatsapp(), t.getDescripcionComercial());
    }
}
