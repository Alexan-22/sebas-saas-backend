package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Categorias")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCategoria;

    @Column(nullable = false, length = 100)
    private String nombreCategoria;

    @Column(nullable = false)
    private Boolean estado = true;

    //Mapeo de tienda_id en Entidades Existentes
    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;
}