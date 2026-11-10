package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String nombre;
    private String correo;
    private String rol;
}