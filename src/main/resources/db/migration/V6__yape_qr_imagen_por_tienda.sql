-- B.2.2.1 - QR por tienda. No ejecutar sobre Supabase productivo.
-- V5 debe existir previamente. Flyway aplicara V6 solamente en un entorno autorizado.
CREATE TABLE public.yape_qr_imagen (
    tienda_id BIGINT PRIMARY KEY REFERENCES public.tiendas(id) ON DELETE CASCADE,
    imagen BYTEA NOT NULL,
    tipo_mime VARCHAR(16) NOT NULL DEFAULT 'image/png' CHECK (tipo_mime = 'image/png'),
    tamano_bytes INTEGER NOT NULL CHECK (tamano_bytes > 0 AND tamano_bytes <= 2097152),
    sha256 CHAR(64) NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
