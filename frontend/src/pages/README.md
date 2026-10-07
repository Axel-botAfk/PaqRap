# pages

Una página por pantalla de la navegación. Responsables: Ariana y Axel.

| Archivo | Pantalla |
| --- | --- |
| `Pedidos.jsx` | Pedidos del plan vigente y sin asignar, con filtros y carga masiva. |
| `Averias.jsx` | Averías de la flota y reglas de indisponibilidad. |
| `OperacionDiaria.jsx` | Seguimiento de la jornada en curso sobre el mapa. |
| `Simulacion.jsx` | Configuración y corrida de los tres escenarios. |

`Operación diaria` y `Simulación` comparten estructura: riel de la izquierda, mapa a la
derecha y detalle de ruta en el riel derecho cuando hay una elegida. `Pedidos` y `Averías`
usan la variante con scroll.
