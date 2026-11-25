package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CompraInvitadoRequest extends CompraRequest {

    @Valid
    @NotNull(message = "Los datos del comprador son obligatorios")
    private ClienteInvitadoDto cliente;

}