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

-- Lectura de usuarios dentro de su boutique.
CREATE POLICY auth_usuario_select
ON public.usuarios
FOR SELECT TO sebas_auth_app
USING (
    tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint
);

-- Registro publico: solo usuarios CLIENTE.
CREATE POLICY auth_usuario_insert
ON public.usuarios
FOR INSERT TO sebas_auth_app
WITH CHECK (
    tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint
    AND EXISTS (
        SELECT 1 FROM public.roles r
        WHERE r.id_rol = usuarios.id_rol
          AND r.nombre_rol = 'CLIENTE'
    )
);

-- Actualizaciones limitadas a la boutique.
CREATE POLICY auth_usuario_update
ON public.usuarios
FOR UPDATE TO sebas_auth_app
USING (
    tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint
)
WITH CHECK (
    tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint
);

-- Impedir cambios de identidad y privilegios durante la autenticacion.
CREATE FUNCTION public.auth_usuario_identidad_inmutable()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $$
BEGIN
    IF pg_has_role(current_user, 'sebas_auth_app', 'USAGE')
       AND (
           NEW.id_usuario IS DISTINCT FROM OLD.id_usuario
           OR NEW.id_rol IS DISTINCT FROM OLD.id_rol
           OR NEW.tienda_id IS DISTINCT FROM OLD.tienda_id
           OR NEW.estado IS DISTINCT FROM OLD.estado
       )
    THEN
        RAISE EXCEPTION 'Cambio de identidad no autorizado'
            USING ERRCODE = '42501';
    END IF;

    RETURN NEW;
END;
$$;

REVOKE ALL
ON FUNCTION public.auth_usuario_identidad_inmutable()
FROM PUBLIC;

CREATE TRIGGER auth_usuario_identidad_protegida
BEFORE UPDATE ON public.usuarios
FOR EACH ROW
EXECUTE FUNCTION public.auth_usuario_identidad_inmutable();
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