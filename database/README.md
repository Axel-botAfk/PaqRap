# Base de datos MySQL de PaqRap

Para levantar una instancia local aislada con Docker y las tres semillas,
consulta [DOCKER_LOCAL.md](DOCKER_LOCAL.md).

## Ejecución en Workbench

Abrir [`paqrap_mysql.sql`](paqrap_mysql.sql) y ejecutar el archivo completo sobre **MySQL 8.0.16 o superior**. Crea `paqrap`, selecciona la base, construye las tablas en orden de dependencia y carga una muestra pequeña. El script está pensado para una base nueva; no se debe ejecutar dos veces sobre las mismas tablas. No contiene `DROP`.

El `SELECT` final debe mostrar **37 vehículos, 10 pedidos, 2 bloqueos y 3 mantenimientos**. Como comprobaciones adicionales:

```sql
USE paqrap;
SELECT p.codigo, p.registro_original, c.id AS cliente, u.x, u.y,
       p.cantidad, p.registrado_en, p.limite_en
FROM pedido p
JOIN cliente c ON c.id = p.cliente_id
JOIN ubicacion u ON u.id = p.destino_id
ORDER BY p.id;

SELECT b.id, b.inicio, b.fin, v.orden, u.x, u.y
FROM bloqueo b
JOIN bloqueo_vertice v ON v.bloqueo_id = b.id
JOIN ubicacion u ON u.id = v.ubicacion_id
ORDER BY b.id, v.orden;
```

El script se ha preparado para Workbench, pero **no se ha ejecutado contra una instancia de MySQL**: no hay credenciales de esta instalación en el proyecto. El cliente local está instalado, pero no se intentó cambiar la base de datos del equipo.

## Qué datos se usan

[`datos/`](../datos/README.md) y `datos/ejemplo/` solo contienen README, así que ahí no hay filas para insertar. La muestra del script procede, sin editar los archivos, de:

- `codigo/paqrap/datos/reales/ventas.202609.txt`: primeras diez líneas.
- `codigo/paqrap/datos/reales/bloqueo.2609.txt`: primeras dos líneas.
- `codigo/paqrap/datos/reales/mant.preventivo.09.10.txt`: primeras tres líneas.

`registro_original` conserva cada línea literal, incluidos los ceros iniciales, y `archivo_fuente` conserva el nombre y periodo. Las fechas, cantidades y coordenadas son su interpretación relacional. La flota, los almacenes y sus propiedades proceden de `DatosCaso` y `TipoVehiculo`, no de estos archivos. Las tablas de ejecuciones, planes, rutas y entregas comienzan vacías porque los TXT contienen **entradas**; esos resultados solo deben insertarse cuando `Simulador` los produzca.

Los archivos son texto con formato propio. `LOAD DATA INFILE` no basta para los tres: el año y mes se obtienen del nombre, ventas genera códigos correlativos, bloqueos tiene una cantidad variable de vértices y mantenimiento usa otra sintaxis. Para cargar todos los meses sin perder trazabilidad:

1. Registrar cada archivo en `archivo_fuente`, conservando nombre, periodo y, si se desea, SHA-256.
2. Leer las ventas con `LectorVentas` y guardar por línea en `pedido`; hacer `upsert` de `cliente` y `ubicacion`. Guardar el texto literal y la línea física. El código `P-00001` se repite por mes, por eso la clave de negocio es `(archivo_id, codigo)`.
3. Leer cada línea de bloqueos con `LectorBloqueos`, guardar `bloqueo` y la poligonal ordenada en `bloqueo_vertice`. La clase `Bloqueo` solo expone nodos ya expandidos; el importador debe conservar también los pares originales de la línea para poder reconstruir la poligonal exacta.
4. Leer mantenimiento con `LectorMantenimiento` y guardar cada relación fecha–vehículo en `mantenimiento_programado`.
5. Hacer cada archivo en una transacción JDBC con consultas parametrizadas. Verificar conteos y rechazar una carga incompleta; no alterar los TXT.

`ventas202609`, `202609.bloqueadas` y otros archivos de demostración solo se cargarían como **otras fuentes** si se necesitan para un escenario específico. No se convierten en tablas nuevas. Tampoco se persisten las estructuras efímeras del algoritmo (lista tabú, vecindarios, matriz de distancias).

## Correspondencia con Java y relaciones

| Java actual | Tabla y relación | Estado / cambio necesario |
| --- | --- | --- |
| `Ubicacion` | `ubicacion`, usada por almacén, pedido y vehículo | Valor de dominio correcto; persistir una fila por `(x,y)`. |
| `Pedido.clienteId` | `cliente` | Es un catálogo de códigos para la FK; el proyecto no tiene una clase `Cliente` con datos personales. |
| `Almacen`, `TipoAlmacen` | `almacen.ubicacion_id → ubicacion.id` | Modelo de dominio correcto; central con stock lógico infinito (`capacidad_maxima = NULL`), intermedios 1000. |
| `TipoVehiculo`, `Vehiculo`, `ConfiguracionFlota` | `vehiculo.tipo_codigo → tipo_vehiculo.codigo`; instantáneas en `ejecucion_tipo_vehiculo` y `ejecucion_vehiculo` | Modelo de dominio correcto; guardar estado y configuración por corrida, no en el catálogo estático. |
| `Pedido` | `pedido` (original del TXT) y `pedido_parte` (partes por corrida); `pedido.cliente_id → cliente.id` | Requiere un adaptador para rehidratar partes: el constructor que acepta `idOriginal` y `pagaAcondicionamiento` es privado. |
| `Bloqueo` | `bloqueo` + `bloqueo_vertice` ordenados | Reconstruir con `Bloqueo.dePoligonal` al leer; el conjunto expandido de nodos es derivado. |
| `PlanMantenimiento` | `mantenimiento_programado` une fechas y vehículos | Reconstruir el `Map<LocalDate, Set<String>>` desde las filas. |
| `Solucion`, `Ruta` | `plan`, `ruta`, `ruta_parada` y `plan_pedido_no_asignado` | `ruta_parada` es la tabla intermedia N:M entre rutas y partes a través de versiones de plan; conserva el orden. |
| `Entrega`, `Averia`, `MedicionDePlanificacion`, `ResumenSimulacion` | `entrega`, `averia`, `medicion_planificacion`, `resumen_simulacion`, `resumen_dia`, `pedido_vencido` | Guardar solo eventos efectivos y resumen final. Las entregas planeadas siguen siendo `ruta_parada`, no `entrega`. |

Las clases de `codigo/paqrap` **son clases de dominio, no entidades JPA**: no tienen `@Entity`, constructores de hidratación ni mapeos. Mantenerlas evita alterar el planificador y permite usar `JdbcTemplate` en `backend`. Con JDBC **no corresponde usar `@JoinColumn`**: las uniones están definidas por las claves foráneas del SQL. Si se opta por JPA en otra fase, las referencias serían `Pedido → Cliente` con `@JoinColumn(name="cliente_id")`, `Pedido → Ubicacion` con `@JoinColumn(name="destino_id")`, `Ruta → Plan/Almacen/Vehiculo` con `plan_id`, `almacen_id`, `vehiculo_id`, y `Ruta ↔ PedidoParte` mediante la entidad intermedia `ruta_parada` porque tiene `orden` y `llegada_estimada`.

## Conexión local con Spring Boot

El perfil Maven `mysql` de [`backend/pom.xml`](../backend/pom.xml) añade `spring-boot-starter-jdbc` y el driver MySQL. `MysqlConnectionConfig` carga un `db.properties` externo, descifra la contraseña con la clave Base64 de 32 bytes en `PAQRAP_DB_KEY` y entrega un `DataSource` a `MysqlDatosService`. El archivo se busca en la raíz al ejecutar desde el repositorio; `PAQRAP_DB_CONFIG` permite indicar una ruta absoluta. El perfil normal sigue leyendo los TXT. La API MySQL lee pedidos, bloqueos, mantenimientos, almacenes y flota con consultas parametrizadas y cierra cada conexión con `try-with-resources`.

[`db.properties.example`](../db.properties.example) documenta `db.host`, `db.port`, `db.name`, `db.user` y `db.password.encrypted`. `db.url` es opcional y reemplaza la URL construida con host, puerto y nombre; permite ajustar parámetros JDBC sin cambiar Java. El backend HTTP escucha en `127.0.0.1:8080` por defecto (`PAQRAP_PORT` puede cambiarlo); el MySQL de Docker escucha en `127.0.0.1:3307`. Son puertos de servicios distintos.

Para el MySQL de Docker, [`start-local-backend-mysql.ps1`](../scripts/start-local-backend-mysql.ps1) genera `db.properties` cifrado y una clave efímera antes de iniciar el backend. `-TestConnection` ejecuta solo la prueba de conexión. La contraseña original sigue en `.env.docker` porque Docker Compose la necesita: esto es **temporal para desarrollo local** y el cifrado del segundo archivo no protege frente a alguien que pueda leer `.env.docker` o el entorno del proceso. Ambos archivos están ignorados por Git. En un entorno real, el proceso debe recibir el secreto y la clave por un mecanismo de secretos externo, con permisos de acceso mínimos; el código de consultas no requiere cambios.

### Preparar una base local nueva

1. En MySQL Workbench, con una cuenta administradora local, ejecuta `paqrap_mysql.sql` sobre una base nueva. No lo ejecutes sobre una base con datos que quieras conservar: el archivo crea tablas sin `DROP`, pero no es una migración.
2. Ejecuta por separado `seed_operacion_diaria.sql`, `seed_cinco_dias.sql` y `seed_colapso.sql` para agregar las tres demostraciones sintéticas. Son idempotentes para sus propios registros. La muestra de septiembre de 2026 del esquema sigue disponible.
3. Crea un usuario **solo de lectura** para la aplicación desde Workbench. Sustituye la contraseña de ejemplo por una que elijas y no la copies al repositorio:

```sql
CREATE USER IF NOT EXISTS 'paqrap_app'@'127.0.0.1' IDENTIFIED BY '<elige-una-contraseña-local>';
GRANT SELECT ON paqrap.* TO 'paqrap_app'@'127.0.0.1';
```

4. En una terminal PowerShell nueva, desde la raíz del repositorio, compila y crea `db.properties` a partir de la plantilla. Escribe la contraseña cuando PowerShell la pida; no la pongas en el historial:

```powershell
mvn -pl backend -am -Pmysql -DskipTests package
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
$env:PAQRAP_DB_KEY = [Convert]::ToBase64String($keyBytes)
$secret = Read-Host 'Contraseña de paqrap_app' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $cipher = $plain | java -cp backend/target/classes com.paqrap.api.DbPasswordCipher encrypt
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    Remove-Variable plain,secret -ErrorAction SilentlyContinue
}
@('db.host=127.0.0.1','db.port=3306','db.name=paqrap','db.user=paqrap_app',"db.password.encrypted=$cipher") | Set-Content db.properties -Encoding ASCII
mvn -pl backend -am -Pmysql '-Dtest=MysqlConnectionIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

La prueba solo ejecuta `SELECT 1` y confirma que se carga el archivo, se descifra la contraseña, MySQL responde y la conexión se cierra. Si falla, Maven indica el problema de configuración o conexión. Conserva `PAQRAP_DB_KEY` en la misma terminal mientras uses ese `db.properties`; al perderla, genera otra clave y vuelve a cifrar la contraseña.

5. Verifica `GET http://127.0.0.1:8080/api/salud`: debe devolver `"fuente":"MYSQL"`. Consulta `GET /api/datos/periodos`; las semillas agregan `202901`, `202902` y `202903`. La GUI local usa el mismo proxy `/api` de Vite.

| Seed | Fecha para ejecutar | Escenario | Contenido |
| --- | --- | --- | --- |
| `seed_operacion_diaria.sql` | 2029-01-15 | Operación día a día | 8 pedidos, 1 bloqueo, 1 mantenimiento. |
| `seed_cinco_dias.sql` | 2029-02-01 | Simulación de 5 días | 15 pedidos distribuidos en 5 días, 2 bloqueos, 2 mantenimientos. |
| `seed_colapso.sql` | 2029-03-01 | Colapso logístico | 10 pedidos lejanos con plazo de 1 hora, 1 bloqueo, 2 mantenimientos. Es un caso de estrés; comprobar el resultado en el simulador, no asumirlo por el nombre. |

Los tres seeds comparten los tres almacenes y la flota de 37 vehículos del esquema base. Son **datos sintéticos**, no parte de los archivos del curso. Las capas del mapa muestran almacenes y bloqueos vigentes a la hora seleccionada; las rutas son planes calculados, no entregas confirmadas.

Comprueba la carga sin modificar datos:

```sql
SELECT DATE_FORMAT(registrado_en, '%Y%m') periodo, COUNT(*) pedidos
FROM pedido WHERE registrado_en >= '2029-01-01' AND registrado_en < '2029-04-01'
GROUP BY periodo ORDER BY periodo;
-- Esperado: 202901=8, 202902=15, 202903=10.
SELECT COUNT(*) almacenes FROM almacen; -- Esperado: 3.
SELECT COUNT(*) vehiculos FROM vehiculo; -- Esperado: 37.
SELECT f.nombre, COUNT(b.id) bloqueos FROM archivo_fuente f
LEFT JOIN bloqueo b ON b.archivo_id=f.id
WHERE f.nombre LIKE 'demo.bloqueos.%' GROUP BY f.nombre;
```

### Límite de la integración actual

La base es la fuente de **entradas y catálogos**, pero **no guarda las ejecuciones ni los resultados**: estos siguen en `ConcurrentMap` y desaparecen al reiniciar el backend. No se insertan entregas o planes ficticios en el seed. Para persistir la salida de cada corrida hacen falta adaptadores transaccionales adicionales, sin duplicar `Pedido`, `Ruta` ni otras clases de dominio:

El perfil MySQL lee los identificadores y tipos de la flota desde `vehiculo`; las especificaciones de capacidad, velocidad y costo siguen viniendo de `TipoVehiculo` en Java. Si se modifican esos valores en `tipo_vehiculo`, el motor todavía no los aplicará automáticamente. Mantener ambos sincronizados hasta implementar la configuración dinámica.

1. Importación transaccional de todos los TXT del curso a `archivo_fuente`, `pedido`, `bloqueo` y `mantenimiento_programado`. Actualmente solo están la muestra original y los tres seeds.
2. `EjecucionRepository`: crear por UUID, actualizar estados y recuperar una corrida tras reinicio; resolver las corridas que queden `EN_CURSO` si el proceso se interrumpe.
3. `PlanRepository` y `ResultadoRepository`: guardar versiones de rutas, partes no asignadas, entregas reales, averías y resúmenes en transacciones idempotentes.

Las operaciones de varios pasos deben usar `@Transactional`. No se requiere `spring.jpa.hibernate.ddl-auto` porque este diseño usa JDBC y el esquema se crea mediante el SQL. `db.properties` cifrado y su clave se mantienen fuera de Git; la clave llega por variable de entorno. La aplicación solo necesita `SELECT` en esta fase.

Las reglas que involucran varias filas siguen en la capa de dominio/transacción: la suma de cantidades de las partes de un pedido no puede exceder el original, una parte no puede aparecer a la vez en `ruta_parada` y `plan_pedido_no_asignado` del mismo plan, y cada tramo de una poligonal debe ser horizontal o vertical. Las restricciones `CHECK` de una sola fila no bastan para expresar esas reglas.

## Inconsistencias que requieren decisión

- `DatosCaso` sitúa los almacenes central y este en `(27,14)` y `(57,27)`; la lista de exigencias v03 consultada antes señala `(25,15)` y `(55,27)`. El seed sigue el **código ejecutable actual** para que la API y la base coincidan. Se debe acordar la fuente oficial y cambiar `DatosCaso`, el seed y las pruebas juntos si prevalece la lista de exigencias.
- `Pedido` documenta una hora de acondicionamiento **por entrega**, pero `partirEn` hace que solo la primera parte pague esa hora. La columna `pedido_parte.paga_acondicionamiento` preserva el valor actual del objeto; el SQL no corrige el algoritmo. Si el requisito es una hora por visita, ajustar `Pedido.parte`, `Evaluador` y pruebas antes de cambiar la carga de partes.
- `Pedido` no tiene una fábrica pública para reconstruir partes con `idOriginal` y el flag desde SQL. Sin esa pequeña API de rehidratación se pueden guardar partes, pero no recuperar exactamente una corrida parcial después de reiniciar.
- La guía de implementación de semana 8 excluía BD en aquella entrega. Esta implementación responde al nuevo alcance solicitado; no implica que el backend ya haya migrado de archivos/memoria a MySQL.
