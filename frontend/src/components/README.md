# components

Piezas reutilizables de la interfaz. Responsables: Ariana y Axel.

| Archivo | Qué hace |
| --- | --- |
| `Mapa.jsx` | Retícula 70 × 50, rutas, pedidos, unidades y almacenes. Zoom y filtros locales a cada navegador. |
| `Kpis.jsx` | Franja de indicadores; exporta también `Kpi` suelto. |
| `ListaRutas.jsx` | Rutas del plan vigente; al elegir una se resalta en el mapa. |
| `DetalleRuta.jsx` | Secuencia de paradas de la ruta elegida, en el riel derecho. |
| `Avisos.jsx` | Banner de avería, modal de colapso y aviso de error. |
| `InventarioAlmacenes.jsx` | Existencias por almacén (a la espera del campo en la API). |
| `CargaMasiva.jsx` | Subida de los archivos mensuales (a la espera del endpoint). |

Todo color de estado va acompañado de texto, nunca solo color.
