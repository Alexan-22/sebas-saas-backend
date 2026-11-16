package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Subconjunto explícitamente registrado por la prueba; no activa el backend completo.
 */
public class CatalogoRlsLocalService {

 private final JdbcTemplate db;

 public CatalogoRlsLocalService(JdbcTemplate db) {
  this.db = db;
 }

 @Transactional
 public List<Map<String, Object>> productos() {
  contexto();
  return db.queryForList(
          "SELECT id_producto, tienda_id, nombre FROM productos ORDER BY id_producto"
  );
 }

 @Transactional
 public List<Map<String, Object>> stock() {
  contexto();
  return db.queryForList(
          "SELECT id_producto_talla, stock FROM producto_tallas ORDER BY id_producto_talla"
  );
 }

 @Transactional
 public void categoria(String nombre) {
  contexto();
  db.update(
          "INSERT INTO categorias(nombre_categoria, estado, tienda_id) VALUES (?, true, ?)",
          nombre,
          TenantContext.getCurrentTenant()
  );
 }

 @Transactional
 public void rollback() {
  categoria("REVERTIR");
  throw new IllegalStateException("Rollback solicitado por prueba");
 }

 private void contexto() {
  if (!TransactionSynchronizationManager.isActualTransactionActive()) {
   throw new IllegalStateException("Transaccion obligatoria");
  }

  db.queryForObject(
          "SELECT set_config('app.tenant_id', ?, true)",
          String.class,
          TenantContext.getCurrentTenant().toString()
  );
 }
}
