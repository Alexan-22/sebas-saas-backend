package com.tiendasebas.tiendasebas.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="auth_recuperaciones")
public class RecuperacionPassword {
 @Id @Column(length=64) public String digest;
 @Column(nullable=false) public Integer usuarioId;
 public Long tiendaId;
 @Column(nullable=false,length=100) public String versionCredenciales;
 @Column(nullable=false) public Instant venceEn;
 public Instant consumidaEn;
}
