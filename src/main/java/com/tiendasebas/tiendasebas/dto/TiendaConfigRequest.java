package com.tiendasebas.tiendasebas.dto;

public record TiendaConfigRequest(
        String nombre, String colorPrimario,
        String colorSecundario, String telefonoSoporte,
        String whatsapp, String descripcionComercial
) {}
