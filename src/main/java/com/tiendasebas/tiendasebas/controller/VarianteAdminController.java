package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.VarianteAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tiendas/admin/variantes")
public class VarianteAdminController {

    private final VarianteAdminService service;

    public VarianteAdminController(VarianteAdminService service) {
        this.service = service;
    }

    @GetMapping("/producto/{idProducto}")
    public List<VarianteAdminService.Variante> listar(
            @PathVariable Integer idProducto) {
        return service.listar(idProducto);
    }

    @PostMapping
    public ResponseEntity<Map<String, Integer>> crear(
            @RequestBody VarianteAdminService.NuevaVariante solicitud) {

        Integer id = service.crear(solicitud);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("idProductoTalla", id));
    }
}