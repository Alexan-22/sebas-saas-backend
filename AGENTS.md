# Sebas SaaS

- Lee primero el estado y la evidencia en `docs/PROJECT_STATUS.md` y `docs/TEST_RESULTS.md` cuando trabajes en el backend. El backend contiene el registro de continuidad de ambos repositorios.
- Verifica las ramas, los SHA remotos y las modificaciones locales antes de editar. Trabaja en la rama del PR o en una copia aislada. Conserva el trabajo existente del usuario y evita `git reset --hard`, `git clean -fd` y force push.
- Solicita autorización explícita para merges, despliegues, operaciones destructivas y cambios sensibles de producción. Antes de hacer push, verifica los triggers y destinos de todos los workflows, incluidos los eventos del PR.
- Antes de escribir datos en pruebas, verifica servidor, puerto, base, usuario y que la instancia sea desechable y aislada. Usa datos ficticios, credenciales exclusivas de prueba y un usuario de aplicación que carezca de superusuario, BYPASSRLS y propiedad de tablas. No uses credenciales ni bases de producción.
- El backend decide si se aceptan compras invitadas. El valor por defecto debe ser desactivado. Conserva las compras registradas y prueba el contexto RLS y su limpieza en el pool.
- Guarda los SHA exactos y los resultados comprobados. Usa PENDIENTE, EN PROGRESO, COMPLETADA o BLOQUEADA. Mantén los secretos y datos personales fuera de la documentación y los logs compartidos.
