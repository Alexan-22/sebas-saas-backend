package com.tiendasebas.tiendasebas.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="auth_limites")
public class LimiteAutenticacion {
 @Id @Column(length=64) public String clave;
 @Column(nullable=false) public Instant inicio;
 @Column(nullable=false) public int intentos;
}
