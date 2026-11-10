-- Additive migration: preserves existing orders and manual payments.
CREATE TABLE public.yape_checkout_reintentos (
    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
    usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id_usuario),
    clave UUID NOT NULL,
    huella VARCHAR(64) NOT NULL CHECK (huella ~ '^[0-9a-f]{64}$'),
    id_venta INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tienda_id, usuario_id, clave),
    UNIQUE (tienda_id, id_venta),
    FOREIGN KEY (tienda_id, id_venta) REFERENCES public.yape_pagos_manuales(tienda_id, id_venta)
);

-- Dedicated ledger because COD kardex has a FK and CHECK exclusive to COD cancellations.
CREATE TABLE public.yape_inventario_reposiciones (
    tienda_id BIGINT NOT NULL,
    id_venta INTEGER NOT NULL,
    id_producto_talla INTEGER NOT NULL REFERENCES public.producto_tallas(id_producto_talla),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    stock_anterior INTEGER NOT NULL CHECK (stock_anterior >= 0),
    stock_posterior INTEGER NOT NULL CHECK (stock_posterior::bigint = stock_anterior::bigint + cantidad::bigint),
    actor_correo VARCHAR(150) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tienda_id,id_venta,id_producto_talla),
    FOREIGN KEY (tienda_id,id_venta) REFERENCES public.yape_pagos_manuales(tienda_id,id_venta)
);
