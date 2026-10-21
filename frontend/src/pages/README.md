# pages

Una por vista del prototipo v6:

- `RielCorrida.jsx` — contenido del riel en las dos vistas de mapa (operación diaria y
  simulación): configuración, rutas del instante, avería, inventario, colapso y resumen.
- `HojaPedidos.jsx` — hoja de pedidos: pestañas de las tres pantallas de pedidos del
  prototipo, más el seguimiento al pulsar «Seguir» en una fila.
- `PedidoNuevo.jsx` — alta de un pedido con la lista de comprobaciones (imagen 09).
- `PedidoCargaMasiva.jsx` — lectura y validación del archivo mensual de ventas (imagen 10).
- `PedidoSeguimiento.jsx` — línea de tiempo y asignaciones de un pedido (imagen 11).
- `HojaAverias.jsx` — hoja de averías y excepciones de la corrida.

Las reglas del pedido (formato del archivo, plazos, retícula) viven en `src/pedidos.js`, no
en estos componentes: así se prueban sin montar React.

Usar los nombres de pantalla del documento 25 (`docs/`).

Responsables: Ariana y Axel.
