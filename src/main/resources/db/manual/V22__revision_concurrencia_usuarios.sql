-- Preparada para revision; aplicar solo en entorno autorizado.
-- No cambia hashes ni contrasenas. Evita que una entidad antigua restaure credenciales.
BEGIN;
ALTER TABLE public.usuarios ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 0;
COMMIT;
