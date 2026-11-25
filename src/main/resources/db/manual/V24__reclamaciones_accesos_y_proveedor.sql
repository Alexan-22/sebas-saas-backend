-- SOLO STAGING Y TRAS AUDITAR V23, ROLES Y RLS. NO EJECUTAR EN PRODUCCION A CIEGAS.
BEGIN;
CREATE TABLE public.saas_reclamaciones_proveedores (
 tienda_id bigint PRIMARY KEY REFERENCES public.tiendas(id),
 razon_social varchar(250) NOT NULL CHECK (length(btrim(razon_social)) > 0),
 ruc varchar(11) NOT NULL CHECK (ruc ~ '^[0-9]{11}$'),
 domicilio_legal text NOT NULL CHECK (length(btrim(domicilio_legal)) > 0),
 verificado boolean NOT NULL DEFAULT false,
 verificado_en timestamptz
);
CREATE TABLE public.saas_reclamaciones_accesos (
 tienda_id bigint NOT NULL,
 reclamacion_id bigint NOT NULL,
 token_sha256 varchar(43) NOT NULL,
 creado_en timestamptz NOT NULL DEFAULT now(),
 revocado_en timestamptz,
 PRIMARY KEY (tienda_id,reclamacion_id),
 UNIQUE (tienda_id,token_sha256),
 FOREIGN KEY (tienda_id,reclamacion_id)
   REFERENCES public.saas_reclamaciones(tienda_id,id)
);
ALTER TABLE public.saas_reclamaciones_proveedores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_proveedores FORCE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_accesos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_accesos FORCE ROW LEVEL SECURITY;
CREATE POLICY proveedor_tenant ON public.saas_reclamaciones_proveedores FOR ALL
 USING (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint)
 WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY accesos_tenant ON public.saas_reclamaciones_accesos FOR ALL
 USING (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint)
 WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
-- SIN GRANTS deliberadamente; definir roles JDBC con principio de privilegio minimo.
COMMIT;
