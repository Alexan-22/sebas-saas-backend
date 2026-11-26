# Estado del proyecto Sebas SaaS

Actualizado el 26 de noviembre de 2026, Lima.

| Fase | Backend | Frontend |
| --- | --- | --- |
| Inspección de código, Git y workflows | COMPLETADA | COMPLETADA |
| Corrección del cierre por defecto | COMPLETADA en rama local | COMPLETADA en rama local |
| Compilación y regresión de checkout | COMPLETADA localmente | COMPLETADA localmente |
| V26 y RLS del esquema de pruebas | COMPLETADA en PostgreSQL aislado | PENDIENTE de ensayo cruzado con dos versiones |
| Verificación de rol y esquema efectivos de Azure | BLOQUEADA | BLOQUEADA por verificación del backend |
| Checks en los SHA candidatos y actualización de PR | BLOQUEADA por autenticación de GitHub | BLOQUEADA por despliegue automático de vista previa |
| Merge, despliegue y ensayo de recuperación | BLOQUEADA | BLOQUEADA |
| Culqi y SaaS Admin | PENDIENTE | PENDIENTE |

**Dictamen de merge**. Backend #15 y frontend #26, **NO APTO PARA MERGE**.

Las ramas locales `test/checkout-invitado-559` contienen los candidatos `494081770214af300a0d90ea50b3b1527a0a5326` (backend) y `3f7f124ba5b87d79017536ba50e61f904846e5c0` (frontend). Se usaron copias aisladas de las ramas. Los resultados se registran por SHA de código.

El PR backend se puede actualizar cuando haya una sesión de GitHub con permiso de escritura. El siguiente punto de continuidad es confirmar el rol efectivo de Azure, el esquema y el historial real de V26, comprobar los checks en los candidatos y probar el despliegue escalonado. La actualización del frontend requiere autorización para la vista previa de Azure que dispara la sincronización del PR.

- [Auditoría y estado inicial](AUDIT_CHECKOUT_INVITADO.md)
- [Resultados de pruebas](TEST_RESULTS.md)
- [Despliegue escalonado y recuperación](DEPLOYMENT_ROLLBACK.md)
- [Hoja de ruta](ROADMAP.md)
