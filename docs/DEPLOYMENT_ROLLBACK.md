# Despliegue escalonado y recuperación

La ejecución queda BLOQUEADA hasta cumplir los criterios de aceptación y obtener las autorizaciones correspondientes. Este procedimiento fue revisado contra el código y los workflows y queda pendiente de ensayo integral con dos binarios y el entorno final.

## Antes del merge

1. Confirmar los SHA de los dos PR y sus checks obligatorios. Validar el mismo conjunto de cambios en una base desechable con esquema equivalente al destino usando un rol de aplicación sin superusuario, sin propiedad de tablas y sin BYPASSRLS.
2. A través de operaciones de lectura autorizadas, identificar la rama y el destino reales de Azure, la revisión actualmente desplegada, el rol efectivo, el historial SQL y las políticas instaladas. Registrar los identificadores y el punto de recuperación sin registrar credenciales.
3. Hacer un ensayo aislado de V26 con datos anteriores. Si el destino ya ejecutó otra versión bajo el mismo identificador, comparar su esquema y checksum y preparar una migración posterior reconciliada. Conservar la versión ya aplicada.
4. Obtener autorización para el merge del backend #15. Confirmar por separado el mecanismo de despliegue y obtener la autorización correspondiente. El YAML del backend no escucha `main`. La configuración de la aplicación usa un esquema administrado externamente y Hibernate valida en producción. El despliegue del binario no sustituye la ventana SQL autorizada.
5. Con el backend nuevo y la tienda anterior, verificar la venta registrada en un entorno aislado. En producción usar comprobaciones de lectura y pedidos sintéticos existentes autorizados. Verificar por lectura la configuración efectiva, el esquema y las políticas del destino. Probar la denegación del POST únicamente contra el entorno aislado.
6. Solicitar autorización para el merge del frontend #26 cuando el backend esté verificado. Un push a `main` activa el workflow de Azure Static Web Apps. Confirmar el SHA del bundle publicado, la configuración de producción, la tienda y la ruta de inicio de sesión del checkout.

## Compatibilidad esperada por contrato

| Combinación | Comportamiento por código |
| --- | --- |
| Backend anterior y frontend candidato de producción | El checkout registrado usa el contrato anterior. Un usuario anónimo recibe la opción de iniciar sesión. |
| Backend candidato y frontend anterior de `main` | El endpoint registrado se conserva. El frontend anterior mantiene el envío invitado apagado. |
| Ambos candidatos con configuración de producción | El frontend deja el envío invitado apagado y el backend deniega el alta. |
| Ambos candidatos con configuración aislada | El frontend usa el proxy de bucle local y el backend exige perfil y flag explícitos. |

El ensayo cruzado de binarios y la verificación efectiva de Azure siguen siendo puertas de aceptación.

## Recuperación

El ensayo aislado de `SET NOT NULL` confirmó que un pago invitado existente impide restaurar las restricciones anteriores y que se conservan los datos ante el rechazo. El rollback del binario y la coordinación con Azure siguen pendientes. [Resultados del ensayo](TEST_RESULTS.md)

1. Detener la promoción y mantener apagada la compra invitada. Registrar el SHA y el incidente. Confirmar qué versión de cada componente y qué esquema están activos.
2. Con aprobación, recuperar primero el artefacto anterior del frontend cuando el fallo se observe en la tienda. Recuperar el backend usando el último binario validado, tras comprobar su comportamiento con la versión actual del esquema.
3. Conservar el esquema aditivo de V26 mientras se recupera el código. Si existen pedidos invitados, conservar sus pagos, fichas y accesos. La restauración de restricciones `NOT NULL` requiere comprobar ausencia de valores nulos y conciliar esos pedidos. La eliminación de tablas o una restauración completa exige un procedimiento probado y autorización explícita.
4. Verificar los flujos de lectura, el aislamiento del tenant, el estado de pedidos y los métodos de pago. Si no se puede volver al binario anterior con los datos presentes, preparar una corrección hacia adelante y ensayarla con copia aislada antes de autorizar su despliegue.
