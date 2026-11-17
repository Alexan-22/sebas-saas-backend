package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ProductoTallas")
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
    @JoinColumn(name = "IdTalla", nullable = true)
    private Talla talla;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(name = "id_color")
    private Long idColor;
}