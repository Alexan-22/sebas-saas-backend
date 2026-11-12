package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.ColorTiendaDto;
import com.tiendasebas.tiendasebas.dto.GuardarColorTiendaRequest;
import com.tiendasebas.tiendasebas.service.ColorTiendaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas/admin/colores")
public class ColorTiendaController {
    private final ColorTiendaService colores;

    public ColorTiendaController(ColorTiendaService colores) {
        this.colores = colores;
    }

    @GetMapping
    public List<ColorTiendaDto> listar() {
        return colores.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColorTiendaDto crear(@RequestBody GuardarColorTiendaRequest request) {
        return colores.crear(request);
    }

    @PutMapping("/{idColor}")
    public ColorTiendaDto actualizar(@PathVariable Long idColor,
                                    @RequestBody GuardarColorTiendaRequest request) {
        return colores.actualizar(idColor, request);
    }
}
