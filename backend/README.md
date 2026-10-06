# backend

Servidor de PaqRap: API REST, WebSocket y planificador de rutas.

- **Tecnología:** Java 17 + Spring Boot (Maven).
- **Responsables:** Melvin y Leslie (integración del planificador con la API).
- **Persistencia:** archivos del curso y estructuras en memoria (ver documentos 23 y 26 en `docs/`).

## Organización de paquetes

Se mantiene el paquete base que ya usa el proyecto del planificador. Dentro de él:

| Paquete | Qué contiene |
|---|---|
| `api` | Controladores REST y endpoints WebSocket |
| `planificador` | Orquesta GRASP, Búsqueda Tabú y ALNS |
| `grasp`, `tabu`, `alns` | Una carpeta por algoritmo |
| `ruteo` | Cálculo de rutas sobre la grilla 70x50 |
| `escenarios` | Tiempo real (día a día), simulación de 5 días y colapso |
| `simulacion` | Motor que emite eventos por WebSocket |
| `datos` | Lectura de los archivos del curso (pedidos, bloqueos, mantenimientos) |

## Contrato con el frontend

- REST bajo `/api/...` (por ejemplo `GET /api/salud`).
- WebSocket bajo `/ws/...` (por ejemplo `/ws/ejecuciones/{id}`).
- nginx reenvía ambos al puerto interno `8081`; el backend solo escucha en `127.0.0.1`.

## Cómo correrlo en local

Se completará cuando exista el proyecto Maven (`pom.xml`). Comando previsto:

```
mvn spring-boot:run
```

## Reglas

- No subir claves ni contraseñas. Usar variables de entorno.
- El código de la versión anterior de los algoritmos sigue en `codigo/` hasta terminar la integración.
