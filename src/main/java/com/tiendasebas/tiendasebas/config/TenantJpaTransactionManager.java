package com.tiendasebas.tiendasebas.config;
/** Opt-in: instalar sólo en contextos cuya matriz RLS esté migrada. No se registra automáticamente. */
public class TenantJpaTransactionManager extends org.springframework.orm.jpa.JpaTransactionManager {
 public TenantJpaTransactionManager(jakarta.persistence.EntityManagerFactory emf,javax.sql.DataSource ds){super(emf);setDataSource(ds);}
 @Override protected void doBegin(Object transaction,org.springframework.transaction.TransactionDefinition definition){
  super.doBegin(transaction,definition);
  String tenant="";try{tenant=TenantContext.getCurrentTenant().toString();}catch(org.springframework.web.server.ResponseStatusException missing){/* Sin ámbito: políticas fallan cerrado. */}
  new org.springframework.jdbc.core.JdbcTemplate(getDataSource()).queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,tenant);
 }
}
