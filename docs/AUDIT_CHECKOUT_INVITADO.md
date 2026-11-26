# Auditoría del checkout invitado

Inspección realizada el 26 de noviembre de 2026 en Lima.

## Estado inicial

| Componente | PR | Base de main comprobada en GitHub y Git local | SHA inicial del PR confirmado en GitHub | Cambios |
| --- | --- | --- | --- | --- |
| Backend | [#15](https://github.com/Alexan-22/sebas-saas-backend/pull/15) | `8919118367412bc771e179db4dbae22ea5677f0b` | `8dfa497bace9fb79ab9537ad95fc0eb1f992d7d9` | 4 commits, 16 archivos |
| Frontend | [#26](https://github.com/Alexan-22/sebas-saas-tienda/pull/26) | `3b9d40b46f8daff1e48b5a5b11aa767796473b12` | `62086bd7c7601f04272c11f6d7750310c0f4f664` | 2 commits, 3 archivos |

Ambos PR estaban abiertos contra `main` desde `test/checkout-invitado-559`. Se usaron clones aislados de las ramas de trabajo. El worktree original del backend tenía un directorio de documentación sin seguimiento. Su archivo SQL de boutiques ficticias se revisó y se incorporó a la copia aislada porque los ensayos ya dependían de esa fixture.

## Hallazgos y correcciones

| Hallazgo | Corrección | Estado |
| --- | --- | --- |
| El backend exponía la operación de compra pública sin feature flag y el frontend tenía el envío activado en la rama del PR. | El POST exige la propiedad `sebas.checkout.invitado.enabled=true` y el perfil `checkout-invitado-test` sin `prod`. Producción y desarrollo normal de Angular tienen el flag apagado. Se mantiene la compra registrada y la consulta de pedidos ya emitidos mediante su token. | COMPLETADA |
| La confirmación de transferencia mostraba todas las cuentas. | El POST devuelve el identificador de la cuenta que validó el servidor. La confirmación muestra solo esa cuenta si continúa disponible. Una respuesta anterior o una cuenta ausente muestra un aviso de contacto. | COMPLETADA |
| Una política permisiva preexistente podía ampliar el alcance de las tablas de V26. | V26 aplica un límite RLS restrictivo por tenant en las tres tablas nuevas. El ensayo incluye una política permisiva anterior y conecta con un usuario de aplicación real, sin propiedad ni BYPASSRLS. | COMPLETADA en SQL local |
| Los permisos y las políticas del bootstrap anterior solo contemplaban Yape registrado. | La fixture de pruebas amplía de forma acotada altas de invitados y transferencia. El rol de aplicación accede únicamente a sus tiendas y los ensayos usan SQL transaccional. La fixture vive en `src/test/resources`. | COMPLETADA para pruebas |
| El traspaso informa que Azure usa el rol `postgres` con BYPASSRLS. | Comprobar el rol efectivo del entorno y sus políticas requiere acceso autorizado a Azure. RLS por sí sola no contiene a un rol con BYPASSRLS. | BLOQUEADA |
| La mayoría de los nombres de usuario, servidores y secretos se suministran por entorno. | Se comprobó la presencia de `DB_PASSWORD` por nombre y se retiró del proceso Maven junto con las variables externas de DB y Spring. Las credenciales de los clústeres se generan para cada fixture. | COMPLETADA |

## Workflows observados

- El backend tenía cero checks en el PR #15. Su workflow existente despliega a `sebas-saas-api-dev` por push a `deploy/sebas-saas-staging` o ejecución manual y compila con `-DskipTests`. Un push a la rama del PR y un merge a `main` quedan fuera de ese trigger. Todavía falta comprobar integraciones externas de Azure. [Checks del PR #15](https://github.com/Alexan-22/sebas-saas-backend/pull/15/checks)
- En el frontend pasó `Build and Deploy Job`. Se omitió `Close Pull Request Job` porque solo se ejecuta con `pull_request.closed`. La sincronización del #26 dispara un despliegue de vista previa de Azure Static Web Apps. Un push a `main` activa el workflow de publicación. El flujo de Amy Fashion se ejecuta manualmente. [Checks del PR #26](https://github.com/Alexan-22/sebas-saas-tienda/pull/26/checks)
- Se agregaron dos workflows de validación sin tareas de despliegue. Sus resultados remotos deberán corresponder al SHA exacto propuesto. La lectura remota por Git confirmó que los dos SHA iniciales siguen al frente de sus ramas. El intento de autenticación para actualizar el backend se detuvo en el almacén de credenciales de Windows, sin efectuar un push.

En la fixture de transferencia se concedió `UPDATE` sobre la columna `id` para el `SELECT FOR SHARE`. RLS limita las filas bloqueables a la tienda activa y `WITH CHECK (false)` impide la escritura con ese rol. [Referencia SELECT](https://www.postgresql.org/docs/17/sql-select.html) · [Referencia CREATE POLICY](https://www.postgresql.org/docs/17/sql-createpolicy.html)

## Alcance operativo de pagos

Yape y transferencia registran el pedido y el pago manual pendiente. Las pruebas emplean cuentas ficticias. La comprobación de una transferencia real, la aprobación de los pagos invitados y cualquier carga de comprobantes de invitados requieren una validación funcional independiente. Los enlaces privados de consulta expiran y el token nunca forma parte de la URL. Un reintento con la misma clave no emite otra credencial.
