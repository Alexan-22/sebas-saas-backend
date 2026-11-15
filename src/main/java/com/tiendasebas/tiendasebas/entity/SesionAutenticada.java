package com.tiendasebas.tiendasebas.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="auth_sesiones")
public class SesionAutenticada {
 @Id public UUID id;
 @Column(nullable=false) public Integer usuarioId;
 public Long tiendaId;
 @Column(nullable=false) public Instant creadaEn;
 @Column(nullable=false) public Instant ultimoUso;
 @Column(nullable=false) public Instant venceEn;
 @Column(nullable=false) public boolean revocada;
}
