package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.EditorTiendaService;
import com.tiendasebas.tiendasebas.service.EditorTiendaService.Contenido;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DisenoTiendaPublicoController {
    private final EditorTiendaService editor;

    public DisenoTiendaPublicoController(EditorTiendaService editor) {
        this.editor = editor;
    }

    @GetMapping("/api/home/public/diseno")
    public Contenido obtener() {
        return editor.disenoPublicado();
    }
}
