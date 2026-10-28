# Plan de acción: pedidos simultáneos, dirección y desempeño

Fecha: 28/10/2026. Rama de trabajo: `Axel-avance`.

> Registro del plan inicial. Tras la revisión visual, Axel pidió retirar las flechas añadidas; se conserva la orientación existente de los pictogramas. Las referencias a flechas más abajo describen el alcance inicial y ya no son criterios de aceptación vigentes. El estado final y sus límites están en `CAMBIOS_PEDIDOS_SIMULTANEOS_FLECHAS.md`; los informes de presentación están en `sem11/`. Los cambios se publican mediante PR a main, sin afirmar despliegue ni mediciones VM.

## 1. Punto de partida

Se incorporó `origin/main` hasta `173c2fb` mediante avance directo sobre `Axel-avance`, sin conflictos. El remoto de Axel partía de `c7ae6ac`. Esta integración es local; todavía no se ha enviado al remoto ni desplegado.

El nuevo main ya incluye orientación del dibujo de vehículos, inventario por fotograma, cambios de vistas y mejoras de trazos. Se conservarán esas mejoras. Este plan no afirma que los dos cambios funcionales solicitados estén implementados.

En `App.jsx`, las rutas visibles proceden del plan seleccionado para el instante; los indicadores todavía recorren `ejecucion.rutas`, que puede ser una instantánea posterior. En `Mapa.jsx`, los destinos se obtienen de `rutas.flatMap(ruta.paradas)`. Esa estructura admite varias rutas, pero por sí sola no garantiza conservar todos los pedidos activos entre cortes/replanificaciones. Hay que reproducir el problema y comprobar los datos antes de atribuirlo solo al dibujo.

## 2. Resultado solicitado

- A un instante T deben verse todos los pedidos recibidos y pendientes o en atención en T, sus rutas activas y sus vehículos, aunque pertenezcan a rutas distintas o compartan destino.
- No se debe mostrar un pedido futuro como recibido ni una entrega futura como completada. Los entregados históricos pueden consultarse aparte; no deben saturar por defecto la vista operativa.
- Seleccionar un pedido o ruta resalta su recorrido sin ocultar los demás salvo filtro explícito.
- Cada vehículo que se mueve tiene una flecha que señala el siguiente tramo real del recorrido: norte, sur, este u oeste. El vehículo parado se distingue de uno en movimiento; la última orientación puede conservarse, pero no debe aparentar avance.
- El equipo debe demostrar el desempeño funcional y computacional de cinco días y operación diaria, y sustentar aportes individuales y coordinación con evidencia.

## 3. Secuencia de ejecución y aceptación

| Orden | Trabajo | Archivos/componentes previstos | Condición de aceptación |
| --- | --- | --- | --- |
| 1 | Reproducir y registrar el problema con tres pedidos de ventanas superpuestas, uno de llegada posterior y dos con igual destino. Comparar JSON, reloj, rutas e interfaz. | API `/planes`, `/fotogramas`, estado de ejecución; banco local | Se identifica en qué paso desaparece o se omite un pedido: motor, publicación, selección temporal, agrupación o filtro. |
| 2 | Construir una única colección de pedidos visibles a T, con ID original/fracción, recepción, estado, ruta y unidad. Conservar los que siguen activos aunque salgan de un plan posterior; deduplicar por identidad, no por coordenada. Pedir estado explícito al backend si el historial no aporta información suficiente. | `App.jsx`, `mapa.js`, `ApiModels`/`EjecucionesService` si hace falta | En un caso de tres pedidos activos aparecen los tres; el futuro no aparece antes de su recepción; ninguna fracción se pierde ni duplica. |
| 3 | Dibujar todas las rutas y destinos concurrentes, con agrupación desplegable cuando coincidan coordenadas. Alinear contadores, panel y mapa con la colección temporal. | `Mapa.jsx`, panel de rutas/pedidos, estilos | Dos o más vehículos/rutas se ven simultáneamente. El contador coincide con IDs visibles y la selección no convierte la vista en secuencial de un pedido por vez. |
| 4 | Añadir flecha SVG por vehículo usando `orientacionEnTrayecto` y el camino ortogonal; distinguir ángulo del movimiento y corrección visual del pictograma cenital. Para grupos en movimiento, no asignar a todos la dirección del primer vehículo. | `Mapa.jsx`, `mapa.js`, estilos; `DibujoVehiculo.jsx` si conviene | Casos norte/sur/este/oeste y giro de esquina correctos; espera/avería sin falsa flecha de avance; símbolos legibles en zoom y móvil. |
| 5 | Verificar regresión temporal y de carga. Probar cambio de ventana, replanificación y avería con pedidos simultáneos. | Pruebas de mapa/trazos/reloj y pruebas API necesarias | No desaparecen pedidos aún activos; rutas y flechas no anticipan cambios; no se restaura inventario o carga de otro instante. |
| 6 | Ejecutar demo local de cinco días y operación diaria y preparar medición en VM con el responsable del despliegue. | Protocolo de rendimiento y CPA | Evidencias con commit, datos, UUID, métricas y estado. No se sustituyen resultados VM por cifras locales. |
| 7 | Consolidar entrega y explicación del equipo. | Documentación, CPA, paquete integrado y guion | Código, documentos y demostración identifican la misma versión y describen límites/pendientes reales. |

Se empieza por los casos controlados, no por aumentar artificialmente la velocidad o el número de marcadores. La concurrencia debe existir en los datos y la planificación; el frontend no inventa rutas que el motor no generó.

## 4. Pruebas concretas de los cambios

1. **Concurrencia:** P1/P2/P3 registrados antes de T, repartidos entre al menos dos rutas; todos pendientes o en atención. Comparar los tres IDs en API, panel y mapa.
2. **Llegada:** P4 recibido en T+10; ausente a T y presente al alcanzar su recepción.
3. **Coincidencia:** dos pedidos distintos con destino idéntico; grupo/contador permite acceder a ambos, sin desplazar sus coordenadas de negocio.
4. **Cambio de plan:** vehículo que ya salió sigue atendiendo un pedido al publicarse otro plan; conservar su representación mientras siga activo.
5. **Parciales:** un original con dos fracciones y unidades diferentes; mostrar asignaciones y cantidad pendiente sin contar dos originales.
6. **Dirección:** cuatro tramos cardinales, giro de 90 grados, espera de acondicionamiento y avería; flecha coherente con el movimiento real.
7. **Filtros:** seleccionar ruta/pedido resalta sin suprimir los otros; un filtro explícito sí reduce la vista y lo indica.
8. **Dos visualizadores:** misma corrida, distintas selecciones/zoom; ambos muestran los mismos pedidos para el mismo reloj con interacción local independiente.

Ejecutar `npm test --prefix frontend` y `npm run build --prefix frontend` después de los cambios. Si se modifica el contrato/motor, ejecutar además las pruebas Java afectadas. La demo visual complementa, no reemplaza, las verificaciones de identidad/instante.

## 5. Desempeño del software requerido por el cronograma

El cronograma sitúa en la entrega integrada de semana 11 la presentación del desempeño individual/en equipo y del software en simulación de periodo y operaciones día a día. También pide el ZIP de `sol.integrada.sem11`. Se confirmará la semana efectiva y el nombre final con el calendario del curso, sin renombrar entregas por la fecha del computador.

| Escenario | Qué demostrar | Qué medir y conservar |
| --- | --- | --- |
| Cinco días | Consumo de datos oficiales de cinco días; varios pedidos/rutas simultáneos; incidencias; resumen final | Duración real completa entre 30 y 60 min, Ta por llamada, cómputo por ventana, RSS máximo/heap definido, pedidos/productos recibidos/entregados/en plazo/pendientes, costo, distancia y retrasos. |
| Operación diaria | Operación a tiempo real según alcance; pedidos que llegan con el reloj; concurrencia y atención a averías | Medir avance en un intervalo conocido, arranque/primer movimiento, Ta, memoria, consistencia de entregas y datos visibles. Una demo acelerada no acredita operación 1x. |

Separar duración de presentación y tiempo de planificación: los 40 minutos configurados para cinco días incluyen esperas intencionales. Medir el tiempo real final sigue siendo necesario para verificar la banda, pero no equivale al costo computacional. No afirmar cumplimiento de 30–60 min a partir de la configuración solamente.

No añadir controles para acelerar, avanzar o retroceder en la presentación evaluada: el cronograma los prohíbe. El historial usado para depuración debe mantenerse separado de los controles operativos de la demo del curso. Tampoco se cambia el servidor sin coordinación con el encargado.

## 6. Desempeño individual y en equipo

Reparto propuesto según compromisos previos, sujeto al trabajo que cada integrante realmente ejecute:

| Persona | Evidencia técnica a preparar | Evidencia de gestión |
| --- | --- | --- |
| Axel | Integración de rama, contrato/datos temporales, pruebas de concurrencia y API, medición de desempeño y trazabilidad CPA | Compromiso, actividad realizada, revisión recibida, horas reales propias y pendientes. |
| Ariana | Mapa, flechas, concurrencia visual, filtros y prueba de interfaz | Aportes propios, decisiones visuales, coordinación con API y correcciones realizadas. |
| Melvin | Revisión del planificador, persistencia de pedidos activos entre planes y coordinación técnica de VM | Revisión de integración, resolución de incidencias y evidencia de despliegue autorizada. |
| Leslie | Conjuntos oficiales, consolidación de evidencias y paquete de entrega | Seguimiento de tareas, dependencias, revisión documental y consolidación. |

Para cada integrante preparar: tarea comprometida → código/documento aportado → prueba o resultado → problema y decisión → pendiente. Usar commits/PR y casos CPA verificables; no atribuir todo un merge al autor que lo integró ni inventar horas.

Como equipo presentar un tablero de tareas con completado/pendiente, una dependencia resuelta entre frontend/backend, defectos encontrados y estado, actualización de RHA/CGR y evaluación de adhesión de semana 11 cuando corresponda. No declarar certificación ISO; mostrar artefactos y actividades realizados frente al proceso evaluado.

## 7. Entregables y cierre

- Código de pedidos simultáneos y flechas en `Axel-avance`, con pruebas y capturas.
- Registro de cambios: problema, solución, archivos, commit y limitaciones.
- CPA: casos anteriores con esperado/obtenido, UUID, fecha, entorno y evidencia.
- Tabla de desempeño local y VM claramente separadas; corridas no ejecutadas figuran pendientes.
- Resumen de aportes individuales y del equipo, sustentado en evidencias.
- ZIP integrado de la semana correspondiente e instrucciones desde cero; sin `.env`, credenciales, dependencias ni artefactos innecesarios.

Antes de presentar: validar en la versión integrada que tres o más pedidos activos aparezcan a la vez, que ninguna selección oculte el resto sin filtro, que la flecha coincida con cada tramo y que la exposición use los resultados de esa misma versión.

## 8. Registro de esta preparación

- Integración local de main completada en `Axel-avance`, HEAD `173c2fb`.
- Al cambiar desde la rama anterior se detectaron cambios de integración en el índice; se conservaron en un stash denominado `resguardo previo a integrar main en Axel-avance`. No se aplican a la rama nueva porque contiene versiones más recientes de esos archivos.
- Este documento prepara la ejecución. Las funcionalidades nuevas, sus pruebas, el push y el despliegue quedan diferenciados del merge ya realizado.
