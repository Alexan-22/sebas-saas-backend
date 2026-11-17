-- SEBAS COLLECTION SAAS V2
-- BORRADOR LOCAL - NO EJECUTAR EN SUPABASE
-- Roles sin LOGIN, sin BYPASSRLS y sin permisos sobre tablas.

BEGIN;

DO $$
DECLARE
    nombre text;
BEGIN
    FOREACH nombre IN ARRAY ARRAY[
        'sebas_auth_app',
        'sebas_global_auth',
        'sebas_global_ops'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_roles
            WHERE rolname = nombre
        ) THEN
            EXECUTE format(
                'CREATE ROLE %I NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT',
                nombre
            );
        END IF;
    END LOOP;
END
$$;

COMMIT;

SELECT
    rolname,
    rolcanlogin,
    rolbypassrls,
    rolsuper
FROM pg_roles
WHERE rolname IN (
    'sebas_auth_app',
    'sebas_global_auth',
    'sebas_global_ops'
)
ORDER BY rolname;
