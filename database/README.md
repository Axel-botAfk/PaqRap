# Base de datos MySQL de PaqRap

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

## Conexión Spring Boot y trabajo pendiente en Java

El perfil Maven `mysql` de [`backend/pom.xml`](../backend/pom.xml) añade `spring-boot-starter-jdbc` y el driver MySQL. [`application-mysql.properties`](../backend/src/main/resources/application-mysql.properties) define la conexión con variables de entorno. Desde la raíz del repositorio, después de crear la base y un usuario MySQL con permisos para `paqrap`:

```powershell
$env:PAQRAP_DB_USER = 'paqrap_app'
$env:PAQRAP_DB_PASSWORD = '<contraseña-local>'
mvn -pl backend -am -Pmysql -DskipTests package
java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

El perfil establece la conexión, pero **la API aún lee archivos y guarda ejecuciones en `ConcurrentMap`**. Para que lea y escriba en MySQL se necesitan adaptadores JDBC en `backend`, sin duplicar `Pedido`, `Ruta` ni otras clases de dominio:

1. `ArchivoFuenteRepository`, `PedidoRepository`, `BloqueoRepository`, `MantenimientoRepository` y `CatalogoRepository`: carga transaccional de TXT y reconstrucción de `DatosService.DatosEntrada` desde SQL. Resolver primero la identidad del pedido por `(archivo_id, codigo)` cuando se consulten varios meses; el código solo no es único.
2. `EjecucionRepository`: crear por UUID, actualizar estados y snapshots de flota/inventario y recuperar una corrida con `GET /api/ejecuciones/{id}`. Al iniciar después de un reinicio, decidir qué hacer con corridas que quedaron `EN_CURSO`.
3. `PlanRepository`: insertar un `plan` por replanificación, sus `ruta`, `ruta_parada` y partes no asignadas en una sola transacción; conservar `orden_plan` y `orden`.
4. `ResultadoRepository`: registrar `Entrega` real, `Averia`, mediciones, vencimientos y resumen al cerrar la corrida. Mantener la escritura idempotente por `(pedido_parte_id)` para no duplicar entregas al reintentar.
5. Integrar esos repositorios en `DatosService` y `EjecucionesService` detrás de un perfil o interfaz, manteniendo el flujo asíncrono y las publicaciones WebSocket. Crear en BD el mismo UUID antes de encolar la tarea y confirmar la transacción antes de publicar cada estado.

Las operaciones de varios pasos deben usar `@Transactional`. No se requiere `spring.jpa.hibernate.ddl-auto` porque este diseño usa JDBC y el esquema se crea mediante el SQL. Los secretos permanecen en variables de entorno, nunca en el repositorio.

Las reglas que involucran varias filas siguen en la capa de dominio/transacción: la suma de cantidades de las partes de un pedido no puede exceder el original, una parte no puede aparecer a la vez en `ruta_parada` y `plan_pedido_no_asignado` del mismo plan, y cada tramo de una poligonal debe ser horizontal o vertical. Las restricciones `CHECK` de una sola fila no bastan para expresar esas reglas.

## Inconsistencias que requieren decisión

- `DatosCaso` sitúa los almacenes central y este en `(27,14)` y `(57,27)`; la lista de exigencias v03 consultada antes señala `(25,15)` y `(55,27)`. El seed sigue el **código ejecutable actual** para que la API y la base coincidan. Se debe acordar la fuente oficial y cambiar `DatosCaso`, el seed y las pruebas juntos si prevalece la lista de exigencias.
- `Pedido` documenta una hora de acondicionamiento **por entrega**, pero `partirEn` hace que solo la primera parte pague esa hora. La columna `pedido_parte.paga_acondicionamiento` preserva el valor actual del objeto; el SQL no corrige el algoritmo. Si el requisito es una hora por visita, ajustar `Pedido.parte`, `Evaluador` y pruebas antes de cambiar la carga de partes.
- `Pedido` no tiene una fábrica pública para reconstruir partes con `idOriginal` y el flag desde SQL. Sin esa pequeña API de rehidratación se pueden guardar partes, pero no recuperar exactamente una corrida parcial después de reiniciar.
- La guía de implementación de semana 8 excluía BD en aquella entrega. Esta implementación responde al nuevo alcance solicitado; no implica que el backend ya haya migrado de archivos/memoria a MySQL.
