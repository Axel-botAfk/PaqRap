# Frontend beta de PaqRap

Interfaz React/Vite para los tres escenarios del simulador. Sigue el enfoque
visual del prototipo v6, pero no incorpora sus pedidos, rutas o incidencias
ficticias. Las vistas comienzan vacías y se llenan solo con respuestas del
backend Java. No es una copia pixel a pixel ni sustituye la revisión de GUI
del equipo.

## Uso local

Requiere Node.js 20.19+ o 22.12+ y el backend activo en `127.0.0.1:8081`.

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

- El catálogo de periodos y sus conteos procede de los archivos del curso.
- El usuario selecciona periodo, fecha/hora, escenario y algoritmo.
- `POST /api/ejecuciones` inicia una corrida asincrónica; WebSocket actualiza
  mapa y métricas. Una consulta REST cada cinco segundos recupera el estado
  si la conexión en vivo se interrumpe.
- El ID de ejecución queda en `?ejecucion=...`; abrir ese enlace en otro
  dispositivo muestra la misma corrida mientras permanezca en memoria.
- Rutas y paradas representan planificación, no entregas consumadas. Las
  entregas efectivas se toman de `avance` y `resumen`.
- No hay alta de pedidos, selección desde una base de datos ni historial de
  incidencias. Esos paneles muestran estados vacíos hasta tener datos.
- La grilla dibuja segmentos esquemáticos entre puntos. No afirma reproducir
  el recorrido de calles ni la posición en tiempo real de un repartidor.

La beta debe permanecer bajo acceso controlado mientras el backend no tenga
autenticación ni límites por usuario. Nunca colocar claves en `src/` ni en
variables Vite expuestas al navegador.
