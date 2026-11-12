# Scripts SQL manuales - Sebas Collection SaaS

## Cabecera y Promo Bar

1. V13__cabecera_saas.sql
   Crea public.saas_cabecera_config y la relacion con tiendas.

2. V14__promo_bar_dos_modos.sql
   Agrega promo_modo y amplía las velocidades permitidas.

## Estado de la base existente

Los cambios de cabecera y Promo Bar V2 ya fueron
aplicados manualmente en Supabase.

No volver a ejecutar estos archivos sobre una base
existente sin inspeccionar primero su estructura.

## Importante

El backend actual no tiene Flyway configurado.

Estos scripts son documentación versionada y no se
ejecutan automáticamente al iniciar Spring Boot.

No activar Flyway sin planificar previamente el
baseline y la reconciliación de V1 a V12.