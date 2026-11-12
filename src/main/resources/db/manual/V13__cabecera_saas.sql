-- Ejecutar UNA SOLA VEZ en Supabase SQL Editor. No modifica tiendas ni pagos.
CREATE TABLE IF NOT EXISTS public.saas_cabecera_config (
  tienda_id BIGINT PRIMARY KEY REFERENCES public.tiendas(id) ON DELETE CASCADE,
  promo_activa BOOLEAN NOT NULL DEFAULT TRUE,
  promo_velocidad VARCHAR(12) NOT NULL DEFAULT 'NORMAL',
  promo_mensajes JSONB NOT NULL DEFAULT '["NUEVAS COLECCIONES · DESCUBRE TU ESTILO"]'::jsonb,
  menu_items JSONB NOT NULL DEFAULT '[{"clave":"TODO","texto":"Todo","visible":true},{"clave":"MUJER","texto":"Mujer","visible":true},{"clave":"HOMBRE","texto":"Hombre","visible":true},{"clave":"NOVEDADES","texto":"Novedades","visible":true},{"clave":"OFERTAS","texto":"Ofertas","visible":true}]'::jsonb,
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_cabecera_velocidad CHECK (promo_velocidad IN ('LENTA','NORMAL','RAPIDA')),
  CONSTRAINT ck_cabecera_mensajes_array CHECK (jsonb_typeof(promo_mensajes) = 'array'),
  CONSTRAINT ck_cabecera_menu_array CHECK (jsonb_typeof(menu_items) = 'array')
);
-- Las escrituras se realizan únicamente por Spring Boot con el tenant del JWT.
-- No se concede acceso anónimo de escritura a esta tabla.
