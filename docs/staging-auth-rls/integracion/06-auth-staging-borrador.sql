-- SEBAS COLLECTION SAAS V2
-- BORRADOR AUTH RLS PARA REVISION
-- NO EJECUTAR EN SUPABASE TODAVIA.
-- Requiere rol sebas_auth_app, validacion de esquema,
-- ACL y pruebas de aislamiento con identidad LOGIN.
-- No incluye permisos sobre inventario ni catalogo.

BEGIN;

GRANT USAGE ON SCHEMA public TO sebas_auth_app;

-- Acceso limitado a tablas de autenticacion
GRANT SELECT, INSERT, UPDATE
ON public.usuarios,
   public.auth_sesiones,
   public.auth_recuperaciones,
   public.auth_limites
TO sebas_auth_app;

GRANT SELECT
ON public.roles
TO sebas_auth_app;

-- Alta de usuarios
GRANT USAGE
ON SEQUENCE public.usuarios_id_usuario_seq
TO sebas_auth_app;

-- Usuarios pertenecientes a la boutique
CREATE POLICY auth_usuario_tenant
ON public.usuarios
TO sebas_auth_app
USING (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
)
WITH CHECK (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
    AND EXISTS (
        SELECT 1 FROM public.roles r
        WHERE r.id_rol = usuarios.id_rol
    )
);

-- Roles ordinarios de boutique
CREATE POLICY auth_roles_ordinarios
ON public.roles
FOR SELECT
TO sebas_auth_app
USING (
    nombre_rol IN ('ADMIN', 'EMPLEADO', 'CLIENTE')
);

-- Sesiones de la boutique
CREATE POLICY auth_sesion_tenant
ON public.auth_sesiones
TO sebas_auth_app
USING (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
)
WITH CHECK (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
    AND EXISTS (
        SELECT 1 FROM public.usuarios u
        WHERE u.id_usuario = auth_sesiones.usuario_id
          AND u.tienda_id = auth_sesiones.tienda_id
    )
);

-- Recuperacion de contrasenas
CREATE POLICY auth_recuperacion_tenant
ON public.auth_recuperaciones
TO sebas_auth_app
USING (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
)
WITH CHECK (
    tienda_id =
    NULLIF(current_setting('app.tenant_id', true), '')::bigint
    AND EXISTS (
        SELECT 1 FROM public.usuarios u
        WHERE u.id_usuario = auth_recuperaciones.usuario_id
          AND u.tienda_id = auth_recuperaciones.tienda_id
    )
);

-- Limites de autenticacion por clave HMAC
CREATE POLICY auth_limite_clave
ON public.auth_limites
TO sebas_auth_app
USING (
    clave =
    NULLIF(current_setting('app.auth_rate_key', true), '')
)
WITH CHECK (
    clave =
    NULLIF(current_setting('app.auth_rate_key', true), '')
);

COMMIT;