package com.tiendasebas.tiendasebas.dto;

public record GuardarColorTiendaRequest(
        String nombre,
        String codigoHex,
        Boolean activo
) {}
