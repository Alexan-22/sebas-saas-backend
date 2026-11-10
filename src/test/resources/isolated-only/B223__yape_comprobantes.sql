-- B.2.2.3: Evidencias privadas del pago Yape; una imagen inmutable por pedido.
-- Seleccionar siguiente version Flyway libre y cargar SOLO en PostgreSQL desechable primero.
-- Nunca ejecutar esta migracion directamente sobre Supabase comercial.
CREATE TABLE public.yape_comprobantes (
    tienda_id BIGINT NOT NULL,
    id_venta INTEGER NOT NULL,
    usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
    imagen BYTEA NOT NULL CHECK (octet_length(imagen) BETWEEN 1 AND 5242880),
    mime_tipo VARCHAR(20) NOT NULL CHECK (mime_tipo IN ('image/png', 'image/jpeg')),
    tamano_bytes INTEGER NOT NULL CHECK (tamano_bytes BETWEEN 1 AND 5242880),
    sha256 CHAR(64) NOT NULL,
    enviado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT yape_comprobante_pk PRIMARY KEY (tienda_id, id_venta),
    CONSTRAINT yape_comprobante_pago_misma_tienda_fk
        FOREIGN KEY (tienda_id, id_venta)
        REFERENCES public.yape_pagos_manuales(tienda_id, id_venta)
);
CREATE INDEX yape_comprobantes_usuario_idx
    ON public.yape_comprobantes (tienda_id, usuario_id, enviado_en DESC);
