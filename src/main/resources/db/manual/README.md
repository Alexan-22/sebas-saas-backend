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

## Revocación de sesiones de empleados

V18__version_sesion_usuarios.sql agrega una columna nullable a usuarios.
Aplicar primero en una base aislada y revisar antes de instalar el backend
que depende de esta columna. Las cuentas existentes conservan la versión
implícita hasta su primera desactivación.

## Editor de tienda

V19__editor_tienda.sql crea el almacenamiento de borradores e historial.
Aplicar en PostgreSQL aislado antes de usar el nuevo editor. El usuario JDBC
debe ser el propietario de las tablas o tener acceso autorizado por RLS.
Las rutas públicas siguen leyendo las configuraciones de cabecera y footer existentes.

## Importante

El backend actual no tiene Flyway configurado.

Estos scripts son documentación versionada y no se
ejecutan automáticamente al iniciar Spring Boot.

No activar Flyway sin planificar previamente el
baseline y la reconciliación de V1 a V12.