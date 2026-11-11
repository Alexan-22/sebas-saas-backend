-- RBAC MULTI-TENANT - FASE 1
-- Solo estructuras nuevas. No modifica usuarios ni pagos.

CREATE TABLE public.rbac_permisos (
    codigo VARCHAR(100) PRIMARY KEY,
    descripcion VARCHAR(200) NOT NULL
);

CREATE TABLE public.rbac_roles (
    id BIGSERIAL PRIMARY KEY,
    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id) CHECK (tienda_id > 0),
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tienda_id, nombre),
    UNIQUE (tienda_id, id)
);

CREATE TABLE public.rbac_roles_permisos (
    tienda_id BIGINT NOT NULL,
    rol_id BIGINT NOT NULL,
    permiso_codigo VARCHAR(100) NOT NULL,

    PRIMARY KEY (tienda_id, rol_id, permiso_codigo),

    FOREIGN KEY (tienda_id, rol_id)
        REFERENCES public.rbac_roles(tienda_id, id)
        ON DELETE CASCADE,

    FOREIGN KEY (permiso_codigo)
        REFERENCES public.rbac_permisos(codigo)
);

INSERT INTO public.rbac_permisos
    (codigo, descripcion)
VALUES
    ('productos.ver', 'Consultar productos'),
    ('productos.editar', 'Crear y editar productos'),
    ('stock.ver', 'Consultar inventario'),
    ('stock.editar', 'Modificar inventario'),
    ('ventas.ver', 'Consultar ventas'),
    ('pedidos.gestionar', 'Gestionar pedidos'),
    ('yape.ver', 'Consultar pagos Yape'),
    ('yape.aprobar', 'Aprobar pagos Yape'),
    ('transferencias.ver', 'Consultar transferencias'),
    ('transferencias.aprobar', 'Aprobar transferencias'),
    ('usuarios.ver', 'Consultar usuarios'),
    ('usuarios.gestionar', 'Gestionar empleados'),
    ('reportes.ver', 'Consultar reportes'),
    ('tienda.configurar', 'Configurar tienda');