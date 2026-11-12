
-- V16: Validar pertenencia del color a la tienda del producto.
-- Compatible con el inventario actual.

BEGIN;

CREATE OR REPLACE FUNCTION public.saas_validar_color_producto()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_tienda_producto BIGINT;
    v_tienda_color BIGINT;
BEGIN
    -- Sin color: mantener el comportamiento anterior.
    IF NEW.id_color IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT p.tienda_id
      INTO v_tienda_producto
      FROM public.productos p
     WHERE p.id_producto = NEW.id_producto;

    IF v_tienda_producto IS NULL THEN
        RAISE EXCEPTION 'Producto no encontrado o sin tienda';
    END IF;

    SELECT c.tienda_id
      INTO v_tienda_color
      FROM public.saas_colores_tienda c
     WHERE c.id_color = NEW.id_color;

    IF v_tienda_color IS NULL THEN
        RAISE EXCEPTION 'Color no encontrado';
    END IF;

    IF v_tienda_producto <> v_tienda_color THEN
        RAISE EXCEPTION
            'No se puede asignar un color de otra tienda'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.producto_tallas'::regclass
          AND tgname = 'tr_saas_validar_color_stock'
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER tr_saas_validar_color_stock
        BEFORE INSERT OR UPDATE OF id_color, id_producto
        ON public.producto_tallas
        FOR EACH ROW
        EXECUTE FUNCTION public.saas_validar_color_producto();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.producto_imagenes'::regclass
          AND tgname = 'tr_saas_validar_color_imagen'
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER tr_saas_validar_color_imagen
        BEFORE INSERT OR UPDATE OF id_color, id_producto
        ON public.producto_imagenes
        FOR EACH ROW
        EXECUTE FUNCTION public.saas_validar_color_producto();
    END IF;
END;
$$;

COMMIT;
