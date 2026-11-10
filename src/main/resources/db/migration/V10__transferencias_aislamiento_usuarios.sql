-- Sebas SaaS: aislamiento por tienda y usuario
-- Ejecutar despues de V9, en la misma ventana
-- de mantenimiento y tras verificar los datos.

BEGIN;



ALTER TABLE public.transferencia_pagos_manuales
ADD CONSTRAINT transferencia_pago_usuario_tenant_fk
FOREIGN KEY (tienda_id, usuario_id)
REFERENCES public.usuarios(tienda_id, id_usuario);

ALTER TABLE public.transferencia_comprobantes
ADD CONSTRAINT transferencia_comprobante_usuario_tenant_fk
FOREIGN KEY (tienda_id, usuario_id)
REFERENCES public.usuarios(tienda_id, id_usuario);

ALTER TABLE public.transferencia_checkout_reintentos
ADD CONSTRAINT transferencia_reintento_usuario_tenant_fk
FOREIGN KEY (tienda_id, usuario_id)
REFERENCES public.usuarios(tienda_id, id_usuario);

COMMIT;