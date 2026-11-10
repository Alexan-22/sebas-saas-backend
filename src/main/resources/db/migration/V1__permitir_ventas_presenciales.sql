-- ============================================================
-- TIENDA SEBAS
-- Migración: soporte para ventas presenciales
-- ============================================================
--
-- Una venta ONLINE utiliza id_usuario.
-- Una venta PRESENCIAL utiliza id_cliente_presencial.
--
-- Por este motivo id_usuario debe permitir NULL.
-- ============================================================

ALTER TABLE ventas
ALTER COLUMN id_usuario DROP NOT NULL;