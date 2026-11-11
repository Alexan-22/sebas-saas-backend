package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.VentaResponse;
import com.tiendasebas.tiendasebas.dto.DetalleCompraResponse;
import com.tiendasebas.tiendasebas.service.VentaService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rbac/empleado/ventas")
public class RbacVentasEmpleadoController {

    private final VentaService ventaService;

    public RbacVentasEmpleadoController(
            VentaService ventaService
    ) {
        this.ventaService = ventaService;
    }

    @GetMapping
    @PreAuthorize(
        "hasRole('EMPLEADO') and " +
        "@rbacAutorizacionService.tienePermiso(authentication, 'ventas.ver')"
    )
    public List<VentaResponse> listarVentas() {
        return ventaService.listarTodas();
    }

    @GetMapping("/{idVenta}/detalle")
    @PreAuthorize(
        "hasRole('EMPLEADO') and " +
        "@rbacAutorizacionService.tienePermiso(authentication, 'ventas.ver')"
    )
    public DetalleCompraResponse detalleVenta(
            @PathVariable Integer idVenta
    ) {
        return ventaService.obtenerDetalleVentaAdmin(idVenta);
    }
}