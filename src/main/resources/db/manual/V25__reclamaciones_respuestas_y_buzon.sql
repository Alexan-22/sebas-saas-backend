-- BORRADOR SOLO PARA STAGING: requiere V23/V24 y auditoria de roles PostgreSQL.
-- No ejecutar en Azure hasta revisar permisos, RLS y backups.
BEGIN;
CREATE TABLE public.saas_reclamaciones_correos_config (
    tienda_id bigint PRIMARY KEY REFERENCES public.tiendas(id),
    correo_admin varchar(254),
    correo_admin_verificado boolean NOT NULL DEFAULT false,
    CONSTRAINT correo_admin_no_vacio CHECK (correo_admin IS NULL OR length(btrim(correo_admin)) > 3)
);
CREATE TABLE public.saas_reclamaciones_notificaciones (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tienda_id bigint NOT NULL,
    reclamacion_id bigint NOT NULL,
    evento_id bigint,
    clase varchar(25) NOT NULL CHECK (clase IN ('RECLAMACION_NUEVA','RESPUESTA_CONSUMIDOR')),
    destinatario varchar(254) NOT NULL,
    asunto varchar(200) NOT NULL,
    cuerpo text NOT NULL,
    estado varchar(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ENVIANDO','ENVIADO','ERROR')),
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
    enviado_en timestamptz,
    intentos int NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    error_ultimo varchar(500),
    FOREIGN KEY (tienda_id,reclamacion_id) REFERENCES public.saas_reclamaciones(tienda_id,id),
    UNIQUE (tienda_id,evento_id,clase)
);
CREATE INDEX idx_reclamaciones_notificaciones_estado ON public.saas_reclamaciones_notificaciones (tienda_id,estado,creado_en);
ALTER TABLE public.saas_reclamaciones_correos_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_correos_config FORCE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_notificaciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_reclamaciones_notificaciones FORCE ROW LEVEL SECURITY;
CREATE POLICY correo_admin_tenant ON public.saas_reclamaciones_correos_config FOR ALL
USING (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint)
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY notif_tenant ON public.saas_reclamaciones_notificaciones FOR ALL
USING (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint)
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
-- Sin GRANT y sin SMTP automático: debe configurarse rol JDBC y worker con permisos auditados.
COMMIT;
