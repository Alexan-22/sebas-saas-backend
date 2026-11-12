
package com.tiendasebas.tiendasebas.dto;

public record PerfilResponse(
        Integer idUsuario,
        String nombre,
        String apellido,
        String correo,
        String rol,
        Long tiendaId,
        Boolean estado
) {}
