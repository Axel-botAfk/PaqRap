# Guía de trabajo del equipo

Cómo está ordenado el repositorio y cómo trabajamos para no pisarnos.

## Dónde va cada cosa

```
PaqRap/
├── backend/              Spring Boot: API REST, WebSocket y planificador
├── frontend/             React: interfaz web
├── datos/                muestras livianas de los archivos del curso
├── docs/                 documentos de diseño y diagramas
├── deploy/               configuración del servidor (sin secretos)
├── .github/workflows/    pipeline de despliegue (GitHub Actions)
├── codigo/               versión anterior de los algoritmos (se migra a backend/ y luego se retira)
└── README.md
```

## Quién lleva qué (semana 8)

| Persona | Carpeta principal | Tarea |
|---|---|---|
| Ariana | `frontend/` | Pantallas según el prototipo |
| Axel | `frontend/` (con Ariana), `datos/`, `deploy/` | Datos reales, pruebas de integración y despliegue |
| Melvin | `backend/` (`api`, `planificador`) | Integrar el planificador con la API |
| Leslie | `backend/` (con Melvin), `deploy/`, `.github/workflows/` | Integración y despliegue |
| Todos | `docs/` | Documentos y diagramas propios |

Si necesitas tocar una carpeta que lleva otra persona, avísale antes.

## Ramas y Pull Requests

- `main` es lo que está desplegado. **Nadie hace push directo a `main`.**
- Cada persona trabaja en su rama: `feature/<nombre>-<tema>`, por ejemplo `feature/ariana-mapa` o `feature/melvin-api-ejecuciones`.
- Los cambios entran a `main` por **Pull Request**, revisado al menos por otra persona.
- Antes de empezar, traer los últimos cambios de `main` a tu rama.

## Mensajes de commit

Cortos y con el área al inicio:

```
api: agrega POST /api/ejecuciones
frontend: mapa de operación día a día
deploy: ajusta timeout del WebSocket
docs: actualiza 25.dis.gui a v1.2
```

## Lo que nunca se sube

- Contraseñas, llaves privadas, archivos `.env`.
- Carpetas generadas: `node_modules/`, `target/`, `dist/`.
- Archivos de datos pesados (el repositorio es público).

## Despliegue

Cuando exista el workflow, cada cambio que entre a `main` se despliega solo en la máquina virtual de la PUCP. Por eso `main` siempre debe compilar y arrancar. Detalles en `deploy/README.md`.
