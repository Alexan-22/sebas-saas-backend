package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "DetalleVenta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDetalleVenta;

    @ManyToOne
    @JoinColumn(name = "IdVenta", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "IdProductoTalla", nullable = false)
    private ProductoTalla productoTalla;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}