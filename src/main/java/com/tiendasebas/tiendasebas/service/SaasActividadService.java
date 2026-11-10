package com.tiendasebas.tiendasebas.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SaasActividadService {

    private final JdbcTemplate jdbc;

    public void registrar(String accion, String entidad, Object entidadId, String descripcion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String usuario = auth != null && auth.isAuthenticated()
                ? auth.getName() : "SISTEMA";

        jdbc.update("""
            INSERT INTO public.saas_actividad
                (usuario, accion, entidad, entidad_id, descripcion)
            VALUES (?, ?, ?, ?, ?)
            """,
                usuario,
                accion,
                entidad,
                entidadId == null ? null : entidadId.toString(),
                descripcion
        );
    }
}
