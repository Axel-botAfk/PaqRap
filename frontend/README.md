# Frontend beta de PaqRap

Interfaz React/Vite para los tres escenarios del simulador. Sigue el enfoque
visual del prototipo v6, pero no incorpora sus pedidos, rutas o incidencias
ficticias. Las vistas comienzan vacías y se llenan solo con respuestas del
backend Java. No es una copia pixel a pixel ni sustituye la revisión de GUI
del equipo.

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

## Comportamiento

- El catálogo procede de los TXT del curso o de MySQL, según el perfil del backend.
- El mapa distingue los tres almacenes y los nodos bloqueados vigentes a la hora simulada.
- El usuario selecciona periodo, fecha/hora, escenario y algoritmo.
- El ritmo lo marca el servidor: 5 días con duración objetivo de 30 a 60 min,
  día a día a 1×, 10×, 60× o 240×, y colapso a 2 h simuladas cada 10 s.
- El reloj en vivo se ancla al tiempo real (`src/reloj.js`): el instante sale
  del tiempo transcurrido desde que el servidor publicó la ventana, con un tope
  por cuadro para no saltar al volver a una pestaña dormida. Cada unidad sigue
  los hitos de su recorrido que informa el simulador.
- Pausar, Reanudar y Detener cambian el estado en el servidor. Las averías se
  registran durante la corrida (también en pausa); sus pedidos se replanifican
  y aparecen en Alertas.
- Al recargar, la corrida se retoma desde `?ejecucion=...` o desde el
  navegador. Si el servidor se reinició, se avisa y hay que iniciar otra.
- Los intermedios muestran inventario actualizado y el central se indica como
  ilimitado. Durante la reproducción histórica, el stock sigue siendo el
  último publicado; no existe historial de stock por fotograma.
- `POST /api/ejecuciones` inicia una corrida asincrónica; WebSocket actualiza
  mapa y métricas. Una consulta REST cada cinco segundos recupera el estado
  si la conexión en vivo se interrumpe.
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
- No hay alta de pedidos ni historial persistente de incidencias. Los pedidos
  vienen de la fuente configurada y las averías se pierden al reiniciar.
- La grilla dibuja segmentos esquemáticos entre puntos. No afirma reproducir
  el recorrido de calles ni la posición en tiempo real de un repartidor. Los
  fotogramas sí contienen posiciones muestreadas del simulador, pero los nodos
  entre dos muestras son una transición visual, no telemetría. Durante la
  reproducción, las rutas muestran el plan histórico del instante; las
  métricas superiores y el inventario siguen siendo los últimos publicados.

La beta debe permanecer bajo acceso controlado mientras el backend no tenga
autenticación ni límites por usuario. Nunca colocar claves en `src/` ni en
variables Vite expuestas al navegador.
