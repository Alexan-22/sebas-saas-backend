package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ProductoImagenes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProductoImagen;

    @ManyToOne
    @JoinColumn(name = "IdProducto", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 255)
    private String imagen;

    @Column(nullable = false)
    private Integer orden = 0;
}