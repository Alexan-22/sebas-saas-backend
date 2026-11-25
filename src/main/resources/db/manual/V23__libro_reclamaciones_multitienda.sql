-- BORRADOR DE MIGRACION PARA REVISION. NO EJECUTAR EN AZURE NI PRODUCCION.
-- Requiere verificar la identidad del rol JDBC, la activacion efectiva de RLS,
-- y la configuracion transaccional app.tenant_id antes de aplicar.
-- No concede GRANT a ningun rol de aplicacion: falla cerrado por defecto.

BEGIN;

CREATE TABLE public.saas_reclamaciones_numeracion (
    tienda_id bigint PRIMARY KEY REFERENCES public.tiendas(id),
    ultimo_numero bigint NOT NULL DEFAULT 0 CHECK (ultimo_numero >= 0)
);

CREATE TABLE public.saas_reclamaciones (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tienda_id bigint NOT NULL REFERENCES public.tiendas(id),
    numero_hoja bigint NOT NULL CHECK (numero_hoja > 0),
    recibido_en timestamptz NOT NULL DEFAULT clock_timestamp(),
    tipo varchar(10) NOT NULL CHECK (tipo IN ('RECLAMO', 'QUEJA')),
    consumidor_nombre varchar(200) NOT NULL,
    documento_tipo varchar(20) NOT NULL,
    documento_numero varchar(30) NOT NULL,
    domicilio text NOT NULL,
    correo varchar(254),
    telefono varchar(30),
    menor_edad boolean NOT NULL DEFAULT false,
    representante_nombre varchar(200),
    bien_tipo varchar(10) NOT NULL CHECK (bien_tipo IN ('PRODUCTO', 'SERVICIO')),
    bien_descripcion text NOT NULL,
    monto_reclamado numeric(12,2) CHECK (monto_reclamado >= 0),
    detalle text NOT NULL,
    pedido text NOT NULL,
    CONSTRAINT uq_reclamacion_tienda_numero UNIQUE (tienda_id, numero_hoja),
    CONSTRAINT uq_reclamacion_tienda_id UNIQUE (tienda_id, id),
    CONSTRAINT ck_reclamacion_menor_representante CHECK (
        NOT menor_edad OR NULLIF(btrim(representante_nombre), '') IS NOT NULL
    ),
    CONSTRAINT ck_reclamacion_contacto CHECK (
        NULLIF(btrim(coalesce(correo, '')), '') IS NOT NULL OR
        NULLIF(btrim(coalesce(telefono, '')), '') IS NOT NULL
    )
);

CREATE INDEX idx_reclamaciones_tienda_fecha
    ON public.saas_reclamaciones (tienda_id, recibido_en DESC);

CREATE TABLE public.saas_reclamaciones_eventos (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tienda_id bigint NOT NULL,
    reclamacion_id bigint NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
    tipo varchar(20) NOT NULL CHECK (tipo IN
        ('RECEPCION', 'NOTA_INTERNA', 'RESPUESTA', 'CAMBIO_ESTADO')),
    contenido text NOT NULL,
    actor varchar(200) NOT NULL,
    estado varchar(20) CHECK (estado IN
        ('RECIBIDO', 'EN_ATENCION', 'RESPONDIDO', 'CERRADO')),
    CONSTRAINT fk_evento_reclamacion_tienda FOREIGN KEY (tienda_id, reclamacion_id)
        REFERENCES public.saas_reclamaciones (tienda_id, id)
);

CREATE INDEX idx_reclamaciones_eventos_tienda_reclamacion
    ON public.saas_reclamaciones_eventos (tienda_id, reclamacion_id, creado_en);

-- Evita alterar o borrar la hoja original y el historial por SQL ordinario.
CREATE FUNCTION public.saas_reclamaciones_bloquear_cambios()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros originales del Libro de Reclamaciones son inmutables';
END;
$$;

CREATE TRIGGER tr_reclamaciones_inmutables
    BEFORE UPDATE OR DELETE ON public.saas_reclamaciones
    FOR EACH ROW EXECUTE FUNCTION public.saas_reclamaciones_bloquear_cambios();

CREATE TRIGGER tr_reclamaciones_eventos_inmutables
    BEFORE UPDATE OR DELETE ON public.saas_reclamaciones_eventos
    FOR EACH ROW EXECUTE FUNCTION public.saas_reclamaciones_bloquear_cambios();

-- RLS fail-closed. app.tenant_id debe fijarse con SET LOCAL o set_config(..., true)
-- en la misma transaccion y conexion antes de cada operacion.
ALTER TABLE public.saas_reclamaciones_numeracion ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_numeracion FORCE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones FORCE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_eventos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_eventos FORCE ROW LEVEL SECURITY;

CREATE POLICY reclamaciones_numeracion_tenant
ON public.saas_reclamaciones_numeracion FOR ALL
USING (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

CREATE POLICY reclamaciones_tenant
ON public.saas_reclamaciones FOR ALL
USING (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

CREATE POLICY reclamaciones_eventos_tenant
ON public.saas_reclamaciones_eventos FOR ALL
USING (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

-- SIN GRANTS: determinar roles exactos, privilegios minimos, pruebas de
-- acceso anonimo y admin, y forma de numeracion transaccional primero.
COMMIT;
