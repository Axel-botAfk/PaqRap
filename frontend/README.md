# frontend

Interfaz web de PaqRap (mapa de operación, simulación, colapso e incidencias).

- **Tecnología:** React (con Vite).
- **Responsables:** Ariana y Axel.
- **Diseño de referencia:** prototipo `01.definicion.prototipo` y documento `25.dis.gui` (carpeta `docs/`).

## Organización prevista

```
frontend/
├── package.json
└── src/
    ├── pages/        una página por pantalla del prototipo
    ├── components/   mapa, panel de incidencias, semáforo, tablas...
    └── services/     llamadas a la API (/api) y al WebSocket (/ws)
```

## Conexión con el backend

El frontend usa **rutas relativas**, porque en producción nginx sirve el front y la API desde el mismo dominio:

- REST: `fetch("/api/...")`
- WebSocket: `wss://<mismo-dominio>/ws/...`

Así no hay problemas de CORS ni direcciones fijas en el código.

## Cómo correrlo en local

Se completará cuando exista el proyecto (`package.json`). Comandos previstos:

```
npm install
npm run dev
```

## Reglas

- No subir `node_modules/` ni la carpeta `dist/` (están en `.gitignore`).
- Los textos de estados y errores deben coincidir con el documento 25 (sección de estados y errores).
