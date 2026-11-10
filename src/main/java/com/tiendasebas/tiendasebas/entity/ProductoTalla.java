package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ProductoTallas", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_Producto_Talla", columnNames = {"IdProducto", "IdTalla"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoTalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProductoTalla;

    @ManyToOne
    @JoinColumn(name = "IdProducto", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "IdTalla", nullable = false)
    private Talla talla;

    @Column(nullable = false)
    private Integer stock = 0;
}