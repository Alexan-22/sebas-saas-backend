package com.tiendasebas.tiendasebas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Usuarios", uniqueConstraints = @UniqueConstraint(name="usuarios_correo_tienda_unique", columnNames={"tienda_id","correo"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    @Version
    @JsonIgnore
    @Column(nullable=false,columnDefinition="bigint default 0")
    private Long revision;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 150)
    private String correo;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    @ToString.Exclude
    private String passwordHash;

    @JsonIgnore
    @Column(name = "version_sesion", length = 36)
    @ToString.Exclude
    private String versionSesion;

    @JsonIgnore
    public String getMaterialSesion() {
        return versionSesion == null ? passwordHash : passwordHash + ":" + versionSesion;
    }

    public void revocarSesiones() {
        versionSesion = java.util.UUID.randomUUID().toString();
    }

    @ManyToOne
    @JoinColumn(name = "IdRol", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private Boolean estado = true;

    //Mapeo de tienda_id en Entidades Existentes
    @Column(name = "tienda_id")
    private Long tiendaId;
}