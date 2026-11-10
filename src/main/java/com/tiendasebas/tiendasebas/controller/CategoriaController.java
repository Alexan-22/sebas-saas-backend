
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.service.CategoriaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<Categoria> listar() {
        return categoriaService.listarTodas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria registrar(@RequestBody Categoria categoria) {
        return categoriaService.guardar(categoria);
    }
}
