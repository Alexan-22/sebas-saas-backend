-- Subconjunto experimental; requiere rol sebas_catalogo_app creado por provisionador.
-- No habilita login de usuarios ni operaciones globales. No instalar remotamente.
BEGIN;
GRANT USAGE ON SCHEMA public TO sebas_catalogo_app;
GRANT SELECT ON productos,producto_tallas,categorias TO sebas_catalogo_app;
GRANT INSERT ON categorias TO sebas_catalogo_app;
GRANT USAGE ON SEQUENCE categorias_id_categoria_seq TO sebas_catalogo_app;
CREATE POLICY catalogo_productos_tenant ON productos FOR SELECT TO sebas_catalogo_app
 USING(tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY catalogo_categorias_lectura ON categorias FOR SELECT TO sebas_catalogo_app
 USING(tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY catalogo_categorias_alta ON categorias FOR INSERT TO sebas_catalogo_app
 WITH CHECK(tienda_id = nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY catalogo_stock_tenant ON producto_tallas FOR SELECT TO sebas_catalogo_app
 USING(EXISTS(SELECT 1 FROM productos p WHERE p.id_producto=producto_tallas.id_producto));
COMMIT;
