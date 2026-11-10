package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Tallas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Talla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTalla;

    @Column(nullable = false, unique = true, length = 10)
    private String nombreTalla;

    @Column(nullable = false)
    private Boolean estado = true;
}