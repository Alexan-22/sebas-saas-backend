-- Revision requerida antes de produccion. No elimina ni modifica usuarios.
-- Alinea unicidad de correo con el login por tenant; conserva unicidad global para SUPER_ADMIN.
BEGIN;
DO $preflight$
BEGIN
 IF EXISTS (SELECT 1 FROM public.usuarios GROUP BY tienda_id,lower(btrim(correo)) HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Existen correos ambiguos dentro del mismo ambito; revisar antes de migrar';
 END IF;
END $preflight$;
CREATE UNIQUE INDEX IF NOT EXISTS usuarios_correo_tienda_ci ON public.usuarios(tienda_id,lower(btrim(correo))) WHERE tienda_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS usuarios_correo_global_ci ON public.usuarios(lower(btrim(correo))) WHERE tienda_id IS NULL;
-- Retirar solo restricciones UNIQUE globales de una columna correo, despues de proteger ambos ambitos.
-- Sin CASCADE: cualquier dependencia inesperada bloquea toda la transaccion para revision.
DO $constraints$
DECLARE constraint_name text;
BEGIN
 FOR constraint_name IN
  SELECT c.conname FROM pg_constraint c
  JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
  WHERE c.conrelid='public.usuarios'::regclass AND c.contype='u'
    AND array_length(c.conkey,1)=1 AND a.attname='correo'
 LOOP
  EXECUTE format('ALTER TABLE public.usuarios DROP CONSTRAINT %I',constraint_name);
 END LOOP;
END $constraints$;
COMMIT;
