-- BORRADOR DE REVISION - NO EJECUTAR EN SUPABASE
-- Requiere adaptar roles, permisos, transacciones y funciones.
-- Solo para desarrollo y validacion con PostgreSQL temporal.
-- Opt-in experimental para sebas_auth_app. Sin Ã¡mbito global Superadmin.
BEGIN;
GRANT USAGE ON SCHEMA public TO sebas_auth_app;
GRANT SELECT,INSERT,UPDATE ON usuarios,auth_sesiones,auth_recuperaciones,auth_limites TO sebas_auth_app;
GRANT SELECT ON roles,productos,producto_tallas TO sebas_auth_app;
GRANT INSERT,UPDATE ON producto_tallas TO sebas_auth_app;
GRANT USAGE ON SEQUENCE usuarios_id_usuario_seq,producto_tallas_id_producto_talla_seq TO sebas_auth_app;
CREATE POLICY auth_usuario_tenant ON usuarios TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint)
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND EXISTS(SELECT 1 FROM roles r WHERE r.id_rol=usuarios.id_rol));
CREATE POLICY auth_roles_ordinarios ON roles FOR SELECT TO sebas_auth_app
 USING(nombre_rol IN ('ADMIN','EMPLEADO','CLIENTE'));
CREATE POLICY auth_sesion_tenant ON auth_sesiones TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint)
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_sesiones.usuario_id AND u.tienda_id=auth_sesiones.tienda_id));
CREATE POLICY auth_recuperacion_tenant ON auth_recuperaciones TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint)
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND EXISTS(SELECT 1 FROM usuarios u WHERE u.id_usuario=auth_recuperaciones.usuario_id AND u.tienda_id=auth_recuperaciones.tienda_id));
CREATE POLICY auth_limite_clave ON auth_limites TO sebas_auth_app
 USING(clave=nullif(current_setting('app.auth_rate_key',true),''))
 WITH CHECK(clave=nullif(current_setting('app.auth_rate_key',true),''));
CREATE POLICY auth_producto_lectura ON productos FOR SELECT TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY auth_stock_producto ON producto_tallas TO sebas_auth_app
 USING(EXISTS(SELECT 1 FROM productos p WHERE p.id_producto=producto_tallas.id_producto))
 WITH CHECK(EXISTS(SELECT 1 FROM productos p WHERE p.id_producto=producto_tallas.id_producto));
COMMIT;

