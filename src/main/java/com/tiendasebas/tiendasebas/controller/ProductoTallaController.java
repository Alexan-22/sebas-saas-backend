package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.service.ProductoTallaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/producto-tallas")
public class ProductoTallaController {

    private final ProductoTallaService productoTallaService;

    public ProductoTallaController(ProductoTallaService productoTallaService) {
        this.productoTallaService = productoTallaService;
    }

    // =========================================================
    // PÚBLICO - TALLAS Y STOCK DE UN PRODUCTO
    // =========================================================
    @GetMapping("/producto/{idProducto}")
    public ResponseEntity<List<ProductoTalla>> listarPorProducto(@PathVariable Integer idProducto) {
        return ResponseEntity.ok(productoTallaService.listarPorProducto(idProducto));
    }

    // =========================================================
    // ADMIN - INVENTARIO COMPLETO PARA REPORTES
    // =========================================================
    @GetMapping("/admin/reportes/inventario")
    public ResponseEntity<List<ProductoTalla>> listarInventarioCompleto() {
        return ResponseEntity.ok(productoTallaService.listarInventarioCompleto());
    }

    // =========================================================
    // ADMIN - REGISTRAR TALLA PARA PRODUCTO
    // =========================================================
    @PostMapping("/admin")
    public ResponseEntity<ProductoTalla> registrar(@RequestParam Integer idProducto, @RequestParam Integer idTalla, @RequestParam Integer stock) {
        return ResponseEntity.ok(productoTallaService.registrar(idProducto, idTalla, stock));
    }

    // =========================================================
    // ADMIN - ACTUALIZAR STOCK
    // =========================================================
    @PutMapping("/admin/{id}/stock")
    public ResponseEntity<ProductoTalla> actualizarStock(@PathVariable Integer id, @RequestParam Integer stock) {
        return ResponseEntity.ok(productoTallaService.actualizarStock(id, stock));
    }
}