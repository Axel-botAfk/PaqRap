# services

Comunicación con el backend y utilidades sin interfaz. Siempre rutas relativas:
REST por `fetch("/api/...")` y WebSocket por `wss://<mismo-dominio>/ws/ejecuciones/{id}`.

| Archivo | Qué hace |
| --- | --- |
| `api.js` | Llamadas REST y construcción de la URL del WebSocket. |
| `useEjecucion.js` | Conexión con una ejecución: WebSocket, respaldo REST cada 5 s y marca de «desactualizado». |
| `formato.js` | Fechas, números y duraciones en es-PE. Sin dato se muestra «Sin datos», nunca 0. |

Responsables: Ariana y Axel. Lo que falta del lado del servidor está en `docs/api-pendiente.md`.
