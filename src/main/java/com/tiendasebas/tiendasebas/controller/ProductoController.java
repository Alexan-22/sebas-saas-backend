
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.service.ProductoService;
import com.tiendasebas.tiendasebas.service.ProductoEliminacionDefinitivaService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    private final ProductoEliminacionDefinitivaService
            eliminacionDefinitivaService;

    public ProductoController(
            ProductoService productoService,
            ProductoEliminacionDefinitivaService eliminacionDefinitivaService
    ) {
        this.productoService = productoService;
        this.eliminacionDefinitivaService =
                eliminacionDefinitivaService;
    }

    // ===============================
    // PUBLICO - LISTAR
    // ===============================

    @GetMapping("/public")
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok(
                productoService.listar()
        );
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<Producto> buscar(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                productoService.buscar(id)
        );
    }

    // ===============================
    // PUBLICO - FILTROS
    // ===============================

    @GetMapping("/public/genero/{idGenero}")
    public ResponseEntity<List<Producto>> listarPorGenero(
            @PathVariable Integer idGenero
    ) {
        return ResponseEntity.ok(
                productoService.listarPorGenero(idGenero)
        );
    }

    @GetMapping("/public/categoria/{idCategoria}")
    public ResponseEntity<List<Producto>> listarPorCategoria(
            @PathVariable Integer idCategoria
    ) {
        return ResponseEntity.ok(
                productoService.listarPorCategoria(idCategoria)
        );
    }

    @GetMapping("/public/filtro")
    public ResponseEntity<List<Producto>> filtrar(
            @RequestParam Integer idGenero,
            @RequestParam Integer idCategoria
    ) {
        return ResponseEntity.ok(
                productoService.filtrar(idGenero, idCategoria)
        );
    }

    @GetMapping("/public/buscar")
    public ResponseEntity<List<Producto>> buscarPorNombre(
            @RequestParam String q
    ) {
        return ResponseEntity.ok(
                productoService.buscarPorNombre(q)
        );
    }

    @GetMapping("/public/destacados")
    public ResponseEntity<List<Producto>> destacados() {
        return ResponseEntity.ok(
                productoService.listarDestacados()
        );
    }

    @GetMapping("/public/nuevos")
    public ResponseEntity<List<Producto>> nuevos() {
        return ResponseEntity.ok(
                productoService.listarNuevos()
        );
    }

    @GetMapping("/public/ofertas")
    public ResponseEntity<List<Producto>> ofertas() {
        return ResponseEntity.ok(
                productoService.listarOfertas()
        );
    }

    // ===============================
    // ADMIN - LISTAR Y CONSULTAR
    // ===============================

    @GetMapping("/admin")
    public ResponseEntity<List<Producto>> listarAdmin() {
        return ResponseEntity.ok(
                productoService.listarAdmin()
        );
    }

    @GetMapping("/admin/{id}")
    public ResponseEntity<Producto> buscarAdmin(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                productoService.buscarAdmin(id)
        );
    }

    // ===============================
    // ADMIN - CREAR
    // ===============================

    @PostMapping("/admin")
    public ResponseEntity<Producto> guardar(
            @RequestBody Producto producto,
            @RequestParam Integer idGenero,
            @RequestParam Integer idCategoria
    ) {
        return ResponseEntity.ok(
                productoService.guardar(
                        producto,
                        idGenero,
                        idCategoria
                )
        );
    }

    // ===============================
    // ADMIN - ACTUALIZAR
    // ===============================

    @PutMapping("/admin/{id}")
    public ResponseEntity<Producto> actualizar(
            @PathVariable Integer id,
            @RequestBody Producto producto,
            @RequestParam Integer idGenero,
            @RequestParam Integer idCategoria
    ) {
        return ResponseEntity.ok(
                productoService.actualizar(
                        id,
                        producto,
                        idGenero,
                        idCategoria
                )
        );
    }

    // ===============================
    // ADMIN - DESACTIVAR
    // ===============================

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<String> eliminar(
            @PathVariable Integer id
    ) {
        productoService.eliminar(id);

        return ResponseEntity.ok(
                "Producto desactivado correctamente"
        );
    }

    // ===============================
    // ADMIN - ELIMINAR DEFINITIVAMENTE
    // ===============================

    @DeleteMapping("/admin/{id}/definitivo")
    public ResponseEntity<Void> eliminarDefinitivamente(
            @PathVariable Integer id
    ) {
        eliminacionDefinitivaService
                .eliminarDefinitivamente(id);

        return ResponseEntity.noContent().build();
    }
}
