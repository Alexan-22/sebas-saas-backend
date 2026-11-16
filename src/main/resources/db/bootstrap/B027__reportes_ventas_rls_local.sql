-- Subconjunto de lectura comercial; no habilita checkout/pagos/cancelaciones.
BEGIN;
ALTER TABLE direcciones_envio ADD COLUMN tienda_id bigint REFERENCES tiendas(id);
UPDATE direcciones_envio d SET tienda_id=v.tienda_id FROM ventas v WHERE v.id_direccion_envio=d.id;
ALTER TABLE direcciones_envio ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE direcciones_envio ADD CONSTRAINT direccion_tenant_id_unique UNIQUE(tienda_id,id);
ALTER TABLE ventas ADD CONSTRAINT venta_direccion_tenant_fk FOREIGN KEY(tienda_id,id_direccion_envio) REFERENCES direcciones_envio(tienda_id,id);
CREATE INDEX detalle_venta_venta_idx ON detalle_venta(id_venta);
CREATE INDEX detalle_venta_variante_idx ON detalle_venta(id_producto_talla);
CREATE INDEX ventas_tienda_fecha_idx ON ventas(tienda_id,fecha_venta);
CREATE FUNCTION public.bootstrap_detalle_tenant() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.ventas v JOIN public.producto_tallas pt ON pt.id_producto_talla=NEW.id_producto_talla JOIN public.productos p ON p.id_producto=pt.id_producto WHERE v.id_venta=NEW.id_venta AND v.tienda_id=p.tienda_id) THEN
  RAISE EXCEPTION 'Relacion comercial de tenant invalida' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.bootstrap_detalle_tenant() FROM PUBLIC;
CREATE TRIGGER bootstrap_detalle_tenant_tr BEFORE INSERT OR UPDATE ON detalle_venta FOR EACH ROW EXECUTE FUNCTION public.bootstrap_detalle_tenant();
GRANT SELECT ON ventas,detalle_venta,direcciones_envio,categorias,generos,tallas TO sebas_auth_app;
CREATE POLICY reportes_ventas_tenant ON ventas FOR SELECT TO sebas_auth_app USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY reportes_direccion_tenant ON direcciones_envio FOR SELECT TO sebas_auth_app USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY reportes_categoria_tenant ON categorias FOR SELECT TO sebas_auth_app USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
-- Catálogos globales no privados: sólo lectura de identificadores positivos.
CREATE POLICY reportes_generos_catalogo ON generos FOR SELECT TO sebas_auth_app USING(id_genero>0);
CREATE POLICY reportes_tallas_catalogo ON tallas FOR SELECT TO sebas_auth_app USING(id_talla>0);
CREATE POLICY reportes_detalle_tenant ON detalle_venta FOR SELECT TO sebas_auth_app
 USING(EXISTS(SELECT 1 FROM ventas v WHERE v.id_venta=detalle_venta.id_venta)
 AND EXISTS(SELECT 1 FROM producto_tallas pt WHERE pt.id_producto_talla=detalle_venta.id_producto_talla));
COMMIT;
