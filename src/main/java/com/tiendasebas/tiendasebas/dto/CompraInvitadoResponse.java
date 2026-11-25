package com.tiendasebas.tiendasebas.dto;

/** El token se entrega una sola vez; no se devuelve en reintentos. */
public record CompraInvitadoResponse(CompraResponse compra, String tokenAcceso) {}