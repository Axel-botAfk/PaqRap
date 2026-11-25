# components

Piezas reutilizables de la interfaz:

- `BarraSuperior.jsx` — barra fija; en las vistas de mapa lleva los controles del mapa y los relojes REAL y SIM (hasta el minuto).
- `BarraLateral.jsx` — menú de las cuatro vistas; define el orden en `VISTAS`.
- `Riel.jsx` — riel derecho plegable, con la pestaña para reabrirlo.
- `Acordeon.jsx` — bloque plegable del riel.
- `Mapa.jsx` — retícula, rutas (pendiente discontinua, recorrido atenuado), paradas, unidades y
  almacenes, con zoom, paneo, filtros, reproductor y leyenda. Pone sus controles en la cabecera.
- `PanelFlotante.jsx` — indicador flotante sobre el mapa (Productos, Estado, Leyenda): se arrastra
  desde la cabecera, se pliega y recuerda su lugar en el navegador.
- `DibujoVehiculo.jsx` — silueta por tipo de unidad, compartida entre mapa y leyenda.

Los estados siempre llevan color **y** texto, nunca solo color.

Responsables: Ariana y Axel.
