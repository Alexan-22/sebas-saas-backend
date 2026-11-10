package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    // ============================================================
    // PÚBLICO
    // ============================================================
    @GetMapping("/public")
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok(productoService.listar());
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<Producto> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.buscar(id));
    }

    @GetMapping("/public/genero/{idGenero}")
    public ResponseEntity<List<Producto>> listarPorGenero(@PathVariable Integer idGenero) {
        return ResponseEntity.ok(productoService.listarPorGenero(idGenero));
    }

    @GetMapping("/public/categoria/{idCategoria}")
    public ResponseEntity<List<Producto>> listarPorCategoria(@PathVariable Integer idCategoria) {
        return ResponseEntity.ok(productoService.listarPorCategoria(idCategoria));
    }

    // ============================================================
    // GÉNERO + CATEGORÍA
    // ============================================================
    @GetMapping("/public/filtro")
    public ResponseEntity<List<Producto>> filtrar(@RequestParam Integer idGenero, @RequestParam Integer idCategoria) {
        return ResponseEntity.ok(productoService.filtrar(idGenero, idCategoria));
    }

    // ============================================================
    // BUSCADOR
    // ============================================================
    @GetMapping("/public/buscar")
    public ResponseEntity<List<Producto>> buscarPorNombre(@RequestParam String q) {
        return ResponseEntity.ok(productoService.buscarPorNombre(q));
    }

    // ============================================================
    // DESTACADOS
    // ============================================================
    @GetMapping("/public/destacados")
    public ResponseEntity<List<Producto>> destacados() {
        return ResponseEntity.ok(productoService.listarDestacados());
    }

    // ============================================================
    // NUEVOS
    // ============================================================
    @GetMapping("/public/nuevos")
    public ResponseEntity<List<Producto>> nuevos() {
        return ResponseEntity.ok(productoService.listarNuevos());
    }

    // ============================================================
    // OFERTAS
    // ============================================================
    @GetMapping("/public/ofertas")
    public ResponseEntity<List<Producto>> ofertas() {
        return ResponseEntity.ok(productoService.listarOfertas());
    }

    // ============================================================
    // ADMIN - REGISTRAR
    // ============================================================
    @GetMapping("/admin")
    public ResponseEntity<List<Producto>> listarAdmin() {
        return ResponseEntity.ok(productoService.listarAdmin());
    }

    @GetMapping("/admin/{id}")
    public ResponseEntity<Producto> buscarAdmin(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.buscarAdmin(id));
    }

    @PostMapping("/admin")
    public ResponseEntity<Producto> guardar(@RequestBody Producto producto, @RequestParam Integer idGenero, @RequestParam Integer idCategoria) {
        return ResponseEntity.ok(productoService.guardar(producto, idGenero, idCategoria));
    }

    // ============================================================
    // ADMIN - ACTUALIZAR
    // ============================================================
    @PutMapping("/admin/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable Integer id, @RequestBody Producto producto,
                                               @RequestParam Integer idGenero, @RequestParam Integer idCategoria) {
        return ResponseEntity.ok(productoService.actualizar(id, producto, idGenero, idCategoria));
    }

    // ============================================================
    // ADMIN - ELIMINAR
    // ============================================================
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.ok("Producto eliminado correctamente");
    }
}