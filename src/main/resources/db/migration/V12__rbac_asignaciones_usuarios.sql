-- RBAC MULTI-TENANT - FASE 2
-- Asignaciones de roles a usuarios de una misma tienda.
-- No se modifican pagos ni registros existentes.

-- Asegura que las referencias puedan verificar tienda y usuario
-- conjuntamente. No cambia los datos existentes.


CREATE TABLE public.rbac_usuarios_roles (
    tienda_id BIGINT NOT NULL,
    usuario_id INTEGER NOT NULL,
    rol_id BIGINT NOT NULL,
    asignado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (tienda_id, usuario_id, rol_id),

    CONSTRAINT fk_rbac_asignacion_rol
        FOREIGN KEY (tienda_id, rol_id)
        REFERENCES public.rbac_roles (tienda_id, id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rbac_asignacion_usuario
        FOREIGN KEY (tienda_id, usuario_id)
        REFERENCES public.usuarios (tienda_id, id_usuario)
        ON DELETE CASCADE
);

CREATE INDEX idx_rbac_usuario_roles
ON public.rbac_usuarios_roles (tienda_id, usuario_id);