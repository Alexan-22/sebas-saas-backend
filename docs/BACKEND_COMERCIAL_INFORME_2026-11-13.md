# Backend comercial SaaS V2 — 2026-11-13

## Alcance y preservación
Trabajo limitado al backend; Superadmin solo consultado. No se conectó con Supabase, no se ejecutaron migraciones ni V18/V19, ni se modificaron Editor, imágenes, JWT, RBAC o RLS. No se realizaron commits, push ni despliegues. La captura inicial y la comparación SHA-256 están en BACKEND_COMERCIAL_BASELINE_2026-11-13.json y BACKEND_COMERCIAL_PRESERVACION_2026-11-13.json: ningún archivo previo desapareció; únicamente cinco archivos existentes cambiaron dentro del alcance comercial. El resto conserva sus hashes. Durante el trabajo se observó externamente el commit 46e515a; no fue creado por este agente.

## Problemas y solución implementada
- Principal sin selección persistente cuando hay varios planes: se incorpora selección en la tabla existente saas_configuracion, clave plan_comercial_principal. Prevalece sobre app.saas.plan-comercial-id; se conserva el comportamiento anterior como respaldo si no hay clave.
- Se exige plan activo, se impide desactivar el principal y el último activo. Bloqueo asesor transaccional de catálogo y bloqueo de boutique serializan selección, edición y altas. Tiempo máximo de espera de bloqueo: cinco segundos.
- Altas conservan precio y moneda históricos, validan fechas, duración y relaciones, y rechazan períodos solapados incluso si un estado persistido VENCIDA está desactualizado. El catálogo no modifica contratos anteriores.
- Auditoría de selección, catálogo y alta dentro de la misma transacción: si falla la auditoría se revierte la escritura. PUT repetido del mismo principal no duplica auditoría; POST concurrente solapado devuelve conflicto para el duplicado.
- Reportes usan vigencia efectiva, precio histórico y moneda filtrada. No presentan contratos como cobros confirmados. Se conserva mrr_contratado por compatibilidad, como alias del importe histórico de contratos activos: NO es ingreso cobrado ni MRR normalizado por duración. ingresos_cobrados_disponibles=false.
- Controladores comerciales requieren SUPER_ADMIN y conservan el filtro JWT existente. Errores de entrada 400, inexistente 404, conflictos 409, indisponibilidad SQL 503 sin detalles internos.

## Contratos HTTP
- GET /api/superadmin/suscripciones/plan-comercial: conserva planId y añade origen y motivo; planId=null si no se puede resolver un principal activo.
- NUEVO PUT /api/superadmin/suscripciones/plan-comercial, JSON {"planId":123}: selección persistente, 200 con la selección; 400/404/409 según validación.
- POST /api/superadmin/suscripciones y gestión de /api/superadmin/planes: rutas existentes; se endurecen integridad, auditoría y conflictos, sin reescribir contratos históricos.
- GET /api/superadmin/reportes/saas/resumen, /planes y /suscripciones: filtros opcionales tiendaId, planId, moneda (PEN por defecto), estado efectivo, desde, hasta y fechaReferencia (día de Lima por defecto). Parámetros desconocidos se rechazan con 400. Fechas ISO; desde/hasta filtran contratos que se cruzan con el intervalo inclusivo; fecha_fin del contrato es exclusiva. Los conteos de boutiques siguen tiendaId; filtros de contrato afectan suscripciones/importes; planes_activos es catálogo por moneda/plan.

## Archivos de implementación
Existentes modificados frente a captura inicial:
- controller/SuperAdminPlanController.java
- controller/SuperAdminSuscripcionController.java
- controller/SuperAdminReporteSaasController.java
- service/SuscripcionService.java
- test/service/SuscripcionPlanComercialTest.java (conserva sus dos casos y aserciones; adapta construcción al servicio extraído).
Nuevos: service/PlanComercialService.java, service/ReporteComercialService.java, controller/ComercialExceptionHandler.java, test/integration/ComercialHttpPostgresTest.java, test/integration/ComercialPostgresFixture.java y scripts/test-backend-comercial.cjs. Rutas Java bajo src/main/java/com/tiendasebas/tiendasebas o src/test/java/com/tiendasebas/tiendasebas según corresponda.

## Validación y reproducción
El runner invoca Maven test con lista explícita de clases, Java 21 y dependencias de caché local. Limpia variables de conexión heredadas. Requiere JDK ms-21.0.12.1, Maven 3.9.16 en caché y PostgreSQL 17 instalado localmente. Debido a limitaciones de rutas del entorno, copia JDK y artefactos usados dentro de target; no cambia versiones ni pom.xml. Ejecutar: node scripts/test-backend-comercial.cjs; solo unitarias: añadir --unit.

La integración crea un clúster PostgreSQL privado en target/comercial-postgres/fixture-*, con puerto loopback aleatorio y credenciales ficticias aleatorias. Verifica data_directory y current_database antes de usarlo. Crea únicamente el esquema de fixture, sin leer scripts de migración; arranca Spring mínimo con los controladores, servicios y seguridad reales. No sustituye un ensayo de la aplicación completa ni verifica el esquema/RLS de Supabase.

Las 61 pruebas conocidas NO se declaran reejecutadas: 43 casos de CancelacionCodHttpJpaTest ejecutan V19 y otro caso de arranque ejecuta SQL de migración. Esos 44 se conservaron y excluyeron para cumplir la prohibición de ejecutar migraciones. Las cuatro clases previas permitidas suman 17 casos.

## Pendientes y riesgos concretos
- Angular: integrar el PUT del selector, retirar el aviso de API inexistente, mostrar motivo/origen y manejar 409/503. En reportes, enviar filtros HTTP y etiquetar importe contratado; no mostrarlo como cobrado. Ningún Angular fue editado.
- No existe registro durable de claves Idempotency-Key/respuestas. El rechazo de solapamientos evita duplicación de contratos; no permite recuperar exactamente la respuesta original tras perder la conexión. Para ello se requiere un recibo persistente con clave, actor, hash de petición y resultado, índice único y política de retención; definir y autorizar su migración antes de implementarlo.
- No hay infraestructura de cobros confirmados/conciliación usada por estos reportes. Renovación automática y pagos NO implementados ni simulados. Una renovación explícita necesita contrato de comando, autorización, idempotencia y auditoría; hoy puede darse de alta otro período contiguo sin alterar el contrato previo.
- La clave de configuración reutiliza una tabla genérica, sin FK al plan: SQL externo podría invalidarla. La lectura falla de forma cerrada y no selecciona otro plan silenciosamente. Los bloqueos protegen los caminos de aplicación modificados, no escrituras externas que los ignoren.
- No se propone migración necesaria para esta selección. V19 sigue pendiente según lo informado por el usuario y no se ejecutó. Validar en entorno aislado que tablas/columnas/privilegios y políticas reales coinciden antes de integrar. No se inspeccionaron credenciales ni datos reales.

## Validación de integración pendiente
1. Preparar staging aislado y revisar su esquema y permisos por un proceso autorizado; no aplicar V18/V19 como parte de este trabajo.
2. Ejecutar pruebas completas que usan migraciones únicamente cuando exista autorización específica y base desechable.
3. Con Angular actualizado, comprobar selección/persistencia, cambio y rechazo de desactivación del principal; altas duplicadas, concurrencia y auditoría.
4. Conciliar reportes con contratos ficticios de distintas monedas, fechas y boutiques; comprobar accesos de roles y tokens revocados.
5. Definir infraestructura de pagos/recibos idempotentes antes de publicar cifras de ingresos cobrados o automatizar renovaciones.

## Resultado final comprobado
- Maven test: 42 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS. Evidencia: BACKEND_COMERCIAL_TESTS_2026-11-13.log (ejecución final).
- 25 nuevas pruebas HTTP/PostgreSQL: selección y persistencia tras reiniciar Spring, validaciones, exclusión de desactivación, carreras de catálogo/altas, duplicados, rollback de auditoría, precios/monedas históricos, filtros y autorización JWT con usuarios ficticios.
- 17 casos previos aprobados: JwtCredencialesRevocacionTest (5), RbacEmpleadoGestionTest (7), RbacRevocacionTenantTest (3), SuscripcionPlanComercialTest (2).
- git diff --check: correcto. Captura de preservación: ningún archivo inicial eliminado; cambios limitados a los cinco archivos comerciales descritos. Archivos de Editor, imágenes, seguridad previa y V18/V19 conservan SHA-256 inicial.
- La fixture terminó y eliminó su clúster privado. Los intentos iniciales fallidos por Docker y pg_ctl son limitaciones del entorno; la ejecución final usa postgres.exe directamente y terminó satisfactoriamente.
