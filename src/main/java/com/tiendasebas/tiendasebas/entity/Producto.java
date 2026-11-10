package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "Productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProducto;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    // Precio normal
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    // Precio promocional
    // null = producto sin oferta
    @Column(precision = 12, scale = 2)
    private BigDecimal precioOferta;

    @ManyToOne
    @JoinColumn(name = "IdGenero", nullable = false)
    private Genero genero;

    @ManyToOne
    @JoinColumn(name = "IdCategoria", nullable = false)
    private Categoria categoria;

    // Imagen principal
    @Column(length = 255)
    private String imagen;

    // Producto nuevo
    @Column(nullable = false)
    private Boolean nuevo = false;

    // Mostrar producto en destacados del inicio
    @Column(nullable = false)
    private Boolean destacado = false;

    // Eliminación lógica
    @Column(nullable = false)
    private Boolean estado = true;

    //Mapeo de tienda_id en Entidades Existentes
    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;
}