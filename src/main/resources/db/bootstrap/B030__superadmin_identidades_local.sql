-- B030 PROPUESTA LOCAL OPT-IN. Requiere roles LOGIN separados creados por el fixture/administrador.
-- No activar en Supabase: compatibilidad y todos los flujos todavía deben ensayarse.
BEGIN;
CREATE TABLE public.tienda_propietarios (
 tienda_id bigint PRIMARY KEY REFERENCES public.tiendas(id),
 usuario_id integer NOT NULL,
 asignado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(tienda_id,usuario_id) REFERENCES public.usuarios(tienda_id,id_usuario)
);
ALTER TABLE public.tienda_propietarios ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.tienda_propietarios FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA public TO sebas_global_auth, sebas_global_ops;
GRANT SELECT ON usuarios, roles TO sebas_global_auth;
GRANT UPDATE(password_hash,version_sesion,revision,nombre,apellido,correo,id_rol,estado,tienda_id)
 ON usuarios TO sebas_global_auth;
GRANT SELECT,INSERT,UPDATE ON auth_sesiones,auth_recuperaciones,auth_limites TO sebas_global_auth;
CREATE POLICY global_auth_roles ON roles FOR SELECT TO sebas_global_auth USING(nombre_rol='SUPER_ADMIN');
CREATE POLICY global_auth_usuario ON usuarios TO sebas_global_auth
 USING(tienda_id IS NULL AND EXISTS(SELECT 1 FROM roles r WHERE r.id_rol=usuarios.id_rol AND r.nombre_rol='SUPER_ADMIN'))
 WITH CHECK(tienda_id IS NULL AND EXISTS(SELECT 1 FROM roles r WHERE r.id_rol=usuarios.id_rol AND r.nombre_rol='SUPER_ADMIN'));
CREATE FUNCTION public.global_auth_identidad_inmutable() RETURNS trigger
 LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 IF current_user='sebas_global_auth' AND ROW(NEW.id_usuario,NEW.nombre,NEW.apellido,NEW.correo,NEW.id_rol,NEW.estado,NEW.tienda_id)
 IS DISTINCT FROM ROW(OLD.id_usuario,OLD.nombre,OLD.apellido,OLD.correo,OLD.id_rol,OLD.estado,OLD.tienda_id) THEN
  RAISE EXCEPTION 'Identidad global inmutable para autenticacion' USING ERRCODE='42501';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.global_auth_identidad_inmutable() FROM PUBLIC;
CREATE TRIGGER global_auth_identidad BEFORE UPDATE ON usuarios FOR EACH ROW EXECUTE FUNCTION public.global_auth_identidad_inmutable();
CREATE POLICY global_auth_sesion ON auth_sesiones TO sebas_global_auth
 USING(tienda_id IS NULL AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_sesiones.usuario_id))
 WITH CHECK(tienda_id IS NULL AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_sesiones.usuario_id));
CREATE POLICY global_auth_recuperacion ON auth_recuperaciones TO sebas_global_auth
 USING(tienda_id IS NULL AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_recuperaciones.usuario_id))
 WITH CHECK(tienda_id IS NULL AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_recuperaciones.usuario_id));
CREATE POLICY global_auth_limite ON auth_limites TO sebas_global_auth
 USING(clave=nullif(current_setting('app.auth_rate_key',true),''))
 WITH CHECK(clave=nullif(current_setting('app.auth_rate_key',true),''));

GRANT SELECT,INSERT ON tiendas,planes_saas,suscripciones_saas,tienda_propietarios TO sebas_global_ops;
GRANT UPDATE(nombre,subdominio,color_primario,logo_url,estado) ON tiendas TO sebas_global_ops;
GRANT UPDATE(nombre,descripcion,precio_mensual,duracion_meses,limite_productos,limite_usuarios,limite_sucursales,beneficios,activo,actualizado_en)
 ON planes_saas TO sebas_global_ops;
GRANT UPDATE(usuario_id,asignado_en) ON tienda_propietarios TO sebas_global_ops;
GRANT SELECT,INSERT ON usuarios TO sebas_global_ops;
GRANT SELECT ON roles TO sebas_global_ops;
GRANT SELECT,INSERT,UPDATE ON saas_configuracion TO sebas_global_ops;
GRANT INSERT ON saas_actividad TO sebas_global_ops;
GRANT USAGE ON SEQUENCE tiendas_id_seq,planes_saas_id_seq,suscripciones_saas_id_seq,usuarios_id_usuario_seq,saas_actividad_id_seq TO sebas_global_ops;
CREATE POLICY global_ops_roles ON roles FOR SELECT TO sebas_global_ops USING(nombre_rol='ADMIN');
CREATE POLICY global_ops_propietario_usuario ON usuarios TO sebas_global_ops
 USING(tienda_id IS NOT NULL AND EXISTS(SELECT 1 FROM roles r WHERE r.id_rol=usuarios.id_rol AND r.nombre_rol='ADMIN'))
 WITH CHECK(tienda_id IS NOT NULL AND EXISTS(SELECT 1 FROM roles r WHERE r.id_rol=usuarios.id_rol AND r.nombre_rol='ADMIN'));
CREATE POLICY global_ops_tiendas ON tiendas TO sebas_global_ops
 USING(id>0 AND estado IN ('ACTIVA','PENDIENTE','SUSPENDIDA'))
 WITH CHECK(id>0 AND estado IN ('ACTIVA','PENDIENTE','SUSPENDIDA'));
CREATE POLICY global_ops_planes ON planes_saas TO sebas_global_ops
 USING(id>0 AND duracion_meses BETWEEN 1 AND 36 AND precio_mensual>=0)
 WITH CHECK(id>0 AND duracion_meses BETWEEN 1 AND 36 AND precio_mensual>=0 AND moneda='PEN');
CREATE POLICY global_ops_suscripciones ON suscripciones_saas TO sebas_global_ops
 USING(id>0 AND EXISTS(SELECT 1 FROM tiendas t WHERE t.id=suscripciones_saas.tienda_id))
 WITH CHECK(id>0 AND estado IN ('ACTIVA','PENDIENTE') AND fecha_fin>fecha_inicio
 AND EXISTS(SELECT 1 FROM tiendas t WHERE t.id=suscripciones_saas.tienda_id)
 AND EXISTS(SELECT 1 FROM planes_saas p WHERE p.id=suscripciones_saas.plan_id AND p.activo));
CREATE POLICY global_ops_propietarios ON tienda_propietarios TO sebas_global_ops
 USING(EXISTS(SELECT 1 FROM tiendas t WHERE t.id=tienda_propietarios.tienda_id))
 WITH CHECK(EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=tienda_propietarios.usuario_id AND u.tienda_id=tienda_propietarios.tienda_id AND u.estado));
CREATE POLICY global_ops_plan_config ON saas_configuracion TO sebas_global_ops
 USING(clave='plan_comercial_principal') WITH CHECK(clave='plan_comercial_principal' AND valor ~ '^[1-9][0-9]*$');
CREATE POLICY global_ops_auditoria ON saas_actividad FOR INSERT TO sebas_global_ops
 WITH CHECK(length(usuario)>0 AND length(accion)>0 AND entidad IN ('TIENDA','PROPIETARIO','PLAN','SUSCRIPCION','PLAN_COMERCIAL'));

-- Suspender/poner pendiente una boutique revoca sesiones, sin exponer IDs ni credenciales.
GRANT SELECT(tienda_id),UPDATE(revocada) ON auth_sesiones TO sebas_global_ops;
CREATE POLICY global_ops_sesiones_inactivas_lectura ON auth_sesiones FOR SELECT TO sebas_global_ops
 USING(tienda_id IS NOT NULL AND EXISTS(SELECT 1 FROM tiendas t WHERE t.id=auth_sesiones.tienda_id AND t.estado IN ('SUSPENDIDA','PENDIENTE')));
CREATE POLICY global_ops_sesiones_inactivas_revocar ON auth_sesiones FOR UPDATE TO sebas_global_ops
 USING(tienda_id IS NOT NULL AND EXISTS(SELECT 1 FROM tiendas t WHERE t.id=auth_sesiones.tienda_id AND t.estado IN ('SUSPENDIDA','PENDIENTE')))
 WITH CHECK(revocada AND tienda_id IS NOT NULL AND EXISTS(SELECT 1 FROM tiendas t WHERE t.id=auth_sesiones.tienda_id AND t.estado IN ('SUSPENDIDA','PENDIENTE')));

-- Mantiene la comprobacion de correo global del contrato sin exponer filas privadas de usuarios.
-- La funcion debe pertenecer al dueno NOLOGIN de usuarios, nunca a un LOGIN de aplicacion.
DO $correo_owner$
BEGIN
 IF NOT EXISTS (
  SELECT 1 FROM pg_roles r JOIN pg_class t ON t.relowner=r.oid
  JOIN pg_namespace n ON n.oid=t.relnamespace
  WHERE r.rolname=current_user AND n.nspname='public' AND t.relname='usuarios'
    AND NOT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls
    AND NOT r.rolcreaterole AND NOT r.rolcreatedb
 ) THEN
  RAISE EXCEPTION 'global_correo_registrado requiere el propietario migrador NOLOGIN limitado' USING ERRCODE='42501';
 END IF;
END
$correo_owner$;
CREATE FUNCTION public.global_correo_registrado(p_correo text) RETURNS boolean
 LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT EXISTS(SELECT 1 FROM public.usuarios u WHERE lower(u.correo)=lower(btrim(p_correo)))
$$;
REVOKE ALL ON FUNCTION public.global_correo_registrado(text) FROM PUBLIC,anon,authenticated,sebas_auth_app,sebas_global_auth;
REVOKE GRANT OPTION FOR EXECUTE ON FUNCTION public.global_correo_registrado(text) FROM sebas_global_ops;
GRANT EXECUTE ON FUNCTION public.global_correo_registrado(text) TO sebas_global_ops;
DO $correo_acl$
BEGIN
 IF EXISTS (
  SELECT 1 FROM pg_proc p
  CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
  WHERE p.oid='public.global_correo_registrado(text)'::regprocedure
    AND a.privilege_type='EXECUTE'
    AND (a.grantee NOT IN (p.proowner,(SELECT oid FROM pg_roles WHERE rolname='sebas_global_ops')) OR (a.grantee=(SELECT oid FROM pg_roles WHERE rolname='sebas_global_ops') AND a.is_grantable))
 ) THEN
  RAISE EXCEPTION 'EXECUTE de global_correo_registrado tiene destinatarios adicionales' USING ERRCODE='42501';
 END IF;
END
$correo_acl$;
COMMIT;
