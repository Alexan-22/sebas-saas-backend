
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.service.CategoriaGestionService;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias/admin")
@PreAuthorize("hasRole('ADMIN')")
public class CategoriaGestionController {

    private final CategoriaGestionService service;

    public CategoriaGestionController(CategoriaGestionService service) {
        this.service = service;
    }

    public record CategoriaDatos(
            String nombreCategoria,
            Boolean estado
    ) {}

    @GetMapping
    public List<Categoria> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria crear(@RequestBody CategoriaDatos datos) {
        return service.crear(
                datos.nombreCategoria(),
                datos.estado()
        );
    }

    @PutMapping("/{id}")
    public Categoria actualizar(
            @PathVariable Integer id,
            @RequestBody CategoriaDatos datos
    ) {
        return service.actualizar(
                id,
                datos.nombreCategoria(),
                datos.estado()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        service.eliminar(id);
    }
}


