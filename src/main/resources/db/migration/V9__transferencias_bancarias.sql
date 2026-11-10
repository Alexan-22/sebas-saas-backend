-- B.2.2.4. PLANTILLA DE REVISION. No ejecutar en Supabase de produccion.
-- El primer ensayo debe ejecutarse EXCLUSIVAMENTE sobre PostgreSQL desechable.
-- Requiere V7 Yape para indice UNIQUE (tienda_id,id_venta) en public.ventas.
CREATE TABLE public.transferencia_cuentas_bancarias (
  id BIGSERIAL UNIQUE NOT NULL,
  moneda CHAR(3) NOT NULL DEFAULT 'PEN' CHECK(moneda='PEN'),
  tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
  banco VARCHAR(20) NOT NULL CHECK (banco IN ('BCP','BBVA','INTERBANK','SCOTIABANK')),
  titular VARCHAR(140) NOT NULL,
  numero_cuenta VARCHAR(30) NOT NULL,
  cci VARCHAR(20),
  habilitado BOOLEAN NOT NULL DEFAULT FALSE,
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tienda_id,banco),
  UNIQUE(tienda_id,id),
  CHECK (numero_cuenta ~ '^[0-9]{7,30}$'),
  CHECK (cci IS NULL OR cci ~ '^[0-9]{20}$'),
  CHECK (length(trim(titular)) > 1)
);
CREATE TABLE public.transferencia_pagos_manuales (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
  id_venta INTEGER NOT NULL,
  usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
  correo_cliente VARCHAR(150) NOT NULL,
  cuenta_bancaria_id BIGINT NOT NULL,
  FOREIGN KEY(tienda_id,cuenta_bancaria_id) REFERENCES public.transferencia_cuentas_bancarias(tienda_id,id),
  importe NUMERIC(12,2) NOT NULL CHECK (importe > 0),
  moneda CHAR(3) NOT NULL DEFAULT 'PEN' CHECK (moneda='PEN'),
  banco VARCHAR(20) NOT NULL CHECK (banco IN ('BCP','BBVA','INTERBANK','SCOTIABANK')),
  titular VARCHAR(140) NOT NULL,
  numero_cuenta VARCHAR(30) NOT NULL,
  cci VARCHAR(20),
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
    CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO')),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  verificado_en TIMESTAMPTZ,
  verificado_por VARCHAR(150),
  motivo_rechazo VARCHAR(250),
  UNIQUE (tienda_id,id_venta),
  UNIQUE (tienda_id,id),
  CONSTRAINT transferencia_venta_tenant_fk FOREIGN KEY (tienda_id,id_venta)
    REFERENCES public.ventas(tienda_id,id_venta),
  CHECK ((estado='PENDIENTE' AND verificado_en IS NULL AND verificado_por IS NULL)
      OR (estado<>'PENDIENTE' AND verificado_en IS NOT NULL AND verificado_por IS NOT NULL))
);
CREATE INDEX transferencia_tienda_estado_fecha_idx
  ON public.transferencia_pagos_manuales(tienda_id,estado,creado_en DESC);
CREATE TABLE public.transferencia_pagos_auditoria (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
  pago_id BIGINT NOT NULL,
  id_venta INTEGER NOT NULL,
  evento VARCHAR(30) NOT NULL CHECK (evento IN ('CREADO','APROBADO','RECHAZADO','CAPTURA_RECIBIDA','CAPTURA_REEMPLAZADA','STOCK_REPUESTO')),
  estado_anterior VARCHAR(20),
  estado_nuevo VARCHAR(20) NOT NULL,
  actor_correo VARCHAR(150) NOT NULL,
  observacion VARCHAR(250),
  ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tienda_id,pago_id) REFERENCES public.transferencia_pagos_manuales(tienda_id,id)
);
CREATE INDEX transferencia_auditoria_lookup_idx
  ON public.transferencia_pagos_auditoria(tienda_id,id_venta,ocurrido_en);
CREATE TABLE public.transferencia_comprobantes (
  tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
  id_venta INTEGER NOT NULL,
  usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
  imagen BYTEA NOT NULL,
  mime_tipo VARCHAR(20) NOT NULL CHECK (mime_tipo IN ('image/png','image/jpeg')),
  tamano_bytes INTEGER NOT NULL CHECK (tamano_bytes BETWEEN 1 AND 5242880),
  sha256 VARCHAR(64) NOT NULL,
  enviado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tienda_id,id_venta),
  FOREIGN KEY (tienda_id,id_venta) REFERENCES public.transferencia_pagos_manuales(tienda_id,id_venta)
);
CREATE TABLE public.transferencia_stock_reposiciones (
  tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
  id_venta INTEGER NOT NULL,
  id_producto_talla INTEGER NOT NULL REFERENCES public.producto_tallas(id_producto_talla),
  cantidad INTEGER NOT NULL CHECK (cantidad > 0),
  stock_anterior INTEGER NOT NULL CHECK (stock_anterior>=0),
  stock_posterior INTEGER NOT NULL CHECK (stock_posterior::bigint = stock_anterior::bigint + cantidad::bigint),
  repuesto_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tienda_id,id_venta,id_producto_talla),
  FOREIGN KEY (tienda_id,id_venta) REFERENCES public.transferencia_pagos_manuales(tienda_id,id_venta)
);

CREATE TABLE public.transferencia_checkout_reintentos (
 tienda_id BIGINT NOT NULL, usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
 clave UUID NOT NULL, huella VARCHAR(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'), id_venta INTEGER NOT NULL,
 PRIMARY KEY(tienda_id,usuario_id,clave), UNIQUE(tienda_id,id_venta),
 FOREIGN KEY(tienda_id,id_venta) REFERENCES public.transferencia_pagos_manuales(tienda_id,id_venta)
);
CREATE TABLE public.transferencia_comprobantes_historial (
 id BIGSERIAL PRIMARY KEY, tienda_id BIGINT NOT NULL, id_venta INTEGER NOT NULL,
 imagen BYTEA NOT NULL, mime_tipo VARCHAR(20) NOT NULL, sha256 VARCHAR(64) NOT NULL,
 enviado_en TIMESTAMPTZ NOT NULL, reemplazado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(tienda_id,id_venta) REFERENCES public.transferencia_pagos_manuales(tienda_id,id_venta)
);
