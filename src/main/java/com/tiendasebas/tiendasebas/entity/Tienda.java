package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// ============================================
// SEBAS COLLECTION SAAS
// ENTIDAD TIENDA MULTITENANT
// ============================================

@Entity
@Table(name = "tiendas")
@Getter
@Setter
public class Tienda {

    // ========================================
    // IDENTIFICADOR
    // ========================================

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    // ========================================
    // NOMBRE COMERCIAL
    // ========================================

    @Column(
            nullable = false,
            length = 100
    )
    private String nombre;

    // ========================================
    // SUBDOMINIO UNICO
    // ========================================

    @Column(
            nullable = false,
            unique = true,
            length = 50
    )
    private String subdominio;

    // ========================================
    // PERSONALIZACION - COLOR
    // ========================================

    @Column(
            name = "color_primario",
            length = 20
    )
    private String colorPrimario = "#000000";

    // ========================================
    // PERSONALIZACION - LOGO
    // ========================================

    @Column(
            name = "logo_url",
            columnDefinition = "TEXT"
    )
    private String logoUrl;

    @Column(name = "color_secundario", length = 20)
    private String colorSecundario = "#FFFFFF";

    @Column(name = "telefono_soporte", length = 20)
    private String telefonoSoporte;

    @Column(name = "whatsapp", length = 20)
    private String whatsapp;

    @Column(name = "descripcion_comercial", length = 500)
    private String descripcionComercial;

    // ========================================
    // ESTADO DE LA TIENDA
    // ========================================
    //
    // ACTIVA:
    //   Tienda habilitada.
    //
    // PENDIENTE:
    //   Esperando aprobacion.
    //
    // SUSPENDIDA:
    //   Acceso operativo restringido.
    //
    // Los valores se validan mediante
    // la restriccion CHECK de PostgreSQL.
    // Las reglas de acceso se implementaran
    // en el backend posteriormente.

    @Column(
            name = "estado",
            nullable = false,
            length = 12
    )
    private String estado = "ACTIVA";

    // ========================================
    // FECHA ORIGINAL DE REGISTRO
    // ========================================

    @Column(
            name = "creado_en",
            insertable = false,
            updatable = false
    )
    private LocalDateTime creadoEn;
}
