-- Ejecutar una sola vez en la base Supabase correcta.
-- No toca pagos, pedidos, usuarios ni otras tablas.
ALTER TABLE public.saas_cabecera_config
    ADD COLUMN IF NOT EXISTS promo_modo VARCHAR(16) NOT NULL DEFAULT 'ROTACION';

ALTER TABLE public.saas_cabecera_config
    DROP CONSTRAINT IF EXISTS ck_cabecera_velocidad;
ALTER TABLE public.saas_cabecera_config
    ADD CONSTRAINT ck_cabecera_velocidad
    CHECK (promo_velocidad IN ('ESTATICA','LENTA','NORMAL','RAPIDA','MUY_RAPIDA'));

ALTER TABLE public.saas_cabecera_config
    DROP CONSTRAINT IF EXISTS ck_cabecera_modo;
ALTER TABLE public.saas_cabecera_config
    ADD CONSTRAINT ck_cabecera_modo
    CHECK (promo_modo IN ('ROTACION','DESPLAZAMIENTO'));
