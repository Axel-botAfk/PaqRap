# PaqRap — Planificador (GRASP + Búsqueda Tabú)

Implementación en Java de los dos metaheurísticos del componente planificador: GRASP
(fase constructiva) y búsqueda tabú (fase de mejora), sobre un mismo modelo de dominio y
una misma función objetivo.

## Alcance implementado

### GRASP — construcción

- Pedidos con entrega regular (36 h) o priorizada (4/8/12/18 h).
- Almacén central con inventario infinito.
- Almacenes intermedios con stock máximo de 1000 unidades.
- Autos, motos y bicicletas con capacidad, velocidad y costo/km del caso.
- Asignación progresiva de:
  - pedido;
  - almacén;
  - vehículo;
  - posición en una ruta.
- Restricciones:
  - stock;
  - capacidad del vehículo;
  - vehículo disponible;
  - cumplimiento del plazo.
- 1 hora de atención por destinatario.
- Costo de ruta por distancia * costo/km del vehículo.
- Priorización por menor holgura y luego menor costo incremental.
- LRC (lista restringida de candidatos) con parámetro alfa.
- Selección aleatoria reproducible mediante semilla.
- Registro de pedidos no asignados.
- Varias iteraciones constructivas y conservación de la mejor solución.

### Búsqueda tabú — mejora

Parte de la solución de GRASP y explora una muestra del vecindario por iteración. Los
movimientos cubren los dos niveles de decisión del caso:

- **Rutas** (qué se entrega y en qué orden):
  - `TRASLADAR`: cambiar un pedido de ruta o de posición dentro de su ruta;
  - `INTERCAMBIAR`: intercambiar dos pedidos entre rutas distintas;
  - `INVERTIR`: invertir un tramo de la secuencia de entregas (2-opt).
- **Asignaciones** (qué recursos atienden cada ruta):
  - `CAMBIAR_VEHICULO`: reasignar la unidad conservando el almacén de salida;
  - `CAMBIAR_ALMACEN`: reasignar la ruta a otro almacén junto con una unidad disponible allí;
  - `ASIGNAR_PENDIENTE`: incorporar al plan un pedido que quedó sin asignar, en una ruta
    existente o estrenando una ruta.

Mecanismos de la metaheurística:

- lista tabú por atributo del movimiento, con tenencia configurable;
- criterio de aspiración (se admite un movimiento tabú si supera a la mejor solución conocida);
- aceptación del mejor candidato aunque empeore el valor actual, para salir de óptimos locales;
- intensificación: al estancarse se vuelve a la mejor solución conocida y se libera la lista tabú;
- límites por iteraciones, por reinicios y por tiempo;
- evaluación por aplicar/medir/deshacer, sin copiar la solución completa en cada vecino;
- reproducibilidad por semilla.

Función objetivo compartida (`Evaluador`): costo de operación más una penalidad por pedido
sin atender. Un plan es infactible —y se descarta— si viola capacidad, plazo, disponibilidad
o ubicación de la unidad, unicidad del vehículo por ruta, o el stock de un almacén intermedio.

## Fuente del proyecto

Las siguientes decisiones vienen del caso/ISA:

- Java.
- GRASP como uno de los dos algoritmos metaheurísticos.
- Fase de construcción + LRC + selección aleatoria + búsqueda local posterior.
- Producto único P.
- Plazos 36/4/8/12/18 h.
- Central con inventario infinito.
- Dos almacenes intermedios, máximo 1000 unidades cada uno.
- Vehículos:
  - auto: 24 paquetes, 40 km/h, S/ 8/km;
  - moto: 8 paquetes, 25 km/h, S/ 6/km;
  - bicicleta: 4 paquetes, 12 km/h, S/ 3/km.
- 1 hora de atención por destinatario.
- Tiempo de carga despreciable.
- Priorización por holgura.
- Asignación de almacén considerando stock, cercanía, plazo, costo y flota.

## Decisiones de diseño de esta primera iteración

1. Java 17 + Maven.
2. Paquetes bajo `com.paqrap`.
3. Una unidad del producto P se considera una unidad de capacidad/paquete.
4. Las ubicaciones se modelan por un identificador lógico.
5. Para las pruebas, las distancias se suministran con `MatrizDistancias`.
6. Cada vehículo disponible participa como máximo en una ruta dentro de una construcción.
7. Para iniciar una ruta, el vehículo debe encontrarse en la ubicación del almacén de salida; todavía no se modela su reubicación.
8. Un pedido no se divide entre varios vehículos o almacenes en esta primera iteración.
9. Una ruta parte de un almacén y termina en el último destinatario; todavía no se modela el retorno.
10. La hora de entrega utilizada para verificar el plazo incluye viaje + 1 hora de atención.
11. Para elegir la mejor construcción se minimiza primero la cantidad de pedidos no asignados y, en empate, el costo total.
12. La LRC usa `alfa=0` como totalmente voraz y `alfa=1` como completamente abierta.
13. GRASP y la búsqueda tabú comparten `Evaluador`: si ambas fases midieran distinto, la
    mejora podría "optimizar" un plan que la construcción considera inviable.
14. `CAMBIAR_ALMACEN` mueve el almacén y la unidad a la vez: por la decisión 7, cambiar el
    almacén sin cambiar la unidad produciría siempre un vecino infactible.
15. La penalidad por pedido no asignado (10 000 por defecto) reproduce la política de GRASP
    —primero cubrir pedidos, después abaratar— dentro de un único valor a minimizar.
16. El vecindario granular por radio queda desactivado por defecto, porque la matriz de
    distancias del modelo actual puede estar registrada de forma parcial.

Estas decisiones deben revisarse cuando el equipo defina el grafo real, turnos y reglas de operación más detalladas.

## No implementado todavía

De forma intencional no se implementaron:

- bloqueos;
- replanificación;
- falla durante una ruta;
- turnos y hora de alimentación;
- recarga automática a las 23:59:59;
- simulación 5D;
- escenario de colapso.

## Ejecución

```bash
mvn test
mvn package
java -cp target/classes com.paqrap.demo.DemoGrasp
java -cp target/classes com.paqrap.demo.DemoTabu
java -cp target/classes com.paqrap.demo.VerificacionGrasp
java -cp target/classes com.paqrap.demo.VerificacionTabu
```

Sin Maven:

```bash
javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')
java -cp out com.paqrap.demo.DemoTabu
```

Uso principal:

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

Con la fase de mejora:

```java
BusquedaTabu tabu = new BusquedaTabu(calculadorDistancia);

Parametros parametros = Parametros.constructor(20, 0.30, 12345L)
    .iteracionesTabu(300)
    .tenenciaTabu(8)
    .tamanoMuestraVecindario(60)
    .iteracionesSinMejora(40)
    .limiteMilisegundos(3_000L)
    .construir();

// GRASP + tabú en una sola llamada:
Solucion solucion = tabu.planificar(estado, parametros);

// O bien mejorar un plan ya construido (no modifica el recibido):
Solucion mejorada = tabu.mejorar(solucionGrasp, estado, parametros);
```

Luego:

```java
for (Ruta ruta : solucion.getRutas()) {
    System.out.println(ruta.getAlmacen());
    System.out.println(ruta.getVehiculo());
    System.out.println(ruta.getPedidos());
}
```

## Evidencia para Semana 4

El commit debería incluir al menos:

- clases del modelo;
- `Planificador`;
- `EstadoOperacion`;
- `Parametros`;
- `CalculadorDistancia`;
- `MatrizDistancias`;
- `Evaluador` y `MetricasRuta`;
- `Grasp`;
- `tabu/BusquedaTabu`, `tabu/Movimiento`, `tabu/TipoMovimiento`, `tabu/ListaTabu`,
  `tabu/AplicadorMovimiento`;
- pruebas `GraspTest`;
- `DemoGrasp`, `DemoTabu`, `VerificacionGrasp`, `VerificacionTabu`;
- actualización del ISA con:
  - clases realmente creadas;
  - correspondencia pseudocódigo -> métodos;
  - ejecución de ejemplo;
  - prueba mínima;
  - commit;
  - integrantes de la pareja y aportes.
