# Sebas Collection | Backend

API REST para una plataforma de comercio electrónico y punto de venta de una tienda de ropa.

Desarrollada con Java 21, Spring Boot y PostgreSQL. Proporciona autenticación, gestión de catálogo e inventario, compras online, ventas presenciales y consultas para reportes.

**Aplicación web:** [www.sebascollection.com](https://www.sebascollection.com/)

**Autor:** Alexander Ancori Conza
**Arquitectura:** Aplicación monolítica organizada por capas.

## Descripción

El backend centraliza las reglas de negocio de Sebas Collection y permite gestionar las ventas realizadas desde la tienda online y desde el punto de venta presencial.

Se integra con un frontend Angular desplegado de forma independiente.

El proyecto conserva el nombre técnico Tienda Sebas en algunos paquetes y configuraciones.

## Tecnologías

- Java 21.
- Spring Boot.
- Spring Web.
- Spring Security y JWT.
- Spring Data JPA e Hibernate.
- PostgreSQL.
- Maven Wrapper.
- GitHub Actions.
- Azure App Service.
- Supabase.

La versión de Spring Boot y las dependencias exactas están definidas en `pom.xml`.

## Funcionalidades

| Módulo | Alcance |
|---|---|
| Autenticación | Registro, login y validación de JWT |
| Usuarios | Roles, estado y administración de cuentas |
| Catálogo | Productos, categorías, géneros, ofertas e imágenes |
| Inventario | Existencias por producto y talla |
| Compras online | Registro de compras y detalle |
| Ventas presenciales | Operaciones del punto de venta |
| Clientes presenciales | Registro de clientes para ventas en tienda |
| Comprobantes | Información para consulta e impresión |
| Reportes | Consultas de ventas, productos, categorías, pagos y canales |

Los importes monetarios se manejan con `BigDecimal`.

Las ventas distinguen su origen ONLINE o PRESENCIAL para facilitar el análisis y los reportes.

## Arquitectura

El backend es una aplicación Spring Boot única; no utiliza microservicios ni Eureka.

Organización por responsabilidades:

| Capa | Responsabilidad |
|---|---|
| controller | Endpoints HTTP |
| dto | Solicitudes y respuestas |
| service | Reglas de negocio |
| repository | Acceso a PostgreSQL |
| entity | Entidades JPA |
| security | Autenticación y autorización |
| config | Configuración de la aplicación |
| exception | Manejo de errores |

Consultar los paquetes del repositorio para la estructura actual.

## Despliegue

| Componente | Entorno |
|---|---|
| API REST | Azure App Service Linux |
| Base de datos PostgreSQL | Supabase |
| Frontend Angular | Azure App Service independiente |
| Sitio público | https://www.sebascollection.com/ |
| Automatización del backend | GitHub Actions |

El despliegue del backend utiliza variables de entorno para separar la configuración de las credenciales.

La base de datos y el almacenamiento de imágenes requieren sus propias estrategias de respaldo.

## Seguridad

- Contraseñas protegidas mediante hash.
- Autenticación JWT.
- Roles ADMIN y CLIENTE.
- Validación del estado y rol del usuario desde la base de datos.
- Restricción de operaciones administrativas.
- Configuración CORS para los orígenes autorizados.
- Uso de HTTPS en producción.

El backend valida los permisos; ocultar opciones en Angular no sustituye esa autorización.

No se incluyen cuentas administrativas ni credenciales reales en este documento.

## Requisitos

- JDK 21.
- PostgreSQL local o una instancia accesible.
- Git.
- Editor compatible con Java.
- Acceso a las variables privadas del entorno.

Verificar herramientas:

```powershell
java -version
git --version
```

El repositorio incluye Maven Wrapper.

## Descargar el proyecto

```powershell
git clone https://github.com/Alexan-22/tienda-sebas-backend.git
cd tienda-sebas-backend
```

Si el repositorio requiere permisos, autenticarse con una cuenta autorizada.

## Base de datos local

Crear una base de datos denominada `TiendaSebas`, respetando el nombre configurado en la URL JDBC.

Ejemplo en PostgreSQL:

```sql
CREATE DATABASE "TiendaSebas";
```

Valores locales habituales:

| Parámetro | Valor |
|---|---|
| Host | localhost |
| Puerto | 5432 |
| Base de datos | TiendaSebas |
| Usuario | postgres |

Crear una base vacía no restaura productos, usuarios, inventario ni ventas existentes.

Para trasladar una instalación con datos se necesita restaurar su respaldo y aplicar los cambios de esquema correspondientes.

## Perfiles de configuración

| Archivo | Uso |
|---|---|
| application.properties | Configuración común |
| application-dev.properties | Desarrollo local |
| application-prod.properties | Producción |

En la configuración documentada del proyecto:

- Desarrollo utiliza `ddl-auto=update`.
- Producción utiliza `ddl-auto=validate`.

En producción, Hibernate valida el esquema; no crea automáticamente las tablas.

## Variables de entorno

| Variable | Función |
|---|---|
| SPRING_PROFILES_ACTIVE | Perfil de ejecución |
| DB_URL | Conexión JDBC PostgreSQL |
| DB_USERNAME | Usuario de base de datos |
| DB_PASSWORD | Contraseña de base de datos |
| JWT_SECRET | Clave de firma de tokens |
| CORS_ALLOWED_ORIGINS | Orígenes permitidos |
| UPLOAD_DIR | Directorio de imágenes |
| JWT_EXPIRATION | Vigencia del token, si está configurada |
| PORT | Puerto HTTP, si está configurado |

No publicar sus valores privados en GitHub.

### Configuración local en PowerShell

Ejecutar en la misma terminal que iniciará el backend:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/TiendaSebas"
$env:DB_USERNAME = "postgres"

$credencial = Get-Credential -UserName "postgres" -Message "Credenciales PostgreSQL"
$env:DB_PASSWORD = $credencial.GetNetworkCredential().Password
```

Configurar también `JWT_SECRET` mediante el mecanismo privado utilizado en la instalación. Su formato debe coincidir con lo que espera la implementación JWT.

No utilizar una clave de ejemplo en producción.

## Ejecutar localmente

```powershell
.\mvnw.cmd spring-boot:run
```

Dirección habitual:

```text
http://localhost:8080
```

Para detener el proceso:

```text
Ctrl + C
```

## Compilar y ejecutar pruebas

```powershell
.\mvnw.cmd clean verify
```

El comando compila y ejecuta las comprobaciones configuradas en Maven. Puede requerir una base de datos y variables de entorno disponibles.

Para generar el paquete:

```powershell
.\mvnw.cmd clean package
```

Los archivos generados se encuentran en `target/`.

La ejecución de estos comandos no implica por sí sola que todos los flujos de negocio estén cubiertos por pruebas automatizadas.

## Rutas de referencia

Base local:

```text
http://localhost:8080
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | /api/generos | Consultar géneros |
| GET | /api/categorias | Consultar categorías |
| GET | /api/productos/public | Consultar catálogo público |
| GET | /api/productos/public/{id} | Consultar producto |

Las demás rutas, cuerpos y permisos deben consultarse en los controladores y DTO actuales del repositorio.

Para operaciones protegidas:

```http
Authorization: Bearer TU_TOKEN
```

### Consultar el catálogo desde PowerShell

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/productos/public" `
    -Method Get -ErrorAction Stop
```

## Imágenes de productos

Directorio de desarrollo:

```text
uploads/productos/
```

El límite multipart configurado es de hasta 10 MB.

En Azure se utiliza un directorio configurado mediante `UPLOAD_DIR`; en la instalación documentada:

```text
/home/uploads/productos
```

Verificar la persistencia del almacenamiento según la configuración de App Service y mantener una copia de respaldo de las imágenes.

No incluir cargas privadas o datos de clientes en el repositorio.

## Reportes

La API proporciona información para analizar:

- Ventas por período.
- Ventas online y presenciales.
- Detalle de ventas.
- Productos.
- Medios de pago.
- Categorías.
- Canales de venta.

El frontend utiliza estos datos para presentar los reportes y generar los archivos PDF y Excel.

## Pagos: alcance actual

El sistema registra medios de pago.

La confirmación de Yape requiere verificación manual en el flujo actual. Los pagos con tarjeta de demostración no representan una integración bancaria real.

No recibir datos reales de tarjetas mediante un formulario de demostración.

## Configuración en Azure

1. Configurar el runtime compatible con Java 21.
2. Establecer el perfil `prod`.
3. Configurar DB_URL, DB_USERNAME y DB_PASSWORD.
4. Configurar JWT_SECRET.
5. Establecer CORS_ALLOWED_ORIGINS con el origen del frontend.
6. Configurar UPLOAD_DIR y verificar su persistencia.
7. Verificar que PostgreSQL tenga el esquema esperado.
8. Ejecutar el despliegue y revisar sus registros.
9. Comprobar catálogo, autenticación, imágenes, compras, POS y reportes.

Para el dominio principal, el origen es:

```text
https://www.sebascollection.com
```

Si también se utiliza el dominio sin `www`, configurar su redirección o autorizarlo cuando corresponda.

No incluir rutas ni una barra final en los valores de origen CORS.

## Problemas frecuentes

### Error de conexión a PostgreSQL

Revisar:

- URL JDBC.
- Usuario y contraseña.
- Disponibilidad de la base de datos.
- Requisitos de conexión y SSL indicados por Supabase.
- Variables cargadas en el proceso.

### Fallo al validar el esquema

Con `ddl-auto=validate`, las tablas deben existir y coincidir con las entidades.

Aplicar las migraciones o restaurar un esquema compatible antes del arranque.

### Error CORS

Comprobar que CORS_ALLOWED_ORIGINS incluya el origen exacto del frontend y reiniciar el servicio después de modificar su configuración.

### HTTP 401 o 403

Verificar token, vencimiento, estado de la cuenta y rol autorizado.

### Error 503 en Azure

Revisar el estado de App Service, los registros de arranque, las variables y las cuotas del plan contratado.

### Imágenes no disponibles

Revisar UPLOAD_DIR, persistencia, permisos y existencia de los archivos.

## Trabajo realizado

- Diseño y desarrollo de la API REST.
- Implementación de autenticación y permisos.
- Gestión del catálogo y stock por talla.
- Integración de compras online y ventas presenciales.
- Tratamiento monetario con BigDecimal.
- Consultas para reportes por período y canal.
- Migración de datos a PostgreSQL en Supabase.
- Configuración del despliegue en Azure mediante GitHub Actions.
- Integración con el frontend y el dominio público.

## Mejoras pendientes

- Integración con un proveedor de pagos.
- Ampliación de pruebas automatizadas.
- Automatización y documentación de cambios de esquema.
- Mejora de supervisión y alertas.
- Verificación periódica de restauración de respaldos.

## Autor

**Alexander Ancori Conza**

GitHub: [Alexan-22](https://github.com/Alexan-22)

Aplicación: [Sebas Collection](https://www.sebascollection.com/)
