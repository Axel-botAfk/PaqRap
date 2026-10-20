# Interacción Planificador – Visualizador

Equipo 2B · 1INF54 · Semana 8

Este diagrama describe **la interacción tal como está implementada** en
`backend/src/main/java/com/paqrap/api` y `frontend/src/`, no la arquitectura propuesta en
`26.dis.arquitectura.solucion.v2`. Las dos difieren en un punto: el documento describe dos
JAR independientes (`paqrap-grasp_v02.jar` y `paqrap-tabu_v02.jar`) que el Servicio
planificador elige en tiempo de ejecución; el código tiene un solo módulo `codigo/paqrap`
del que `EjecucionesService` instancia `Grasp` o `BusquedaTabu` directamente.

## Quién es quién

| Elemento del diseño | Clase o archivo real |
| --- | --- |
| Visualizador | SPA React: `frontend/src/App.jsx` y sus pantallas |
| Servicio de aplicación | `ApiController` (REST) |
| Motor de simulación | `Simulador` (`com.paqrap.simulacion`) |
| Servicio planificador | `EjecucionesService` |
| Planificador | `Grasp` o `BusquedaTabu`, ambos implementan `Planificador` |
| Evaluador común | `Evaluador` |
| Canal en vivo | `EjecucionWebSocketHandler` + `EventosWebSocket` |
| Almacenamiento | archivos del curso, vía `DatosService` |

El Visualizador y el Perfil de planificación no son procesos separados: se sirven dentro de
la misma SPA, como ya aclara `26.dis.arquitectura.solucion.v2` §3.

## Secuencia principal

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant V as Visualizador<br/>(SPA React)
    participant A as ApiController<br/>(REST)
    participant S as EjecucionesService
    participant W as EventosWebSocket
    participant M as Simulador<br/>(motor)
    participant P as Planificador<br/>(Grasp | BusquedaTabu)
    participant E as Evaluador
    participant F as Archivos del curso

    U->>V: abre la aplicación
    V->>A: GET /api/datos/periodos
    A->>F: lee ventas / bloqueos / mantenimiento
    F-->>A: periodos disponibles
    A-->>V: 200 ["202601", ...]

    U->>V: elige escenario, fecha y hora
    V->>A: POST /api/ejecuciones
    A->>S: crear(request)
    Note over S: valida fecha, escenario y horizonte<br/>un hilo de simulación, cola de 2
    S-->>A: id (estado PENDIENTE)
    A-->>V: 202 Accepted + id

    V->>W: WS /ws/ejecuciones/{id}
    W-->>V: INSTANTANEA (estado inicial)

    S->>S: actualizar(EN_CURSO)
    S->>W: publicar(INICIADA)
    W-->>V: INICIADA

    S->>M: correr(inicio, horizonte, ventas, flota)

    loop cada avance del reloj simulado
        M->>P: planificar(EstadoOperacion, Parametros)
        P->>E: evaluar candidatas y validar plan
        E-->>P: factible, violaciones, métricas
        P-->>M: Solucion (rutas, no asignados, costo)
        M->>S: observador(reloj, estado, plan, medición, avance)
        S->>W: publicar(PROGRESO | PLAN_SIN_ASIGNACIONES)
        W-->>V: evento + EjecucionVista
        V->>V: redibuja mapa, rutas, métricas
    end

    M-->>S: ResumenSimulacion
    S->>W: publicar(TERMINADA | COLAPSO)
    W-->>V: evento final + resumen
    V->>U: muestra resultados del escenario
```

## Qué pasa cuando algo se corta

```mermaid
sequenceDiagram
    autonumber
    participant V as Visualizador
    participant A as ApiController
    participant S as EjecucionesService
    participant M as Simulador

    rect rgb(245, 242, 242)
    Note over V,A: Caída del canal en vivo
    V--xV: onclose / onerror del WebSocket
    V->>V: marca la vista como «desactualizada»
    loop cada 5 s hasta estado final
        V->>A: GET /api/ejecuciones/{id}
        A-->>V: última instantánea conocida
    end
    end

    rect rgb(245, 242, 242)
    Note over V,M: Cancelación
    V->>A: POST /api/ejecuciones/{id}/cancelar
    A->>S: marcar CANCELADA
    S-->>V: 200
    M->>S: observador(siguiente avance)
    S--xM: CancellationException
    S->>V: CANCELADA
    end
```

El reloj simulado (`reloj`) y el tiempo real de cómputo (`actualizadoEn`) viajan por separado
en cada `EjecucionVista`, de modo que el Visualizador los puede distinguir en todo momento,
como exige `25.dis.gui.v2` §1.

## Lo que el Planificador no recibe del Visualizador

Vale la pena dejarlo escrito porque delimita la interacción: el Visualizador **solo** puede
crear, consultar y cancelar una ejecución. No registra pedidos, no reporta averías, no sube
archivos y no modifica un plan en curso. La API no tiene endpoints de escritura sobre el
dominio, de modo que no existe un camino por el que la interfaz pueda alterar una corrida en
marcha más allá de cancelarla.
