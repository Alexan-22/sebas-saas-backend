-- Preparada para revision. No aplicar a Supabase desde esta tarea.
-- Requiere V18 y usuarios existentes. El backend debe usar un rol de servidor confiable.
BEGIN;
CREATE TABLE IF NOT EXISTS public.auth_sesiones (
 id uuid PRIMARY KEY, usuario_id integer NOT NULL REFERENCES public.usuarios(id_usuario),
 tienda_id bigint REFERENCES public.tiendas(id), creada_en timestamptz NOT NULL,
 ultimo_uso timestamptz NOT NULL, vence_en timestamptz NOT NULL, revocada boolean NOT NULL DEFAULT false,
 CONSTRAINT auth_sesion_tiempo CHECK(vence_en>creada_en AND ultimo_uso>=creada_en)
);
CREATE INDEX IF NOT EXISTS auth_sesiones_usuario_idx ON public.auth_sesiones(usuario_id);
CREATE INDEX IF NOT EXISTS auth_sesiones_vence_idx ON public.auth_sesiones(vence_en);
CREATE TABLE IF NOT EXISTS public.auth_recuperaciones (
 digest varchar(64) PRIMARY KEY, usuario_id integer NOT NULL REFERENCES public.usuarios(id_usuario),
 tienda_id bigint REFERENCES public.tiendas(id), version_credenciales varchar(100) NOT NULL, vence_en timestamptz NOT NULL, consumida_en timestamptz
);
CREATE INDEX IF NOT EXISTS auth_recuperaciones_usuario_idx ON public.auth_recuperaciones(usuario_id);
CREATE TABLE IF NOT EXISTS public.auth_limites (
 clave varchar(64) PRIMARY KEY, inicio timestamptz NOT NULL, intentos integer NOT NULL CHECK(intentos>=1)
);
-- Sin acceso directo a navegador/PostgREST. No se desactiva ninguna politica existente.
ALTER TABLE public.auth_sesiones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auth_recuperaciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auth_limites ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.auth_sesiones,public.auth_recuperaciones,public.auth_limites FROM PUBLIC;
DO $secure$
DECLARE role_name text;
BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
   EXECUTE format('REVOKE ALL ON public.auth_sesiones,public.auth_recuperaciones,public.auth_limites FROM %I',role_name);
  END IF;
 END LOOP;
END $secure$;
COMMIT;
