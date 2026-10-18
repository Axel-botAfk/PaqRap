# CPA — casos de aceptación de API y escenarios

Versión de trabajo para consolidación del equipo. Técnica: clases de equivalencia y valores límite. Ambiente local: MySQL Docker con semillas sintéticas; la validación final con datos reales y VM debe anotarse por separado. Un resultado esperado no se considera ejecutado hasta registrar fecha, entorno, respuesta y evidencia.

## Clases de entrada

| Variable | Clase válida | Clases inválidas / frontera |
| --- | --- | --- |
| Escenario | `OPERACION_DIARIA`, `PERIODO_5_DIAS`, `COLAPSO` | omitido, desconocido |
| Algoritmo | `GRASP`, `TABU` | omitido, desconocido |
| Inicio | fecha/hora del periodo disponible | formato inválido, periodo sin datos, borde de mes sin archivos siguientes |
| Horizonte de colapso | 1–60 días | 0, 61, negativo; no corresponde a otros escenarios |
| Pausa de demostración | ausente o 0–500 ms | -1, 501, texto |
| ID de ejecución | UUID existente | UUID inexistente, formato incorrecto |
| Índice de fotogramas | entero >= 0 | negativo, texto |
| Avería | unidad de flota, tipo 1/2/3, corrida activa | unidad desconocida, tipo inválido/ausente, repetida, corrida pendiente/final |

## Casos API

| ID | Entrada y acción | Resultado esperado | Estado |
| --- | --- | --- | --- |
| CPA-API-01 | `GET /api/salud` con MySQL local | `200`, `estado=OK`, `fuente=MYSQL` | Ejecutado 18/10/2026 |
| CPA-API-02 | `GET /api/datos/periodos` | `200`, incluye `202901`, `202902`, `202903` | Ejecutado 18/10/2026 |
| CPA-API-03 | `GET /api/datos/periodos/no-es-periodo` | `400 PERIODO_INVALIDO`, sin ruta interna ni traza | Automatizado |
| CPA-API-04 | Crear con algoritmo desconocido o campos obligatorios ausentes | `400 SOLICITUD_INVALIDA` | Automatizado |
| CPA-API-05 | Crear con `pausaMs=501` | `400 PAUSA_INVALIDA` | Automatizado |
| CPA-API-06 | Crear con `pausaMs=0` y `500` | `202`, corrida admitida; sin alterar reglas del algoritmo | Pendiente límites |
| CPA-API-07 | Consultar inventario de UUID inexistente | `404 EJECUCION_NO_ENCONTRADA` | Automatizado |
| CPA-API-08 | Consultar inventario de una corrida | Reloj coherente; dos intermedios con stock numérico; central con `ilimitado=true`, `disponible=null` | Automatizado y E2E local |
| CPA-API-09 | Registrar avería de unidad real y tipo 3 en `EN_CURSO` | `202 REGISTRADA`; posteriormente `APLICADA`; la unidad se retira y se replantea | Registro/aplicación E2E local; reasignación dirigida pendiente |
| CPA-API-10 | Registrar avería sin tipo | `400`, no se altera la corrida | Automatizado |
| CPA-API-11 | Registrar unidad desconocida | `400 UNIDAD_DESCONOCIDA` | Ejecutado 18/10/2026 |
| CPA-API-12 | Repetir avería de la misma unidad | `409 AVERIA_DUPLICADA`, solo una aplicación | Pendiente automatizar |
| CPA-API-13 | Registrar avería en corrida terminada | `409 EJECUCION_NO_ACTIVA` | Automatizado a nivel servicio |
| CPA-API-14 | Fotogramas con `desde=-1` | `400 INDICE_INVALIDO` | Automatizado a nivel servicio |
| CPA-API-15 | Cancelar durante ejecución y consultar | `CANCELADA`; no continúa planificando | Pendiente E2E |
| CPA-API-16 | Consultar `/planes?desde=0` y páginas posteriores | Índices consecutivos, reloj ordenado, varias rutas y paradas sin reemplazar planes anteriores | Automatizado y E2E local 18/10/2026: 112 planes, 63 con rutas simultáneas, máximo 2 paradas por ruta |
| CPA-API-17 | Consultar `/planes?desde=-1` | `400 INDICE_INVALIDO` | Automatizado a nivel servicio |
| CPA-API-18 | Nueva replanificación durante corrida activa | Evento `CALCULANDO_RUTAS`, `calculandoRutas=true` y rutas actuales vacías; al terminar, `false` y nuevo plan; los planes históricos siguen disponibles | E2E WebSocket local 18/10/2026: 36 eventos de cálculo y 36 planes publicados; revisión visual pendiente |

## Escenarios y punta a punta

| ID | Clase y datos | Comprobación de aceptación | Estado |
| --- | --- | --- | --- |
| CPA-ESC-01 | Operación diaria, `202901`, inicio 2029-01-15, GRASP, semilla 7 | Termina; pedidos recibidos coinciden con la fuente; GUI y API muestran mismo avance; reloj e inventario llegan al fin | Ejecutado local con avería tipo 3; contrastar métricas GUI visualmente |
| CPA-ESC-02 | Cinco días, `202902`, inicio 2029-02-01, GRASP | Procesa cinco jornadas y 75 pedidos; `TERMINADA` | Ejecutado local 18/10/2026; falta TABU y revisión visual |
| CPA-ESC-03 | Colapso, `202903`, inicio 2029-03-01, TABU, horizonte 1, semilla 7 | Estado y primer incumplimiento provienen del motor; no se inventa colapso | Ejecutado local 18/10/2026: `COLAPSADA`, 35 pedidos |
| CPA-ESC-04 | Avería de vehículo con ruta que contiene pedido identificable | Capturar asignación antes; averiar la unidad; constatar nueva unidad o estado `NO_ASIGNADO` con motivo tras replanificación; nunca dar el pedido por entregado sin entrega efectiva | Ejecutado local 18/10/2026: pedido `4-D-001` pasó de `TA01` a `TA06` |
| CPA-ESC-05 | Stock intermedio durante operación | Registrar stock antes y después de carga/reabastecimiento; no negativo; la GUI coincide con el endpoint y dice “sin datos” si falta respuesta | Pendiente prueba dirigida con Ariana |
| CPA-ESC-06 | GUI abierta en dos dispositivos y WebSocket interrumpido | Ambos ven el mismo ID y valores; REST recupera el estado tras cortar el socket | Pendiente VM con Melvin/Leslie |
| CPA-ESC-07 | Reproducir un periodo con planes sucesivos y rutas simultáneas | Mapa y lista cambian con el reloj; se ven todas las rutas del plan elegido, las paradas 1..N y la última destacada; no se presenta el plan final en los fotogramas antiguos | Prueba de selección automatizada en frontend; revisión visual y E2E pendientes |

## Registro de ejecución

Para cada caso pendiente anotar: fecha/hora, commit, perfil `mysql` o archivos, VM/local, periodo, algoritmo, semilla, solicitud, código/respuesta relevante, resultado esperado/observado, captura o log, responsable, estado (aprobado/fallido/bloqueado) e incidencia vinculada. No confundir “automatizado” con “validado en la VM”.
