-- Migracion reconciliada: las tablas pueden existir previamente en Supabase.
-- IMPORTANTE: no garantiza aislamiento si el backend conecta con postgres/BYPASSRLS.
-- Validar contra PostgreSQL aislado y roles de aplicacion restringidos antes de merge.
BEGIN;
-- CHECKOUT INVITADO MULTITIENDA
-- No recrea tablas preexistentes; requiere comprobar su esquema real.

CREATE TABLE IF NOT EXISTS public.venta_clientes_invitados (
    tienda_id BIGINT NOT NULL,
    id_venta INTEGER NOT NULL,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    telefono VARCHAR(9) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tienda_id, id_venta),

    FOREIGN KEY (tienda_id, id_venta)
        REFERENCES public.ventas(tienda_id, id_venta),

    CHECK (length(trim(nombres)) > 0),
    CHECK (length(trim(apellidos)) > 0),
    CHECK (telefono ~ '^9[0-9]{8}$')
);

-- Los pagos actuales conservan sus usuarios.
-- Las nuevas operaciones invitadas pueden no tener usuario registrado.

ALTER TABLE public.yape_pagos_manuales
    ALTER COLUMN usuario_id DROP NOT NULL;

ALTER TABLE public.transferencia_pagos_manuales
    ALTER COLUMN usuario_id DROP NOT NULL;

ALTER TABLE public.transferencia_comprobantes
    ALTER COLUMN usuario_id DROP NOT NULL;

-- Idempotencia independiente del usuario registrado.

CREATE TABLE IF NOT EXISTS public.checkout_invitado_reintentos (
    tienda_id BIGINT NOT NULL,
    clave UUID NOT NULL,
    huella VARCHAR(64) NOT NULL,
    id_venta INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tienda_id, clave),

    UNIQUE (tienda_id, id_venta),

    FOREIGN KEY (tienda_id, id_venta)
        REFERENCES public.venta_clientes_invitados(tienda_id, id_venta),

    CHECK (huella ~ '^[0-9a-f]{64}$')
);

-- Credencial de consulta de pedidos.
-- Solo se guarda su hash, nunca el token en texto.

CREATE TABLE IF NOT EXISTS public.checkout_invitado_accesos (
    tienda_id BIGINT NOT NULL,
    id_venta INTEGER NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (tienda_id, id_venta),

    UNIQUE (token_hash),

    FOREIGN KEY (tienda_id, id_venta)
        REFERENCES public.venta_clientes_invitados(tienda_id, id_venta),

    CHECK (vence_en > creado_en)
);

DO $rls$
DECLARE nombre text;
BEGIN
  FOREACH nombre IN ARRAY ARRAY[
    'venta_clientes_invitados',
    'checkout_invitado_reintentos',
    'checkout_invitado_accesos'
  ] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', nombre);
    IF NOT EXISTS (
      SELECT 1 FROM pg_policies
      WHERE schemaname = 'public' AND tablename = nombre
        AND policyname = 'checkout_invitado_tenant'
    ) THEN
      EXECUTE format($policy$
        CREATE POLICY checkout_invitado_tenant ON public.%I FOR ALL
        USING (tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint)
        WITH CHECK (tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint)
      $policy$, nombre);
    END IF;
    -- Una política permisiva previa puede coexistir con la del candidato.
    -- La política restrictiva mantiene el límite del tenant en todos los casos.
    EXECUTE format('DROP POLICY IF EXISTS checkout_invitado_limite_tenant ON public.%I', nombre);
    EXECUTE format($policy$
      CREATE POLICY checkout_invitado_limite_tenant ON public.%I AS RESTRICTIVE FOR ALL
      USING (tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint)
      WITH CHECK (tienda_id = NULLIF(current_setting('app.tenant_id', true), '')::bigint)
    $policy$, nombre);
  END LOOP;
END
$rls$;
COMMIT;
