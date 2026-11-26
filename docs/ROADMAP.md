# Hoja de ruta

| Fase | Estado | Condición de salida |
| --- | --- | --- |
| Auditoría de ramas y workflows | COMPLETADA | SHA de los PR contrastados y triggers del repositorio registrados. |
| Cierre por defecto y protección RLS | COMPLETADA en el código local | Compilar, probar los candidatos y registrar resultados en TEST_RESULTS. |
| Validación local y comprobación de Azure | BLOQUEADA fuera del entorno local | Aprobar pruebas críticas y confirmar rol, esquema y políticas del destino. |
| Despliegue escalonado y recuperación | BLOQUEADA | Ensayo cruzado, checks remotos y autorizaciones separadas para backend y frontend. |
| Integración Culqi | PENDIENTE | Tras estabilizar checkout. Iniciar en sandbox con verificación de webhooks, idempotencia y estados de pago. |
| SaaS Admin | PENDIENTE | Tras estabilizar checkout. Definir alcance de tiendas, usuarios, suscripciones, roles, reportes y RLS. |
