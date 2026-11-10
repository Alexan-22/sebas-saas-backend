-- B.2.2.2 - Yape directo, pedidos con pago pendiente y auditoria.
-- NO ejecutar contra Supabase comercial: primero validar solo en Testcontainers.
-- No modifica pagos_simulados ni los pagos SANDBOX.
-- Apoya FK compuesta para garantizar que un pago no apunte a venta de otra tienda.
CREATE UNIQUE INDEX yape_ventas_tienda_venta_uq
    ON public.ventas (tienda_id, id_venta);

CREATE TABLE public.yape_pagos_manuales (
    id BIGSERIAL PRIMARY KEY,
    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
    id_venta INTEGER NOT NULL,
    usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
    correo_cliente VARCHAR(150) NOT NULL,
    importe NUMERIC(12,2) NOT NULL CHECK (importe > 0),
    moneda CHAR(3) NOT NULL DEFAULT 'PEN' CHECK (moneda = 'PEN'),
    numero_yape VARCHAR(9) NOT NULL CHECK (numero_yape ~ '^9[0-9]{8}$'),
    titular_yape VARCHAR(120) NOT NULL,
    qr_imagen BYTEA NOT NULL,
    qr_sha256 VARCHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    verificado_en TIMESTAMPTZ,
    verificado_por VARCHAR(150),
    motivo_rechazo VARCHAR(250),
    CONSTRAINT yape_pagos_unicos_por_venta UNIQUE (tienda_id, id_venta),
    CONSTRAINT yape_pagos_venta_misma_tienda_fk
        FOREIGN KEY (tienda_id, id_venta) REFERENCES public.ventas(tienda_id, id_venta),
    CONSTRAINT yape_pagos_verificacion_consistente CHECK (
        (estado = 'PENDIENTE' AND verificado_en IS NULL AND verificado_por IS NULL)
        OR (estado <> 'PENDIENTE' AND verificado_en IS NOT NULL AND verificado_por IS NOT NULL)
    )
);

CREATE UNIQUE INDEX yape_pagos_tienda_id_uq
    ON public.yape_pagos_manuales (tienda_id, id);

CREATE INDEX yape_pagos_tienda_estado_fecha_idx
    ON public.yape_pagos_manuales (tienda_id, estado, creado_en DESC);

CREATE TABLE public.yape_pagos_auditoria (
    id BIGSERIAL PRIMARY KEY,
    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
    pago_id BIGINT NOT NULL,
    id_venta INTEGER NOT NULL REFERENCES public.ventas(id_venta),
    evento VARCHAR(30) NOT NULL CHECK (evento IN ('CREADO', 'APROBADO', 'RECHAZADO')),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    actor_correo VARCHAR(150) NOT NULL,
    observacion VARCHAR(250),
    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT yape_eventos_pago_misma_tienda_fk
        FOREIGN KEY (tienda_id, pago_id) REFERENCES public.yape_pagos_manuales(tienda_id, id)
);

CREATE INDEX yape_auditoria_tienda_pago_idx
    ON public.yape_pagos_auditoria (tienda_id, pago_id, ocurrido_en);
