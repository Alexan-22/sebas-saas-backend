-- SOLO POSTGRESQL TEMPORAL.
-- No ejecutar en Supabase Staging ni produccion.
-- Requiere instalar primero B025__auth_tenant_local.sql.
-- No crea contrasenas ni conexiones externas.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'sebas_auth_app'
    ) THEN
        RAISE EXCEPTION
            'Primero debe existir el rol sebas_auth_app';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'sebas_boutique_jdbc_test'
    ) THEN
        CREATE ROLE sebas_boutique_jdbc_test
            NOLOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOBYPASSRLS
            INHERIT;
    END IF;
END
$$;

-- Heredar únicamente el subconjunto de autenticacion.
GRANT sebas_auth_app
    TO sebas_boutique_jdbc_test
    WITH INHERIT TRUE, SET FALSE;

-- Verificar la herencia de privilegios.
DO $$
BEGIN
    IF NOT pg_has_role(
        'sebas_boutique_jdbc_test',
        'sebas_auth_app',
        'USAGE'
    ) THEN
        RAISE EXCEPTION
            'La identidad JDBC no hereda permisos de autenticacion';
    END IF;
END
$$;

COMMIT;

-- Consultas de comprobacion.
SELECT
    rolname,
    rolcanlogin,
    rolsuper,
    rolbypassrls
FROM pg_roles
WHERE rolname IN (
    'sebas_auth_app',
    'sebas_boutique_jdbc_test'
)
ORDER BY rolname;