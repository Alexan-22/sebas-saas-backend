# Sebas Collection SaaS V2
## Plan de autenticación segura - Staging

Estado: EN DESARROLLO
Rama: feat/saas-v2-auth-rls-staging

### Arquitectura

1. sebas_staging_api
   - Conexion actual del catalogo publico.
   - Mantener permisos restringidos.
   - No conceder acceso general a usuarios.

2. sebas_auth_app
   - Autenticacion de usuarios por boutique.
   - Aislamiento mediante tienda_id y RLS.
   - Credenciales independientes.

3. sebas_global_auth
   - Autenticacion del Super Admin.
   - Acceso restringido a identidad global.

4. sebas_global_ops
   - Operaciones administrativas autorizadas.
   - Gestion de tiendas, propietarios y planes.

### Reglas de seguridad

- No desactivar RLS.
- No utilizar postgres como conexion de la API.
- No conceder BYPASSRLS.
- No ejecutar scripts experimentales directamente en Supabase.
- No desplegar cambios sin pruebas de aislamiento.
- No modificar las boutiques de produccion.

### Validaciones locales completadas

- PostgresTestSafetyTest: 2 pruebas aprobadas.
- AuthTenantPostgresTest: 20 pruebas aprobadas.
- BootstrapAuthJpaRlsTest: 1 prueba aprobada.
- BootstrapSuperadminJdbcTest: 1 prueba aprobada.
- BootstrapRolesPostgresTest: 1 prueba aprobada.

Total: 25 pruebas seleccionadas aprobadas.

### Pendientes

- Adaptar scripts de migracion para Staging.
- Validar privilegios de roles PostgreSQL.
- Verificar contexto de tenant por transaccion.
- Comprobar autenticacion real en entorno aislado.
- Configurar conexiones JDBC separadas en Azure.
- Crear administrador de Demo Boutique SaaS.
- Verificar que boutique 1 no acceda a boutique 2.
- Publicar panel Super Admin SaaS.
