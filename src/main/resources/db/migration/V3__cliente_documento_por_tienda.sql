-- Aplicar solo tras revisar asignacion de tenants historicos. No reasigna registros.
-- Flyway debe ejecutar este archivo dentro de una unica transaccion.
LOCK TABLE public.clientes_presenciales IN ACCESS EXCLUSIVE MODE;
DO $$
DECLARE definition text;
BEGIN
    IF EXISTS (SELECT 1 FROM public.clientes_presenciales WHERE tienda_id IS NULL OR tienda_id <= 0) THEN
        RAISE EXCEPTION 'Existen clientes sin tenant valido; requieren revision manual';
    END IF;
    IF EXISTS (SELECT 1 FROM public.clientes_presenciales
               GROUP BY tienda_id, tipo_documento, numero_documento HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Existen documentos duplicados dentro de una tienda';
    END IF;
    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
      WHERE conrelid='public.clientes_presenciales'::regclass
        AND conname='uk_cliente_presencial_documento' AND contype='u';
    IF definition IS DISTINCT FROM 'UNIQUE (tipo_documento, numero_documento)' THEN
        RAISE EXCEPTION 'Restriccion documental distinta del contrato local; revisar esquema';
    END IF;
END $$;
ALTER TABLE public.clientes_presenciales
    ADD CONSTRAINT uk_cliente_presencial_tienda_documento
    UNIQUE (tienda_id, tipo_documento, numero_documento);
ALTER TABLE public.clientes_presenciales
    DROP CONSTRAINT uk_cliente_presencial_documento;
-- Conserva todas las filas. La columna sigue nullable por compatibilidad historica.
