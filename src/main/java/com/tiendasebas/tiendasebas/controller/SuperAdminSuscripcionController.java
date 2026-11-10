
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.CrearSuscripcionRequest;
import com.tiendasebas.tiendasebas.service.SuscripcionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/suscripciones")
public class SuperAdminSuscripcionController {

    private final SuscripcionService service;

    public SuperAdminSuscripcionController(
            SuscripcionService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return service.listar();
    }

    @GetMapping("/tienda/{tiendaId}")
    public List<Map<String, Object>> porTienda(
            @PathVariable Long tiendaId
    ) {
        return service.listarPorTienda(tiendaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(
            @RequestBody CrearSuscripcionRequest request
    ) {
        return service.crear(request);
    }
}
