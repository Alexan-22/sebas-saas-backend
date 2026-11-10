package com.tiendasebas.tiendasebas.yape;

/** Los datos de este record pueden mostrarse al comprador SOLO si habilitado=true. */
public record YapeConfiguracion(
        boolean habilitado,
        String numeroYape,
        String titularYape,
        String qrUrl
) {
    public static YapeConfiguracion deshabilitada() {
        return new YapeConfiguracion(false, null, null, null);
    }
}
