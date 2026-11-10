package com.tiendasebas.tiendasebas.dto;

/** Configuracion publica sin datos privados de propietarios. */
public record TiendaPublicaResponse(
        Long id, String nombre, String subdominio,
        String colorPrimario, String logoUrl,
        String colorSecundario, String telefonoSoporte,
        String whatsapp, String descripcionComercial
) {}
