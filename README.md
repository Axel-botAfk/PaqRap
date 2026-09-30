# PaqRap

PaqRap es un proyecto de software orientado a mejorar la planificación y el monitoreo de entregas de una empresa de distribución urbana. La solución busca reemplazar el manejo manual de pedidos y rutas por un sistema capaz de organizar la operación, reducir costos de recorrido y ayudar a cumplir los plazos comprometidos con los clientes.

## Sobre el proyecto

La empresa PaqRap distribuye un único tipo de producto mediante una flota compuesta por autos, motos y bicicletas. Atiende pedidos regulares y priorizados, cuenta con un almacén central y dos almacenes intermedios, y debe responder ante situaciones como bloqueos de calles o fallas de vehículos.

## Estado actual

Está implementado el componente planificador, la operación simulada y los tres escenarios del caso,
sobre los datos reales que entrega el equipo docente.

**Tres metaheurísticas independientes**, cada una en su carpeta y comparables entre sí porque
comparten el evaluador y nada más:

| algoritmo | cómo busca | punto de partida |
|---|---|---|
| GRASP | construcción golosa aleatorizada + búsqueda local, repetidas | desde cero en cada iteración |
| Búsqueda tabú | mejora con memoria de corto plazo | vecino más cercano factible, sin usar GRASP |
| ALNS | destruye 10-35% y repara, con pesos adaptativos | su propia reparación por arrepentimiento |

**Reglas del caso implementadas:** ciudad de 70x50 km sobre retícula, bloqueos por nodo con
recorrido que los esquiva, turnos con refrigerio, averías de los tres tipos, mantenimiento
preventivo, almacenes intermedios con recarga diaria, entregas parciales, carga a bordo entre
replanificaciones y lectura de los tres archivos de datos para cualquier mes.

**La unidad de la operación es el producto**, no el pedido: es lo que ocupa capacidad, lo que
descuenta el inventario, lo que penaliza la función objetivo y lo que reparten las entregas
parciales.

Quedan pendientes el trasvase entre unidades, el mantenimiento prospectivo y el componente
visualizador, que se conecta por el observador que ya expone el simulador.

La [decisión y verificación del constructor independiente de Tabú](codigo/paqrap/docs/semana7-vecino-cercano.md)
documenta el cambio técnico de la semana 7 y los resultados exploratorios, aún no
equivalentes a la experimentación final del curso.

## Estructura

```text
codigo/
  historico/paqrap-grasp_v02/   Primera iteración del planificador (congelada como evidencia)
  paqrap/                       Proyecto vigente
```

Dentro de `codigo/paqrap/src/main/java/com/paqrap/`:

```text
modelo/          la ciudad, la flota, los pedidos, los bloqueos, las averías y los turnos
datos/           lectores de los archivos del caso y datos fijos del enunciado
escenarios/      instancias de prueba compartidas por las demos
planificador/    evaluador, inventario y parámetros comunes a los tres algoritmos
  ruteo/         distancias, mapa de bloqueos y recorrido que los esquiva
  grasp/         GRASP y su búsqueda local
  tabu/          búsqueda tabú, movimientos y lista tabú
  alns/          destructores, reparadores y ruleta adaptativa
simulacion/      operación con replanificación, ritmo (Ta, Sa, K, Sc) y resumen
verificacion/    51 comprobaciones ejecutables, sin JUnit
demo/            puntos de entrada: día a día, 5 días, colapso y banco de pruebas
```

## Cómo se corre

Desde `codigo/paqrap`:

```bash
javac -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")
```

| qué | comando |
|---|---|
| verificaciones | `java -cp target/classes com.paqrap.verificacion.VerificacionSimulacion` |
| día a día / medición | `java -cp target/classes com.paqrap.demo.DemoDatosReales 5 datos/reales` |
| escenario 5D (presentación, 30-60 min) | `java -cp target/classes com.paqrap.demo.DemoSimulacion5Dias` |
| 5D a fondo, para medir Ta | `java -cp target/classes com.paqrap.demo.DemoMedicion5D 5 datos/reales` |
| colapso | `java -cp target/classes com.paqrap.demo.DemoColapso` |
| comparar algoritmos | `java -Dpaqrap.algoritmos=tabu,grasp,alns -cp target/classes com.paqrap.demo.BancoDePruebas 3 datos/reales` |

Propiedades de uso frecuente: `-Dpaqrap.algoritmo=tabu|grasp|alns|constructivo`,
`-Dpaqrap.periodo=202610`, `-Dpaqrap.bloques=true` (lectura por bloques),
`-Dpaqrap.duracion=45` y `-Dpaqrap.sa=5000` (ritmo de pantalla en la 5D),
`-Dpaqrap.traza=20` (una línea por planificación con la cola, la carga y el Ta).

En PowerShell los `-D` van entre comillas, y la compilación es:

```powershell
$fuentes = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
[System.IO.File]::WriteAllLines("$pwd\sources.txt", $fuentes)
javac -encoding UTF-8 -d target\classes "@sources.txt"
```
