-- Sólo para la fixture PostgreSQL privada que aplica B028 y V26.
BEGIN;

GRANT SELECT, INSERT ON public.venta_clientes_invitados,
    public.checkout_invitado_reintentos, public.checkout_invitado_accesos TO sebas_auth_app;
GRANT SELECT ON public.transferencia_cuentas_bancarias TO sebas_auth_app;
-- PostgreSQL exige permiso UPDATE de columna para el SELECT FOR SHARE del checkout.
-- La política con WITH CHECK (false) impide modificar la cuenta.
GRANT UPDATE (id) ON public.transferencia_cuentas_bancarias TO sebas_auth_app;
GRANT SELECT, INSERT ON public.transferencia_pagos_manuales TO sebas_auth_app;
GRANT INSERT ON public.transferencia_pagos_auditoria TO sebas_auth_app;
GRANT USAGE ON SEQUENCE public.transferencia_pagos_manuales_id_seq,
    public.transferencia_pagos_auditoria_id_seq TO sebas_auth_app;

CREATE POLICY fixture_checkout_venta_invitada ON public.ventas FOR INSERT TO sebas_auth_app
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND id_usuario IS NULL AND tipo_venta = 'ONLINE' AND estado = 'PAGO_PENDIENTE'
    AND medio_pago IN ('YAPE','TRANSFERENCIA'));

CREATE POLICY fixture_checkout_venta_transferencia ON public.ventas FOR UPDATE TO sebas_auth_app
USING (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND id_usuario IS NULL AND estado = 'PAGO_PENDIENTE' AND medio_pago = 'TRANSFERENCIA')
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND id_usuario IS NULL AND tipo_venta = 'ONLINE' AND estado = 'PAGO_PENDIENTE'
    AND medio_pago = 'TRANSFERENCIA');

CREATE POLICY fixture_checkout_detalle_transferencia ON public.detalle_venta FOR INSERT TO sebas_auth_app
WITH CHECK (EXISTS (SELECT 1 FROM public.ventas v WHERE v.id_venta = detalle_venta.id_venta
        AND v.id_usuario IS NULL AND v.estado = 'PAGO_PENDIENTE' AND v.medio_pago = 'TRANSFERENCIA')
    AND EXISTS (SELECT 1 FROM public.producto_tallas pt
        WHERE pt.id_producto_talla = detalle_venta.id_producto_talla));

CREATE POLICY fixture_checkout_yape_invitado ON public.yape_pagos_manuales FOR INSERT TO sebas_auth_app
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND usuario_id IS NULL AND estado = 'PENDIENTE' AND verificado_en IS NULL AND verificado_por IS NULL
    AND EXISTS (SELECT 1 FROM public.ventas v WHERE v.id_venta = yape_pagos_manuales.id_venta
        AND v.tienda_id = yape_pagos_manuales.tienda_id AND v.id_usuario IS NULL
        AND v.estado = 'PAGO_PENDIENTE' AND v.medio_pago = 'YAPE'));

CREATE POLICY fixture_checkout_transferencia_cuenta ON public.transferencia_cuentas_bancarias
FOR SELECT TO sebas_auth_app
USING (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint);

CREATE POLICY fixture_checkout_transferencia_bloqueo ON public.transferencia_cuentas_bancarias
FOR UPDATE TO sebas_auth_app
USING (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint)
WITH CHECK (false);

CREATE POLICY fixture_checkout_transferencia_lectura ON public.transferencia_pagos_manuales
FOR SELECT TO sebas_auth_app
USING (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint);

CREATE POLICY fixture_checkout_transferencia_invitada ON public.transferencia_pagos_manuales
FOR INSERT TO sebas_auth_app
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND usuario_id IS NULL AND estado = 'PENDIENTE' AND verificado_en IS NULL AND verificado_por IS NULL
    AND EXISTS (SELECT 1 FROM public.ventas v WHERE v.id_venta = transferencia_pagos_manuales.id_venta
        AND v.tienda_id = transferencia_pagos_manuales.tienda_id AND v.id_usuario IS NULL
        AND v.estado = 'PAGO_PENDIENTE' AND v.medio_pago = 'TRANSFERENCIA'));

CREATE POLICY fixture_checkout_transferencia_evento ON public.transferencia_pagos_auditoria
FOR INSERT TO sebas_auth_app
WITH CHECK (tienda_id = nullif(current_setting('app.tenant_id', true),'')::bigint
    AND evento = 'CREADO' AND estado_nuevo = 'PENDIENTE'
    AND EXISTS (SELECT 1 FROM public.transferencia_pagos_manuales p
        WHERE p.id = pago_id AND p.id_venta = transferencia_pagos_auditoria.id_venta
            AND p.tienda_id = transferencia_pagos_auditoria.tienda_id));

COMMIT;
