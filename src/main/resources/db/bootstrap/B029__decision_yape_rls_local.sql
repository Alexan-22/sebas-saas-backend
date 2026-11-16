-- Opt-in: decisiones Yape manuales, no procesa dinero ni habilita otros proveedores.
BEGIN;
GRANT UPDATE(estado,verificado_en,verificado_por,motivo_rechazo) ON yape_pagos_manuales TO sebas_auth_app;
GRANT SELECT ON yape_pagos_auditoria,yape_inventario_reposiciones TO sebas_auth_app;
GRANT INSERT ON yape_inventario_reposiciones TO sebas_auth_app;
CREATE POLICY decision_yape_update ON yape_pagos_manuales FOR UPDATE TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND estado IN ('PENDIENTE','APROBADO','RECHAZADO'))
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND estado IN ('APROBADO','RECHAZADO') AND verificado_en IS NOT NULL AND verificado_por IS NOT NULL);
CREATE POLICY decision_yape_venta_update ON ventas FOR UPDATE TO sebas_auth_app
 USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND medio_pago='YAPE' AND estado IN ('PAGO_PENDIENTE','PAGO_CONFIRMADO','PAGO_RECHAZADO'))
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND medio_pago='YAPE' AND tipo_venta='ONLINE' AND estado IN ('PAGO_PENDIENTE','PAGO_CONFIRMADO','PAGO_RECHAZADO'));
CREATE POLICY decision_yape_auditoria_leer ON yape_pagos_auditoria FOR SELECT TO sebas_auth_app USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY decision_yape_evento ON yape_pagos_auditoria FOR INSERT TO sebas_auth_app
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND evento IN ('APROBADO','RECHAZADO') AND estado_anterior='PENDIENTE' AND estado_nuevo=evento AND EXISTS(SELECT 1 FROM yape_pagos_manuales p WHERE p.id=pago_id AND p.id_venta=yape_pagos_auditoria.id_venta AND p.tienda_id=yape_pagos_auditoria.tienda_id AND p.estado=yape_pagos_auditoria.estado_nuevo));
CREATE POLICY decision_yape_reposicion_leer ON yape_inventario_reposiciones FOR SELECT TO sebas_auth_app USING(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint);
CREATE POLICY decision_yape_reposicion_alta ON yape_inventario_reposiciones FOR INSERT TO sebas_auth_app
 WITH CHECK(tienda_id=nullif(current_setting('app.tenant_id',true),'')::bigint AND EXISTS(SELECT 1 FROM productos p JOIN producto_tallas pt ON pt.id_producto=p.id_producto WHERE pt.id_producto_talla=yape_inventario_reposiciones.id_producto_talla AND p.tienda_id=yape_inventario_reposiciones.tienda_id) AND EXISTS(SELECT 1 FROM ventas v WHERE v.id_venta=yape_inventario_reposiciones.id_venta AND v.tienda_id=yape_inventario_reposiciones.tienda_id AND v.medio_pago='YAPE'));
ALTER TABLE yape_pagos_auditoria ADD CONSTRAINT decision_yape_auditoria_venta_fk FOREIGN KEY(tienda_id,id_venta) REFERENCES ventas(tienda_id,id_venta);
CREATE FUNCTION public.yape_decision_final_inmutable() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $tag$
BEGIN
 IF OLD.estado<>'PENDIENTE' AND ROW(NEW.estado,NEW.verificado_en,NEW.verificado_por,NEW.motivo_rechazo) IS DISTINCT FROM ROW(OLD.estado,OLD.verificado_en,OLD.verificado_por,OLD.motivo_rechazo) THEN
  RAISE EXCEPTION 'Decision de pago final inmutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $tag$;
REVOKE ALL ON FUNCTION public.yape_decision_final_inmutable() FROM PUBLIC;
CREATE TRIGGER yape_decision_final_inmutable BEFORE UPDATE ON yape_pagos_manuales FOR EACH ROW EXECUTE FUNCTION public.yape_decision_final_inmutable();
COMMIT;
