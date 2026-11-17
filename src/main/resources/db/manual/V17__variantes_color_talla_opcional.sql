
-- V17: permitir colores y tallas opcionales.
-- Conserva id_producto_talla y el stock actual.

BEGIN;

SET LOCAL lock_timeout = '5s';

LOCK TABLE public.producto_tallas
IN ACCESS EXCLUSIVE MODE;

-- Nueva regla: no repetir combinaciones.
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_saas_producto_talla_color
ON public.producto_tallas (
    id_producto,
    COALESCE(id_talla, -1),
    COALESCE(id_color, -1)
);

-- Sustituir la regla anterior.
ALTER TABLE public.producto_tallas
    DROP CONSTRAINT IF EXISTS uq_producto_talla;

-- Permitir artículos sin talla.
ALTER TABLE public.producto_tallas
    ALTER COLUMN id_talla DROP NOT NULL;

COMMIT;
