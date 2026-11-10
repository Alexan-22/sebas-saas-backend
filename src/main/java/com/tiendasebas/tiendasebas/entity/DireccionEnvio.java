package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "direcciones_envio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(nullable = false, length = 100)
    private String distrito;

    @Column(nullable = false, length = 255)
    private String direccionExacta;

    @Column(length = 255)
    private String referencia;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costoDelivery;
}