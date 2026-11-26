# Resultados de pruebas

Ejecuciones locales del 26 de noviembre de 2026, Lima.

| Repositorio | SHA de código verificado | Ejecución | Resultado |
| --- | --- | --- | --- |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | Compilación Java 21 y empaquetado Spring Boot | COMPLETADA. Se generó el JAR. |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | `CheckoutInvitadoConfiguracionTest` | 5 aprobadas. |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | `CheckoutInvitadoMigracionRlsTest` | 2 aprobadas. |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | `CheckoutInvitadoServiceTest`, `YapeCheckoutSafetyTest`, `TransferenciaImagenTest` | 14 aprobadas. |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | `RbacVentasSeguridadTest` | 6 aprobadas. |
| Backend | `494081770214af300a0d90ea50b3b1527a0a5326` | `BootstrapCheckoutRlsTest` | 1 aprobada. Compra invitada, compra registrada, stock, concurrencia y aislamiento. |
| Frontend | `3f7f124ba5b87d79017536ba50e61f904846e5c0` | `npm test -- --watch=false` | 133 aprobadas en 19 archivos. |
| Frontend | `3f7f124ba5b87d79017536ba50e61f904846e5c0` | `npm run build` y `npm run build -- --configuration checkout-invitado-test` | Ambos códigos de salida 0. El build de producción emitió advertencias de dependencias CommonJS. |

Maven `verify` terminó con 28 pruebas aprobadas, cero fallos y cero omitidas. La ejecución incluyó compilación y empaquetado. El backend usó los puertos aislados 54250, 54334 y 54368, y el frontend compiló sus dos configuraciones. Los reportes preexistentes de checkout, V26, Yape y RBAC sumaban 12 casos y carecían de un SHA consignado. Se volvieron a ejecutar las clases identificables sobre el SHA backend indicado. La versión ampliada de V26 aporta un segundo caso. El SHA `96ef60a1a01615cf11af77adec2397e0cf0ee610` compiló y se empaquetó. Su ejecución HTTP se detuvo en la cabecera Host. El candidato intermedio `0d6e75d71d0766a8ba74c998c72958da076a6e80` obtuvo 27 aprobadas y un fallo HTTP en la transferencia. Ambos fallos quedaron resueltos en el SHA verificado.

## Entorno y cobertura

- PostgreSQL nativo 17 en clústeres privados con SCRAM, creados bajo `target/comercial-postgres`. El daemon Docker local estaba detenido. Cada clúster escucha en `127.0.0.1` y la fixture comprueba el directorio real, puerto y base `comercial_aislado` antes de aplicar SQL.
- Los dos casos de V26 verificaron con datos ficticios que la misma conexión física del pool cambia de tenant por transacción y recupera visibilidad cero tras rollback y commit. El login `checkout_test_user` carecía de superusuario, BYPASSRLS y propiedad de las tres tablas. Se verificó la conservación de relaciones y usuarios en pagos Yape, transferencias y comprobantes anteriores.
- El ensayo HTTP del candidato separa el rol de migración `comercial_fixture` del rol utilizado por Spring, `sebas_auth_app`. Se ejecuta contra Tomcat y PostgreSQL en loopback. Las políticas de invitado de ese ensayo forman parte de la fixture aislada.
- En las primeras ejecuciones hubo errores por acceso a rutas de Windows, una fixture sin seguimiento y el bloqueo de la cabecera `Host` en la JVM de pruebas. Las rutas se resolvieron con un alias local al espacio autorizado. Se incorporó la fixture y el cliente HTTP del ensayo pasó a usar curl con loopback y sin proxy para conservar la cabecera `Host` de cada tienda ficticia. Los registros preliminares incluyen cambios aún sin commit sobre `8dfa497bace9fb79ab9537ad95fc0eb1f992d7d9` y `62086bd7c7601f04272c11f6d7750310c0f4f664`.
- Los archivos del contrato de compra registrada coinciden con la base local de `main` en ambos repositorios. Quedan pendientes el ensayo cruzado de binarios, la aprobación de pagos manuales invitados, la revisión del destino de Azure y la ejecución de Actions en los candidatos.

## Escenario HTTP con rol restringido

En el SHA `494081770214af300a0d90ea50b3b1527a0a5326`, el ensayo HTTP pasó en PostgreSQL `127.0.0.1:54250`, base `comercial_aislado`. El rol de migración fue `comercial_fixture` y la aplicación se conectó como `sebas_auth_app`. Se comprobaron el login y el checkout registrados, Yape y transferencia invitados con pagos ficticios pendientes, la cuenta seleccionada, el listado administrativo por tienda y la consulta de un pedido usando su código privado. La otra tienda y un código incorrecto devolvieron 404. También pasaron el stock insuficiente, el rollback ante un fallo de detalle, los reintentos simultáneos, la competencia por la última unidad y el rechazo a una edición de cuenta bancaria por el rol de checkout. La comprobación administrativa se hizo por API.

## Ensayo de recuperación SQL

Sobre el SHA `0d6e75d71d0766a8ba74c998c72958da076a6e80`, se aplicaron B000, V1 a V12 y V26 en `127.0.0.1:55439`, base aislada `sebas_checkout_rollback_tx`, con el rol de migración `sebas_audit_owner`. Volver a exigir `NOT NULL` fue viable dentro de una transacción con filas registradas. Con un pago invitado existente, PostgreSQL rechazó el cambio con SQLSTATE 23502 y conservó los dos pagos y la ficha invitada. El clúster se detuvo al terminar.

## Repetición local

En el backend, usar Java 21, Maven, curl y un binario PostgreSQL local. Fuera de Windows, indicar el directorio de herramientas mediante `POSTGRES_TEST_BIN`. Retirar las variables de DB y Spring externas del proceso de pruebas. Ejecutar el comando exacto de `.github/workflows/checkout-ci.yml`. Los puertos se asignan para cada clúster privado.

En la tienda, usar Node 24 y `npm ci`. La configuración `checkout-invitado-test` permite servir el frontend contra el proxy de loopback local. Para el ensayo manual, apuntar el proxy a la aplicación de prueba verificada. La aplicación debe tener un rol y un clúster de pruebas. El perfil Spring `checkout-invitado-test` requiere activar explícitamente `sebas.checkout.invitado.enabled=true`.
