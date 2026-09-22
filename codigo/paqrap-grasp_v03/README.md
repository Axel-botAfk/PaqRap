# PaqRap — GRASP (segunda iteración / versión final)

Implementación en Java del algoritmo GRASP para el componente planificador de PaqRap.

Esta versión completa las dos fases definidas para GRASP en el ISA:

1. **Fase constructiva aleatorizada** mediante inserciones factibles y una Lista Restringida de Candidatos (LRC).
2. **Fase de búsqueda local** para mejorar el orden de entrega dentro de cada ruta antes de comparar la solución con la mejor solución global.

## Alcance implementado

- Pedidos con entrega regular (36 h) o priorizada (4/8/12/18 h).
- Almacén central con inventario infinito.
- Almacenes intermedios con stock controlado.
- Autos, motos y bicicletas con capacidad, velocidad y costo/km del caso.
- Asignación progresiva de:
  - pedido;
  - almacén;
  - vehículo;
  - posición en una ruta.
- Restricciones de la construcción:
  - stock;
  - capacidad del vehículo;
  - vehículo disponible (ni en avería ni en mantenimiento preventivo vigente);
  - cumplimiento del plazo.
- 1 hora de atención por destinatario (`Parametros.horasAtencionPorDestinatario`, configurable;
  1 h es el valor del caso). **No cuenta dentro del plazo de entrega** (Preguntas y Respuestas,
  pregunta 11): el plazo se valida contra la hora de llegada, antes de sumar esa hora.
- Averías (Preguntas y Respuestas, pregunta 3) y mantenimiento preventivo (pregunta 19): una
  unidad con una `VentanaIndisponibilidad` vigente no puede iniciar ni completar una ruta que
  cruce ese período. `LectorMantenimiento` parsea el archivo oficial `mant.preventivo.m1.m2`
  (formato `aaaammdd:TTNN`) a estas ventanas.
- Costo de ruta calculado como distancia * costo/km del vehículo.
- Priorización por menor holgura y luego menor costo incremental.
- LRC con parámetro `alfa`.
- Selección aleatoria reproducible mediante semilla.
- Registro de pedidos no asignados.
- Varias iteraciones constructivas y conservación de la mejor solución.
- **Búsqueda local intra-ruta por reinserción de pedidos**.

## Averías, mantenimiento y otros parámetros pedidos por la jp (22-sept-2026, actualizado)

La jp pidió considerar como parámetros: hora de entrega adicional, mantenimiento de vehículo,
turnos y averías, recarga de almacenes, y hora de refrigerio de los conductores. Esto es lo que
se implementó y lo que se dejó pendiente, contrastado con las Preguntas y Respuestas oficiales
del caso (`c.1inf54.26-2.preguntas.respuestas`) y con el Enunciado de la Situación Auténtica
(`c.1inf54.26-2.b.situacion.autentica`):

- **Hora de entrega adicional** → implementado. Es el tiempo de acondicionamiento (pregunta 14:
  1 hora por entrega, sin importar la cantidad). Se movió de una constante fija en `Grasp.java`
  a `Parametros.horasAtencionPorDestinatario`, y se corrigió que no debe contar contra el plazo
  (pregunta 11 — ver más abajo).
- **Turnos** → definidos e implementados en `Turno.java`. La Situación Auténtica los precisa:
  "Los cambios de turno se realizan cada 8 horas (07:00, 15:00, 23:00)", es decir 3 turnos
  diarios de 8h. Se usan únicamente para calcular cuándo termina una avería tipo 2 o tipo 3 (ver
  siguiente punto). **Sigue sin implementarse como restricción sobre el vehículo**: la pregunta
  12 de Preguntas y Respuestas dice explícitamente que el cambio de turno es transparente para
  la unidad ("el conductor da el alcance a la unidad de transporte... tiempo despreciable"), así
  que el turno en sí no bloquea al vehículo.
- **Averías** → implementado por completo, incluida la duración oficial de cada tipo.
  `TipoIndisponibilidad` distingue los 3 tipos (pregunta 3) y `CalculadorAveria` calcula la
  ventana exacta a partir del momento en que ocurre la avería:
  - Tipo 1 (menor): `inicio + 2 horas` exactas.
  - Tipo 2 (intermedia): dura hasta el **fin del turno siguiente** al que ocurrió (usa `Turno`).
  - Tipo 3 (mayor): dura **al menos 2 días** y retorna exactamente en el turno de 15:00 a 23:00
    (es decir, a las 15:00 del primer día en que ya se cumplieron los 2 días).
  `VentanaIndisponibilidad` guarda el rango resultante, `Vehiculo` la trae en su lista de
  ventanas, y `Grasp` rechaza cualquier ruta que la cruce. Verificado en `VerificacionAveria`
  (5 casos, incluye los bordes de cruce de turno) y demostrado en `DemoDatasetJP`.
  **Todavía fuera de alcance** (nivel de simulación, no de este algoritmo): el motor que decide
  *cuándo* ocurre una avería durante una simulación de varios días, y el traslado "instantáneo"
  de la unidad y sus pedidos no trasvasados al almacén central que indica la pregunta 3 (incluye
  un trasvase de 30 min) — esto no cambia la duración de la ventana de indisponibilidad, solo la
  ubicación de la unidad durante/después de ella, y este modelo de GRASP no lleva seguimiento de
  posición en tiempo real.
- **Mantenimiento de vehículo** → implementado. `LectorMantenimiento` lee el archivo oficial
  `mant.preventivo.m1.m2` (formato `aaaammdd:TTNN`) y genera las `VentanaIndisponibilidad`
  correspondientes. La duración por tipo de vehículo es **provisional**: el profesor marcó ese
  dato como pendiente ("FALTA poner duración de los mantenimientos") y solo dejó una referencia
  informal (bicicleta = 1 turno, moto = 1 día, auto = 2 días); mientras tanto se aplica como
  mínimo lo que sí está confirmado (Nota 1: la unidad no se programa entre las 00:00 y las 23:59
  del día registrado). Actualizar `LectorMantenimiento.DURACION_HORAS_POR_TIPO` en cuanto el
  profesor confirme el valor definitivo.
- **Hora de refrigerio de los conductores** → **no se implementó, pero ya tiene sustento oficial
  parcial**. La Situación Auténtica sí lo menciona: "se debe respetar una hora para alimentación
  (p.e. almuerzo, cena) dentro del horario de la jornada y al menos una hora antes o una hora
  después de un cambio de turno." Lo que **no** dice ninguno de los dos documentos es si esa hora
  deja al vehículo indisponible (como una avería) o si, igual que en la pregunta 12, otro
  conductor puede tomar el relevo sin afectar a la unidad. Implementarlo como restricción de
  `Vehiculo` sin esa confirmación arriesga inventar una regla — se recomienda confirmar con la jp
  antes de tocar código.
- **Recarga de los almacenes** → **no se implementó dentro de GRASP mismo** (`Grasp.construir` sigue
  sin tocar el stock de entrada), pero sí se resolvió a nivel del bucle de simulación de varios
  días que se agregó después (ver la sección "Simulación multi-día y datos reales" más abajo): la
  pregunta 10 solo define que una unidad puede recargar carga en cualquier almacén con stock, no
  que el almacén se reabastezca solo, así que no hay una regla oficial de cuándo/cuánto se repone
  el stock de un almacén intermedio. A falta de esa regla, `Simulador` reabastece cada almacén
  intermedio a su capacidad máxima (1000) en cada latido — una simplificación documentada en el
  propio `Simulador.java`, no un dato confirmado por el profesor.

## Corrección importante: el plazo de entrega ya no incluye la hora de atención

Versión anterior (incorrecta): `calcularMetricas` sumaba la hora de atención al reloj **antes**
de comparar contra `pedido.getFechaLimite()`, así que una entrega que llegaba justo a tiempo
podía rechazarse por "incumplir el plazo" solo por sumarle esa hora extra.

Versión corregida: el plazo se valida contra la hora de llegada real (antes de la hora de
atención); la hora de atención sigue sumándose al reloj para calcular cuándo el vehículo queda
libre para el siguiente tramo, pero ya no cuenta contra el plazo del pedido que se está
entregando — tal como indica la pregunta 11 de Preguntas y Respuestas.

## Búsqueda local implementada

Después de construir una solución, GRASP recorre cada ruta y explora un vecindario de reinserción:

1. retira temporalmente un pedido de la secuencia;
2. prueba todas las posiciones posibles dentro de la misma ruta;
3. recalcula distancia, costo, duración y horas de entrega;
4. descarta las secuencias que incumplen algún plazo;
5. aplica la mejor reinserción que reduzca el costo;
6. repite el proceso hasta que ya no exista una mejora factible.

La búsqueda local **no cambia el almacén ni el vehículo de la ruta**, por lo que conserva su carga y consumo de stock. Su objetivo en esta iteración es optimizar el orden de entrega de los pedidos ya asignados.

## Decisiones de diseño

1. Java 17 + Maven.
2. Paquetes bajo `com.paqrap`.
3. Una unidad del producto P se considera una unidad de capacidad/paquete.
4. Las ubicaciones se modelan mediante un identificador lógico.
5. Para las pruebas, las distancias se suministran con `MatrizDistancias`.
6. Cada vehículo disponible participa como máximo en una ruta dentro de una construcción.
7. Para iniciar una ruta, el vehículo debe encontrarse en la ubicación del almacén de salida.
8. Un pedido no se divide entre varios vehículos o almacenes en esta implementación.
9. Una ruta parte de un almacén y termina en el último destinatario; no se modela el retorno al almacén.
10. La hora de entrega usada para verificar el plazo es la hora de llegada al cliente (viaje),
    **sin** la hora de atención (pregunta 11 de Preguntas y Respuestas). La hora de atención sí
    se suma al reloj para el siguiente tramo de la ruta.
11. Para elegir la mejor solución se minimiza primero la cantidad de pedidos no asignados y, en empate, el costo total.
12. La LRC usa `alfa=0` como comportamiento totalmente voraz y `alfa=1` como lista completamente abierta.
13. La búsqueda local utiliza un vecindario de reinserción dentro de la misma ruta y solo acepta mejoras factibles de costo.
14. Una unidad con una `VentanaIndisponibilidad` (avería o mantenimiento preventivo) vigente en
    cualquier instante del tramo que dura la ruta no puede usarse para esa ruta.

## Fuera del alcance de esta implementación de GRASP

Estas funciones pertenecen a otras partes del sistema o a la replanificación y no forman parte de esta versión del algoritmo:

- bloqueos de calles;
- replanificación ante incidencias (decidir *cuándo* ocurre una avería y reubicar la unidad/sus pedidos al almacén central);
- el cambio de turno en sí (no bloquea al vehículo según la pregunta 12 del caso; sí se usa `Turno` para calcular cuánto dura una avería tipo 2/3 — ver más arriba) y la hora de refrigerio del conductor (tiene sustento en la Situación Auténtica, pero no está claro si bloquea al vehículo — pendiente de confirmar con la jp);
- escenario de colapso (el ritmo de llegada nunca se compara contra un límite de capacidad para
  detectar un colapso explícito, a diferencia del simulador del proyecto de búsqueda tabú del
  equipo).

Averías y mantenimiento preventivo **sí están en el alcance** de esta versión (ver la sección
"Averías, mantenimiento y otros parámetros pedidos por la jp" más arriba): `Grasp` ya respeta las
`VentanaIndisponibilidad` de cada vehículo al construir una ruta. La recarga de almacenes y la
simulación de varios días (antes fuera de alcance) también se resolvieron — ver la siguiente
sección.

## Simulación multi-día y datos reales

Hasta esta versión, GRASP solo se probaba con una única planificación estática: una lista fija de
pedidos y una sola llamada a `Grasp.construir`. Eso no alcanzaba para usar los archivos reales que
entregó el profesor (`ventas.202609.txt`, `ventas.202610.txt`, `mant.preventivo.09.10.txt`, los
mismos que usa el proyecto de búsqueda tabú del equipo), que cubren varios días y miles de
pedidos. Se agregó lo que faltaba:

- **`DistanciaManhattan`** (paquete `planificador`): implementación de `CalculadorDistancia` sobre
  una cuadrícula (x,y), para los `posX,posY` reales del archivo de ventas. No reemplaza a
  `MatrizDistancias` (sigue siendo válida para los demos/verificaciones con identificadores
  lógicos arbitrarios); `Ubicacion` sigue siendo, a propósito, un id opaco (ver decisión de diseño
  #4) — `DistanciaManhattan` solo interpreta ese id como coordenada cuando ella misma lo construyó.
- **`datos.LectorVentas`**: lee `ventas.aaaamm.txt` (`##d##h##m:posX,posY,cliente,qq,hl`) y arma
  `Pedido`. El plazo `hl` del archivo (4, 8, 12, 18 o 36) se mapea 1 a 1 al `TipoEntrega` ya
  existente: los valores reales del caso coinciden exactamente con los de ese enum.
- **`datos.DatosCaso`**: coordenadas reales de los 3 almacenes (central y los dos intermedios) y
  la flota real completa (10 autos + 15 motos + 12 bicicletas = 37 unidades, códigos `TA01..TA10` /
  `TM01..TM15` / `TB01..TB12`, el mismo formato que ya lee `LectorMantenimiento`).
- **`simulacion.Simulador`** (paquete nuevo): corre GRASP repetidamente contra un horizonte de
  varios días. En cada "latido" (instante de replanificación, cada hora por defecto) reúne los
  pedidos pendientes y las unidades libres en ese momento, llama una vez a `Grasp.construir` y
  aplica el plan al estado para el siguiente latido, reutilizando la misma flota entre
  planificaciones. Sus decisiones de diseño (documentadas en la clase) son:
  1. cadencia de replanificación configurable en vez de continua, porque `Ruta` no expone la hora
     de llegada de cada parada, solo la duración total: una unidad se libera recién cuando termina
     toda su ruta;
  2. **entre latidos, una unidad vuelve a su almacén de origen** para poder recibir una ruta nueva
     (`Grasp.construir` exige que la unidad esté físicamente en un almacén para iniciar una ruta —
     ver decisión de diseño #7); el costo/duración de esa ruta sigue sin incluir el trayecto de
     vuelta, solo se reinicia la posición registrada para el siguiente latido. Sin este ajuste la
     flota se "varaba" en la última parada de su primera ruta y quedaba inutilizable el resto de la
     simulación — es justo lo que se observó al probar el simulador por primera vez;
  3. los almacenes intermedios se reabastecen a su capacidad máxima en cada latido (ver sección
     anterior);
  4. bloqueos de calles quedan fuera de esta simulación, igual que en el resto de GRASP;
  5. un pedido sin inserción factible antes de su fecha límite se marca vencido y no se reintenta.
- **`simulacion.ResumenSimulacion`**: resultado agregado (recibidos, entregados, vencidos,
  pendientes al cierre, distancia/costo total, y el desglose por día). A diferencia del resumen
  del proyecto de búsqueda tabú del equipo, no incluye métricas por pedido individual (hora exacta
  de entrega, etc.): el modelo de `Ruta` de este proyecto no expone esa granularidad.
- **`demo.DemoDatosReales`**: el demo que corre todo lo anterior contra los archivos reales y
  muestra el resumen y la evolución diaria (ver "Ejecución" más abajo).

## Ejecución con Maven

```bash
mvn test
mvn package
java -cp target/classes com.paqrap.demo.DemoGrasp
```

## Verificación sin JUnit

Si solo se dispone de JDK 17, se puede compilar el código principal y ejecutar la verificación incluida:

```bash
mkdir -p build
javac -encoding UTF-8 -d build $(find src/main/java -name "*.java")
java -cp build com.paqrap.demo.VerificacionGrasp
```

La verificación cubre:

1. construcción y asignación válida;
2. restricción de capacidad;
3. uso del almacén central como respaldo;
4. rechazo de rutas que incumplen plazo (por el viaje, no por la hora de atención);
5. mejora del orden de una ruta mediante búsqueda local;
6. exclusión de una unidad con avería o mantenimiento vigente.

`VerificacionMantenimiento` (mismo mecanismo, sin JUnit) verifica además que `LectorMantenimiento`
interprete correctamente el archivo oficial `mant.preventivo.m1.m2`:

```bash
java -cp build com.paqrap.demo.VerificacionMantenimiento
```

`VerificacionAveria` verifica que `CalculadorAveria` calcule la duración de cada tipo de avería
exactamente como la describe la pregunta 3, usando los turnos oficiales de la Situación Auténtica
(07:00, 15:00, 23:00):

```bash
java -cp build com.paqrap.demo.VerificacionAveria
```

`DemoDatasetJP` muestra, con salida explicada paso a paso (útil para la exposición), los 5
escenarios de los parámetros pedidos por la jp: avería tipo 1, avería tipo 3, mantenimiento
preventivo, la corrección del plazo (pregunta 11) y un dataset combinado con varios pedidos y
vehículos, algunos bloqueados:

```bash
java -cp build com.paqrap.demo.DemoDatasetJP
```

`DemoDatosReales` corre la simulación multi-día contra los archivos reales del profesor. Por
defecto simula 3 días de setiembre de 2026 leyendo de `datos/reales/`, con un latido de
replanificación cada 60 minutos:

```bash
java -cp build com.paqrap.demo.DemoDatosReales
# o con los parametros explicitos: [dias] [carpeta] [maxIteracionesGrasp] [alfa] [minutosEntreLatidos]
java -cp build com.paqrap.demo.DemoDatosReales 7 datos/reales 20 0.30 60
```

## Uso principal

```java
Grasp grasp = new Grasp(calculadorDistancia);

Solucion solucion = grasp.construir(
    horaPlanificacion,
    pedidos,
    almacenes,
    vehiculos,
    new Parametros(20, 0.30, 12345L)
);
```

El método `planificar(...)` ejecuta, para cada iteración:

```text
CONSTRUIR_SOLUCION
        ↓
BUSQUEDA_LOCAL
        ↓
COMPARAR_CON_MEJOR_GLOBAL
```

## Archivos principales

- `Grasp.java`: orquesta construcción, búsqueda local y selección de la mejor solución.
- `Ruta.java`: representa la ruta y permite reemplazar el orden de pedidos validado por la búsqueda local.
- `EstadoOperacion.java`: agrupa la información de entrada del planificador.
- `Parametros.java`: contiene iteraciones, alfa, semilla y la hora de atención por destinatario.
- `Vehiculo.java`: incluye sus `VentanaIndisponibilidad` (averías/mantenimiento).
- `VentanaIndisponibilidad.java` / `TipoIndisponibilidad.java`: modelan un rango de tiempo en que una unidad no puede programarse, y el motivo.
- `LectorMantenimiento.java`: parsea el archivo oficial `mant.preventivo.m1.m2` a `VentanaIndisponibilidad`.
- `MatrizDistancias.java`: implementación de distancias para pruebas y demostración.
- `DistanciaManhattan.java`: implementación de distancias sobre coordenadas (x,y), para datos reales.
- `datos/LectorVentas.java` / `datos/DatosCaso.java`: lectura del archivo real de ventas y datos fijos (almacenes/flota) del caso.
- `simulacion/Simulador.java` / `simulacion/ResumenSimulacion.java`: simulación multi-día reutilizando GRASP y la flota entre planificaciones.
- `GraspTest.java`: pruebas unitarias JUnit.
- `VerificacionGrasp.java` / `VerificacionMantenimiento.java`: pruebas ejecutables sin dependencias de test.
- `DemoGrasp.java` / `DemoGraspDataset.java`: ejemplos de generación de rutas.
- `DemoDatosReales.java`: simulación multi-día contra los datos reales del profesor.
