# Frontend beta de PaqRap

Interfaz React/Vite para los escenarios del simulador, con el armazón del prototipo v6: el
mapa ocupa la pantalla y todo lo demás flota encima y se pliega. No incorpora los pedidos,
rutas ni incidencias ficticias del prototipo: las vistas comienzan vacías y se llenan solo con
respuestas del backend Java. No es una copia pixel a pixel ni sustituye la revisión de GUI del
equipo.

## Uso local

Requiere Node.js 20.19+ o 22.12+ y el backend activo en `127.0.0.1:8080`.

```sh
npm ci
npm run dev
```

Abrir `http://127.0.0.1:5173/`. Vite reenvía `/api` y `/ws` al backend;
el frontend no guarda host, token ni credenciales. Para producción:

```sh
npm run build
```

Copiar el contenido de `dist/` a la raíz web servida por nginx. Consultar
`deploy/BETA_ENTREGA.md` antes de instalarlo en la VM del curso.

## Cómo está armado

```
src/
├── App.jsx                   armazón, estado de la corrida y carga de datos
├── formato.js                fechas, números, ritmo y color por ruta
├── mapa.js / reloj.js        geometría de la retícula y reloj anclado (sin React)
├── pedidos.js                reglas del pedido: archivo de ventas, plazos y retícula
├── components/               barra superior, menú, riel, acordeón, mapa
├── pages/                    riel de la corrida y las hojas de pedidos y averías
└── services/api.js           REST y WebSocket
```

Cuatro vistas, como el prototipo: **Pedidos** y **Averías de la flota** son hojas que se
superponen al mapa; **Operación diaria** y **Simulación** son vistas de mapa y comparten el
riel derecho (`pages/RielCorrida.jsx`). Pedidos reúne en pestañas las tres pantallas de
pedidos del prototipo —registrados, alta y carga masiva— y abre el seguimiento al pulsar
«Seguir» en una fila, en vez de añadir entradas al menú lateral. La vista decide el escenario: operación diaria fija
`OPERACION_DIARIA` y simulación deja elegir entre `PERIODO_5_DIAS` y `COLAPSO`. Al recuperar
una corrida existente manda su escenario y la vista se ajusta a él.

Se pliegan el menú lateral (el *burger*), el riel entero (deja una pestaña para reabrirlo),
cada bloque del riel y la leyenda del mapa. En pantallas de 900 px o menos el menú y el riel
se superponen al mapa en vez de quitarle ancho, y ambos arrancan cerrados.

El mapa tiene zoom (`+`, `−`, `TODO` y la rueda), paneo con arrastre y un panel de filtros por
tipo de unidad y por situación del pedido. Los símbolos conservan su tamaño en pantalla al
acercar: lo que crece es la ciudad, no los iconos.

La tipografía Archivo se carga desde Google Fonts en `index.html`, con `system-ui` de
respaldo. Si la VM del curso no debe pedir recursos externos, hay que servirla desde `dist/`.

## Comportamiento

- El catálogo procede de los TXT del curso o de MySQL, según el perfil del backend.
- El mapa distingue los tres almacenes y los nodos bloqueados vigentes a la hora simulada.
- El usuario solo elige fecha y hora de inicio, y en Simulación el tipo de escenario. El mes
  de datos sale de la fecha; el calendario está acotado a los periodos con datos cargados.
- El planificador es siempre Vecino Más Cercano + Búsqueda Tabú; no se elige desde la pantalla.
- El ritmo es fijo por escenario y tampoco se configura: 5 días en unos 40 min reales,
  día a día a 60× y colapso a 2 h simuladas cada 10 s.
- La corrida solo se inicia. No hay Pausar ni Detener: para cambiar de escenario se inicia otra.
  La anterior sigue viva en el servidor hasta que termina por sí sola.
- El reloj en vivo se ancla al tiempo real (`src/reloj.js`): el instante sale
  del tiempo transcurrido desde que el servidor publicó la ventana, con un tope
  por cuadro para no saltar al volver a una pestaña dormida. Cada unidad sigue
  los hitos de su recorrido que informa el simulador.
- Pausar, Reanudar y Detener cambian el estado en el servidor. Las averías se
  registran durante la corrida (también en pausa); sus pedidos se replanifican
  y aparecen en Averías de la flota.
- Al recargar, la corrida se retoma desde `?ejecucion=...` o desde el
  navegador. Si el servidor se reinició, se avisa y hay que iniciar otra.
- Los intermedios muestran inventario actualizado y el central se indica como
  ilimitado. El llenado de la barra se mide contra el `stockInicial` del caso, porque la API
  no publica capacidad. Durante la reproducción histórica el stock sigue siendo el último
  publicado; no existe historial de stock por fotograma.
- `POST /api/ejecuciones` inicia una corrida asincrónica; WebSocket actualiza
  mapa y métricas. Una consulta REST cada cinco segundos recupera el estado
  si la conexión en vivo se interrumpe.
- El costo de cada ruta (`RutaVista.costo`, S/ 8, 6 y 3 por km según el tipo de unidad) se
  muestra en la lista de rutas del riel, con el total de distancia y costo del instante al
  pie. El resumen trae el costo total de la corrida, pero el servidor solo lo publica al
  terminar; esto se ve mientras corre. No toca el mapa.
- El mapa consulta además los fotogramas de posiciones realmente ejecutadas.
  Con «Historial» o el deslizador se revisa lo ya ocurrido (pausa, selección de
  instante y tres velocidades); «En vivo» vuelve al reloj de la corrida. Cuando varias
  unidades comparten nodo se muestra un contador para evitar iconos superpuestos.
- Consulta el historial paginado de planes: al mover el reloj muestra las
  rutas que estaban vigentes entonces, incluidas las simultáneas. Cada ruta
  une todas sus paradas en orden y las numera; un aro señala la última.
- El ID de ejecución queda en `?ejecucion=...`; abrir ese enlace en otro
  dispositivo muestra la misma corrida mientras permanezca en memoria.
- Rutas y paradas históricas representan planificación, no entregas consumadas. Las
  entregas efectivas se toman de `avance` y `resumen`.
- El **seguimiento** de un pedido sale del historial de planes, la misma fuente que la
  columna «Situación»: a qué ruta y unidad quedó asignado en cada replanificación y qué
  hitos informó el simulador. Cada salida posterior a la primera se rotula «retoma la ruta»,
  porque es la unidad reanudando desde donde estaba y no una salida de almacén.
- El **alta de pedidos** manda el pedido a la corrida cuando hay una activa
  (`POST /api/ejecuciones/{id}/pedidos`): entra a la cola del planificador en el corte
  siguiente y el servidor le pone la fecha de registro con el reloj de la operación. Es lo que
  permite que el día a día en tiempo real reciba demanda que no existía al arrancar. Sin corrida
  activa no hay dónde registrarlo y entrega la línea en el formato del archivo mensual, como
  antes. La **carga masiva** sigue sin endpoint: valida el archivo entero —formato, día dentro
  del mes, hora, nodo dentro de la retícula, cantidad y plazo— y lista todos los fallos de una
  vez, mientras que el backend aborta en el primero. Cada pantalla dice en su pie qué hace.
- Las averías se pierden al reiniciar el servidor; no hay historial persistente de
  incidencias.
- La grilla dibuja segmentos esquemáticos entre puntos. No afirma reproducir
  el recorrido de calles ni la posición en tiempo real de un repartidor. Los
  fotogramas sí contienen posiciones muestreadas del simulador, pero los nodos
  entre dos muestras son una transición visual, no telemetría. Durante la
  reproducción, las rutas muestran el plan histórico del instante; las
  métricas superiores y el inventario siguen siendo los últimos publicados.

## Lo que esta interfaz deduce porque la API no lo publica

Dos datos del prototipo no existen en el contrato actual y se resuelven en el cliente;
**conviene rehacerlos en el backend** cuando haya oportunidad. El tercer apartado documenta de
dónde sale cada cifra de la barra superior.

### 1. Situación del pedido: «en ruta» y «entregado»

`PedidoVista.estado` solo toma dos valores en todo el backend: `"PLANIFICADO"`
(`Vistas.ruta`) y `"NO_ASIGNADO"` (`EjecucionesService`). El filtro del mapa, la columna
«Situación» de Pedidos y los indicadores de la barra superior usan `estadosDePedido()`
(`src/mapa.js`), que
recorre todos los planes conocidos y cruza dos fuentes:

- **Entregado** cuando el hito `ENTREGA` o `FIN_ENTREGA` del pedido ya ocurrió en el instante
  reproducido.
- **En ruta** cuando la unidad ya pasó por `SALIDA` y el pedido sigue pendiente. Aquí no basta
  con mirar los hitos: los `trayectos` solo llegan hasta el `CORTE` de su ventana, así que una
  unidad que sale a las 08:00 con entregas a las 09:30 informa `SALIDA`, `ALMACEN` y `CORTE` y
  ninguna `ENTREGA`. Por eso también cuentan las paradas de la ruta de una unidad que ya salió,
  aunque su trayecto no las alcance.

Es una deducción fiel a los hitos, pero depende de que el historial de planes esté completo en
el cliente. Si el backend llega a publicar el estado real por pedido, conviene usarlo y dejar
`estadosDePedido()` solo como respaldo.

### 2. La operación día a día va en tiempo real — **a confirmar con el docente**

`TIEMPO_REAL_DIA_A_DIA` en `src/formato.js` está en `true` y
`paqrap.operacion.tiempo-real` en el backend también. Con eso la operación diaria:

- no pide fecha ni hora: el servidor la ancla al minuto presente (`anclarAlPresente()`);
- avanza a 1× (K = 1), así que un minuto de pantalla es un minuto de operación;
- arranca con la cola vacía si el mes en curso no tiene datos, en vez de rechazar el inicio;
- recibe pedidos mientras corre por `POST /api/ejecuciones/{id}/pedidos`.

Sa no cambia (20 s): el planificador se sigue invocando una vez cada 20 s reales, igual que a
60×. Lo único que se encoge es Sc, de 20 min a 20 s. Por eso el movimiento en pantalla se ve
muy lento: es lo que se mueve una unidad en veinte segundos de realidad.

**Por qué está a confirmar.** Los ítems del docente apuntan a lo contrario: C02 («¿Pide fecha
y hora hasta el nivel de minuto?») está dado como conforme, A18 y A19 registran K = 60 para el
día a día como dato correcto, y la prueba del propio docente en B14 fue con fecha 15/01/2029.
Además el concepto de evaluación del bloque C es «MAPA BASE – todos los escenarios», y C12/C13
piden el *momento simulado* y el *tiempo simulado transcurrido* en todos ellos: una operación en
tiempo real no tiene momento simulado. Se implementa igual porque es la lectura natural de
«operación día a día» y porque el equipo decidió verificarla contra el docente con la pantalla
funcionando delante.

**Rollback** (vuelve exactamente al comportamiento anterior, 60× con inicio elegido):

1. `TIEMPO_REAL_DIA_A_DIA = false` en `src/formato.js`.
2. `paqrap.operacion.tiempo-real=false`, o `PAQRAP_TIEMPO_REAL=false` en el entorno.

Lo que queda puesto en los dos casos, porque no depende del interruptor y no estorba: el alta de
pedidos contra una corrida activa y `Simulador.registrarPedidoEnVivo()`.

Con el interruptor apagado vuelve el desajuste anterior: la jornada dura `24 h / velocidad` y
`RitmoEscenario.VELOCIDADES` solo admite `{1, 10, 60, 240}`, así que 60× da 24 min y la banda de
30 a 60 min exigiría una velocidad entre 24 y 48. El arreglo es una línea en el backend —añadir
`30` a `VELOCIDADES`, que da 48 min— y cambiar `VELOCIDAD_DIA_A_DIA`.

### 3. Los cuatro indicadores de la barra superior

La franja superior reparte los pedidos entre cuatro situaciones y no muestra nada más
(`chips` en `src/App.jsx`). Tres se cuentan sobre el plan vigente, igual que la hoja de
Pedidos, y la cuarta no:

| Indicador | De dónde sale |
|---|---|
| Entregados | `avance.pedidosEntregados`, acumulado de la corrida |
| En ruta | paradas del plan vigente que `estadosDePedido()` marca `EN_RUTA` |
| Planificados | paradas del plan vigente sin situación deducida: con ruta, pero su unidad aún no sale |
| Por planificar | `ejecucion.noAsignados` |

Los entregados van aparte porque un pedido entregado **sale del plan vigente**: contarlo sobre
las rutas lo perdería en la siguiente replanificación. El acumulado es el único conteo que
sobrevive, y por eso tampoco baja al mover el reloj hacia atrás: durante la reproducción
histórica es el último valor publicado, no el de ese instante.

«Planificados» suele quedarse en 0 en las corridas del curso: el planificador emite el plan y
las unidades salen en ese mismo instante, así que el pedido pasa de planificado a en ruta sin
un tramo visible. No es un fallo del conteo — se ve crecer al mover el reloj a un instante
anterior a la `SALIDA` de la unidad.

Antes esta franja mostraba `PEDIDOS 109 / 6 240`, con el total del mes como denominador en vez
del horizonte de la corrida. Ese cociente ya no existe, así que la nota sobre el denominador
equivocado se retira; si algún día hace falta el total del horizonte, el backend tendría que
publicar `pedidosRecibidos` en `AvanceVista` (hoy `resumen` solo se llena al terminar).

La beta debe permanecer bajo acceso controlado mientras el backend no tenga
autenticación ni límites por usuario. Nunca colocar claves en `src/` ni en
variables Vite expuestas al navegador.
