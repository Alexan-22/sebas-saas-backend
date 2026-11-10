package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Generos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Genero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idGenero;

    @Column(nullable = false, unique = true, length = 50)
    private String nombreGenero;

    @Column(nullable = false)
    private Boolean estado = true;
}