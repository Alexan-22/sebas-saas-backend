package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.service.ProductoService;
import com.tiendasebas.tiendasebas.service.ProductoTallaService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rbac/empleado")
@PreAuthorize("hasRole('EMPLEADO')")
public class RbacCatalogoEmpleadoController {

    private final ProductoService productos;
    private final ProductoTallaService inventario;

    public RbacCatalogoEmpleadoController(
            ProductoService productos,
            ProductoTallaService inventario
    ) {
        this.productos = productos;
        this.inventario = inventario;
    }

    @GetMapping("/productos")
    @PreAuthorize(
        "hasRole('EMPLEADO') and @rbacAutorizacionService.tienePermiso(authentication, 'productos.ver')"
    )
    public List<Producto> listarProductos() {
        return productos.listarAdmin();
    }

    @GetMapping("/productos/{id}")
    @PreAuthorize(
        "hasRole('EMPLEADO') and @rbacAutorizacionService.tienePermiso(authentication, 'productos.ver')"
    )
    public Producto buscarProducto(@PathVariable Integer id) {
        return productos.buscarAdmin(id);
    }

    @GetMapping("/stock")
    @PreAuthorize(
        "hasRole('EMPLEADO') and @rbacAutorizacionService.tienePermiso(authentication, 'stock.ver')"
    )
    public List<ProductoTalla> listarStock() {
        return inventario.listarInventarioCompleto();
    }
}