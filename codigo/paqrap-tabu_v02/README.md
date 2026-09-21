# PaqRap — Planificador (GRASP y Búsqueda Tabú)

Implementación en Java de los dos metaheurísticos del componente planificador, sobre un mismo
modelo de dominio y una misma función objetivo.

Los dos son **independientes**, que es lo que permite compararlos:

| Algoritmo | Punto de partida | Exploración |
|---|---|---|
| GRASP | construcción golosa aleatorizada con LRC, multiarranque | la aleatoriedad de la LRC sobre varias construcciones |
| Búsqueda Tabú | `ConstructorVecinoMasCercano`, determinista | movimientos sobre el vecindario con memoria de corto plazo |

La búsqueda tabú **no** arranca del resultado de GRASP. Si lo hiciera, la comparación no
mediría dos algoritmos sino uno y su fase de mejora, y el segundo ganaría por construcción.
El constructivo inicial es inyectable, así que la variante GRASP + tabú sigue disponible como
tercer punto de comparación para el informe:

```java
new BusquedaTabu(evaluador, new Grasp(evaluador));
```

## Alcance implementado

### Datos del caso ya incorporados

- Ciudad como retícula de 70 km (eje X) por 50 km (eje Y), nodos cada kilómetro, origen en
  (0,0) abajo a la izquierda, calles de doble sentido y sin diagonales.
- Distancias calculadas sobre la retícula, sin tablas cargadas a mano: `DistanciaManhattan`
  con la ciudad despejada y `EnrutadorBloqueos` cuando hay tramos cerrados.
- Bloqueos de calles con vigencia horaria, leídos del archivo mensual `aaaamm.bloqueadas`
  (`LectorBloqueos`): un nodo cerrado no se atraviesa y el recorrido lo rodea; si el destino
  queda aislado, el pedido se reporta como inalcanzable.
- Almacenes en sus posiciones reales: central (27,14), intermedio Nor-Oeste (12,38) e
  intermedio Este (57,27).
- Flota del caso: 10 autos, 15 motos y 12 bicicletas, con códigos `TTNN` (`TA01`, `TM03`,
  `TB10`) y salida inicial desde el almacén central.
- Plazo del pedido como número de horas, tal como llega en el archivo de ventas (campo `hl`).
- Recarga de los almacenes intermedios cada 24 horas a las 23:59:59 (`Inventario`).
- Capacidad, velocidad y costo por kilómetro modificables en caliente y por tipo de unidad
  (`ConfiguracionFlota`).

### GRASP — construcción

- Pedidos con entrega regular (36 h) o priorizada (4/8/12/18 h).
- Almacén central con inventario infinito.
- Almacenes intermedios con stock máximo de 1000 unidades.
- Autos, motos y bicicletas con capacidad, velocidad y costo/km del caso.
- Asignación progresiva de:
  - pedido;
  - almacén de carga;
  - unidad de transporte;
  - posición dentro de un viaje.
- Viajes encadenados: una unidad puede hacer varios a lo largo del horizonte, recargando en
  cualquier almacén que tenga producto y trasladándose hasta él si hace falta.
- Restricciones:
  - stock del periodo de reposición vigente;
  - capacidad del vehículo;
  - vehículo disponible;
  - cumplimiento del plazo.
- 1 hora de acondicionamiento por entrega, fuera del plazo comprometido.
- Costo de ruta por distancia * costo/km del vehículo.
- Priorización por plazo más apretado y, dentro de un mismo pedido, menor costo incremental
  sobre el programa completo de la unidad.
- LRC (lista restringida de candidatos) por valor, con parámetro alfa.
- Selección aleatoria reproducible mediante semilla.
- Registro de pedidos no asignados.
- Varias iteraciones constructivas y conservación de la mejor solución.

### Vecino más cercano — construcción de la tabú

Recorre las unidades de la más barata por kilómetro a la más cara y con cada una arma viajes
hasta que no pueda más: carga en el almacén con producto más cercano, y desde donde esté mira
los `k` pedidos pendientes más cercanos que quepan, eligiendo de esos el de plazo más apretado.
El filtro por cercanía es lo que la hace barata; el desempate por urgencia evita que un vecino
más cercano puro deje vencer los pedidos priorizados. Con `k=1` queda el vecino más cercano
clásico. Es determinista: no usa la semilla.

### Búsqueda tabú — mejora

Parte de esa solución y explora una muestra del vecindario por iteración. Los movimientos
cubren los dos niveles de decisión del caso:

- **Rutas** (qué se entrega y en qué orden):
  - `TRASLADAR`: cambiar un pedido de ruta o de posición dentro de su ruta;
  - `INTERCAMBIAR`: intercambiar dos pedidos entre rutas distintas;
  - `INVERTIR`: invertir un tramo de la secuencia de entregas (2-opt).
- **Asignaciones** (qué recursos atienden cada ruta):
  - `CAMBIAR_VEHICULO`: que otra unidad se haga cargo del viaje;
  - `CAMBIAR_ALMACEN`: cambiar el almacén donde el viaje carga;
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
sin atender. Un plan es infactible —y se descarta— si viola la capacidad de un viaje, un plazo,
la disponibilidad de una unidad, o el stock de un almacén en el periodo en que carga.

El costo de un viaje no se mide aislado: el `Evaluador` encadena los viajes de cada unidad y
cobra también los traslados sin carga —hasta el primer almacén y entre el último cliente y el
almacén siguiente—, porque la unidad realmente los recorre.

## Operación simulada

`simulacion/Simulador` resuelve los tres escenarios del caso con la misma mecánica: el reloj
avanza a saltos fijos —media hora por defecto— y en cada salto se replanifica con los pedidos
que ya llegaron y las unidades donde hayan quedado.

- El plan no se ejecuta completo, solo hasta donde alcanza el reloj. Una unidad avanza por su
  programa mientras haya salido hacia el punto siguiente antes del corte, de modo que un tramo
  ya iniciado se completa aunque termine después; lo que no había empezado vuelve a decidirse.
- El inventario de los almacenes intermedios se arrastra entre saltos y se recarga a las
  23:59:59, así que el planificador ve el stock que realmente queda.
- Los pedidos entran cuando el reloj alcanza su fecha de llegada, igual que en la operación.
- **Colapso logístico**: el planificador nunca acepta una entrega fuera de plazo, de modo que
  un pedido que no alcanza a ser atendido se queda esperando. Cuando su fecha límite pasa sin
  entregarse, la operación colapsó.

Los archivos de ventas están en `datos/`, en el formato del caso, y se regeneran con
`GenerarDatosDePrueba` (semilla fija, así que las corridas son reproducibles):

- `ventas202609`: 120 pedidos por día durante 5 días, para el escenario 5D.
- `ventas202610`: 60 pedidos el primer día y 8 más cada día, para el escenario de colapso.

### Techo de la flota y calibración del colapso

`DemoCapacidad` corre la operación con demanda constante a varios niveles y mira si la cola
crece. Con la flota del caso —37 unidades— y pedidos de 1 a 12 unidades de producto, el techo
está alrededor de **200 pedidos por día**: a ese nivel aparecen los primeros vencimientos y de
250 en adelante la cola ya no se recupera.

Ese techo no lo pone el planificador sino la operación. Cada entrega consume una hora de
acondicionamiento más el viaje, y con un pedido promedio de 6,5 unidades solo los autos
(capacidad 24) agrupan varias entregas en un viaje: las motos llevan una y las bicicletas ni
siquiera pueden con dos tercios de los pedidos. Multiplicado por 37 unidades y 24 horas, no da
para mucho más. Dos intentos de subirlo **no funcionaron** y quedaron medidos:

- **Llenar primero las unidades de mayor capacidad** empeora el resultado: a 200 pedidos por día
  pasa de 1 vencido a 11. Como cada unidad se llena hasta que no puede más, partir por los autos
  concentra el trabajo en diez unidades y deja ociosas a las otras veintisiete. Queda como
  parámetro de `ConstructorVecinoMasCercano`, desactivado.
- **Cuadruplicar el presupuesto de tiempo de la tabú** (400 ms → 1500 ms por iteración) no cambia
  nada: lo que corta la búsqueda son las 120 iteraciones configuradas, no el reloj.

Por eso la rampa del escenario de colapso está calibrada contra ese techo y no al revés. Con
+8 pedidos por día la operación **aguanta 29 días** antes de quebrarse, con la cola subiendo de
forma visible durante la última semana. Pedirle que dure más exige cambiar la operación —más
flota, o pedidos más chicos—, no afinar el planificador.

### Costo de la construcción

Con cientos de pedidos en cola, generar candidatos para todos ellos en cada inserción vuelve
la construcción cuadrática. Se acotó de dos formas, ambas configurables por parámetros:

- solo compiten los pedidos que comparten el plazo más apretado, que son los únicos que la
  lista restringida puede elegir de todas maneras;
- por cada pedido se prueban los `viajesCandidatosPorPedido` viajes más cercanos al destino y
  las `unidadesCandidatasPorPedido` unidades más cercanas, desempatando por costo por kilómetro.

El recorte cuesta calidad —en el dataset mediano, alrededor de un 8% de costo— y a cambio hace
viable el escenario de colapso. Subir ambos valores devuelve la calidad en instancias chicas.

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

## Decisiones de diseño vigentes

1. Java 17 + Maven.
2. Paquetes bajo `com.paqrap`.
3. Una unidad del producto P se considera una unidad de capacidad/paquete.
4. Las ubicaciones son nodos `(x, y)` de la retícula.
5. Las distancias se calculan sobre la retícula y dependen del instante de salida, porque los
   bloqueos tienen vigencia horaria.
5.1. Una unidad nunca se planifica a través de una esquina que estará cerrada en el momento en
   que pasaría por ella, aunque al salir estuviera abierta. Por eso la consulta de distancia
   recibe también la velocidad: es lo que ubica cada esquina en el tiempo. El recorrido no
   espera a que una calle reabra, así que en el peor caso informa un camino válido más largo
   que el óptimo; nunca uno que atraviese un tramo cerrado.
5.2. Un nodo bloqueado no se puede atravesar. El origen sí se admite aunque esté cerrado,
   porque una unidad que ya se encuentra ahí tiene que poder salir. **Decisión del equipo**: el
   caso no dice qué ocurre si un cierre cae sobre la posición de una unidad.
6. Una ruta representa **un viaje**: llegar a un almacén, cargar y hacer una secuencia de
   entregas. Una unidad encadena varios viajes; el orden en que la solución los guarda es el
   orden en que la unidad los ejecuta.
7. Una unidad puede cargar en cualquier almacén que tenga producto, trasladándose hasta él si
   hace falta. Ese traslado sin carga se paga en distancia, tiempo y costo.
8. Un pedido no se divide entre varias unidades o almacenes.
9. El horizonte termina en la última entrega: no se modela el retorno final a un almacén.
10. El plazo se verifica contra la hora de **llegada** al cliente. La hora de acondicionamiento
    ocupa a la unidad pero no cuenta contra la fecha límite.
11. Para elegir la mejor construcción se minimiza primero la cantidad de pedidos no asignados y, en empate, el costo total.
12. La LRC usa `alfa=0` como totalmente voraz y `alfa=1` como completamente abierta.
13. GRASP y la búsqueda tabú comparten `Evaluador`: miden con las mismas reglas, que es
    condición para que la comparación entre ambos signifique algo.
13.1. La búsqueda tabú parte de su propio constructivo y no de GRASP, para que los dos
    algoritmos de la experimentación numérica sean independientes.
14. `CAMBIAR_ALMACEN` cambia solo el almacén de carga. La unidad se traslada hasta él, de modo
    que ya no hace falta moverlos juntos.
15. La penalidad por pedido no asignado (10 000 por defecto) reproduce la política de GRASP
    —primero cubrir pedidos, después abaratar— dentro de un único valor a minimizar.
16. El vecindario granular por radio queda desactivado por defecto; sobre la retícula ya puede
    activarse con un radio en kilómetros sin riesgo de descartar vecinos por falta de datos.

Estas decisiones deben revisarse cuando el equipo defina el grafo real, turnos y reglas de operación más detalladas.

## No implementado todavía

De forma intencional no se implementaron:

- entregas parciales de un mismo pedido;
- turnos de 8 horas y la hora de alimentación;
- averías (tipos 1, 2 y 3) y trasvase de paquetes;
- mantenimiento preventivo (archivo `mant.preventivo.m1.m2`);
- lectura del archivo de mantenimiento;
- reacción a una avería o a un bloqueo que aparece con la unidad ya en camino: la
  replanificación periódica lo recoge en el salto siguiente, no en el instante del hecho.

El horizonte de planificación no está acotado por turnos: una unidad encadena viajes mientras
haya pedidos y los plazos lo permitan. Los turnos de 8 horas y la hora de alimentación son el
siguiente límite a incorporar.

## Ejecución

```bash
mvn test
mvn package
java -cp target/classes com.paqrap.demo.DemoGrasp
java -cp target/classes com.paqrap.demo.DemoTabu
java -cp target/classes com.paqrap.demo.DemoBloqueos
java -cp target/classes com.paqrap.demo.VerificacionGrasp
java -cp target/classes com.paqrap.demo.VerificacionTabu
java -cp target/classes com.paqrap.demo.VerificacionBloqueos
java -cp target/classes com.paqrap.demo.VerificacionSimulacion

java -cp target/classes com.paqrap.demo.GenerarDatosDePrueba
java -cp target/classes com.paqrap.demo.DemoSimulacion5Dias
java -cp target/classes com.paqrap.demo.DemoColapso
java -cp target/classes com.paqrap.demo.DemoCapacidad
```

`DemoCapacidad` acepta el presupuesto de la tabú y los niveles de demanda a probar:
`DemoCapacidad 1500 200,250,300`.

`DemoBloqueos` lee `datos/202609.bloqueadas`, un archivo de ejemplo con el formato del caso;
se puede pasar otra ruta como argumento.

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
- `Ciudad`, `Ubicacion`, `Bloqueo`;
- `CalculadorDistancia`, `DistanciaManhattan`, `MapaBloqueos`, `EnrutadorBloqueos`;
- `datos/LectorBloqueos`, `datos/LectorVentas`, `datos/GeneradorVentas`;
- `simulacion/Simulador`, `simulacion/Entrega`, `simulacion/ResumenSimulacion`;
- `Inventario`;
- `ConfiguracionFlota`, `EspecificacionVehiculo`;
- `datos/DatosCaso`;
- `Evaluador`, `MetricasRuta`, `ResultadoPlan`, `CargaEnAlmacen`;
- `Grasp`;
- `ConstructorVecinoMasCercano`;
- `tabu/BusquedaTabu`, `tabu/Movimiento`, `tabu/TipoMovimiento`, `tabu/ListaTabu`,
  `tabu/AplicadorMovimiento`;
- pruebas `GraspTest` (8 casos) y `EnrutadorBloqueosTest` (9 casos);
- `DemoGrasp`, `DemoTabu`, `DemoTabuDataset`, `DemoBloqueos`, `EscenarioDemo`, `Reporte`,
  `DemoSimulacion5Dias`, `DemoColapso`, `DemoCapacidad`, `GenerarDatosDePrueba`,
  `VerificacionGrasp`, `VerificacionTabu`, `VerificacionBloqueos`, `VerificacionSimulacion`;
- actualización del ISA con:
  - clases realmente creadas;
  - correspondencia pseudocódigo -> métodos;
  - ejecución de ejemplo;
  - prueba mínima;
  - commit;
  - integrantes de la pareja y aportes.
