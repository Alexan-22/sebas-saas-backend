-- BORRADOR DE REVISION - NO EJECUTAR EN SUPABASE
-- Requiere adaptar roles, permisos, transacciones y funciones.
-- Solo para desarrollo y validacion con PostgreSQL temporal.
-- Propietario migrador; devuelve sÃ³lo id de boutique activa. Sin privilegios globales al llamador.
BEGIN;
CREATE FUNCTION public.auth_resolver_host(p_host text,p_base text,p_custom boolean)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT min(t.id) FROM public.tiendas t
 WHERE t.estado='ACTIVA' AND length(p_host)<=253
 AND p_host ~ '^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$'
 AND ((p_base<>'' AND p_host=t.subdominio||'.'||p_base)
 OR (p_custom AND EXISTS(SELECT 1 FROM public.dominios_saas d WHERE d.tienda_id=t.id AND d.dominio=p_host AND d.estado='VERIFICADO_DNS' AND d.verificado_en IS NOT NULL)))
 HAVING count(*)=1
$$;
REVOKE ALL ON FUNCTION public.auth_resolver_host(text,text,boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.auth_resolver_host(text,text,boolean) TO sebas_auth_app;
COMMIT;

