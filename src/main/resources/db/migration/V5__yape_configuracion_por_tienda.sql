-- B.2.2.1 - SOLO aplicar en PostgreSQL desechable de desarrollo hasta validar.
-- Independiente de pagos_simulados y del proveedor SANDBOX.
CREATE TABLE public.yape_configuracion_tienda (
    tienda_id BIGINT PRIMARY KEY REFERENCES public.tiendas(id),
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    numero_yape VARCHAR(9),
    titular_yape VARCHAR(120),
    qr_url TEXT,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT yape_numero_formato_ck
        CHECK (numero_yape IS NULL OR numero_yape ~ '^9[0-9]{8}$'),
    CONSTRAINT yape_habilitado_completo_ck
        CHECK (NOT habilitado OR (
            numero_yape IS NOT NULL AND
            titular_yape IS NOT NULL AND length(btrim(titular_yape)) > 0 AND
            qr_url IS NOT NULL AND length(btrim(qr_url)) > 0
        ))
);
