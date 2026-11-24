package com.tiendasebas.tiendasebas.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PaginasAyuda {

    private PaginasAyuda() {}

    public enum Clave {
        CAMBIOS,
        ENVIOS,
        PREGUNTAS,
        TERMINOS,
        PRIVACIDAD,
        RECLAMACIONES
    }

    public record Pagina(
        Clave clave,
        String titulo,
        String contenido,
        boolean visible
    ) {}

    public static List<Pagina> validar(List<Pagina> entrada) {
        if (entrada == null) {
            return List.of();
        }

        if (entrada.size() > Clave.values().length) {
            throw new IllegalArgumentException(
                "Demasiadas paginas informativas"
            );
        }

        Set<Clave> claves = new HashSet<>();
        List<Pagina> resultado = new ArrayList<>();

        for (Pagina pagina : entrada) {
            if (pagina == null || pagina.clave() == null) {
                throw new IllegalArgumentException(
                    "Pagina informativa invalida"
                );
            }

            if (!claves.add(pagina.clave())) {
                throw new IllegalArgumentException(
                    "No se permiten paginas duplicadas"
                );
            }

            String titulo = pagina.titulo() == null
                ? "" : pagina.titulo().strip();

            String contenido = pagina.contenido() == null
                ? "" : pagina.contenido().strip();

            if (titulo.length() > 100 ||
                contenido.length() > 12000) {
                throw new IllegalArgumentException(
                    "Contenido de pagina demasiado largo"
                );
            }

            if (pagina.visible() &&
                (titulo.isBlank() || contenido.isBlank())) {
                throw new IllegalArgumentException(
                    "Una pagina visible necesita titulo y contenido"
                );
            }

            resultado.add(new Pagina(
                pagina.clave(),
                titulo,
                contenido,
                pagina.visible()
            ));
        }

        return List.copyOf(resultado);
    }
}