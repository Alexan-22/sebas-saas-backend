package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.ProductoImagen;
import com.tiendasebas.tiendasebas.service.ProductoImagenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/producto-imagenes")
public class ProductoImagenController {

    private final ProductoImagenService productoImagenService;

    public ProductoImagenController(ProductoImagenService productoImagenService) {
        this.productoImagenService = productoImagenService;
    }

    // ============================================================
    // PÚBLICO
    // ============================================================
    @GetMapping("/producto/{idProducto}")
    public ResponseEntity<List<ProductoImagen>> listar(@PathVariable Integer idProducto) {
        return ResponseEntity.ok(productoImagenService.listarPorProducto(idProducto));
    }

    @GetMapping("/admin/producto/{idProducto}")
    public ResponseEntity<List<ProductoImagen>> listarAdmin(@PathVariable Integer idProducto) {
        return ResponseEntity.ok(productoImagenService.listarPorProductoAdmin(idProducto));
    }

    // ============================================================
    // ADMIN - SUBIR IMAGEN
    // ============================================================
    @PostMapping(value = "/admin/producto/{idProducto}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductoImagen> subir(@PathVariable Integer idProducto, @RequestParam("imagen") MultipartFile imagen,
                                                @RequestParam(defaultValue = "0") Integer orden) {
        return ResponseEntity.ok(productoImagenService.subir(idProducto, imagen, orden));
    }

    // ============================================================
    // ADMIN - ELIMINAR
    // ============================================================
    @DeleteMapping("/admin/{idImagen}")
    public ResponseEntity<String> eliminar(@PathVariable Integer idImagen) {
        productoImagenService.eliminar(idImagen);
        return ResponseEntity.ok("Imagen eliminada correctamente");
    }
}
