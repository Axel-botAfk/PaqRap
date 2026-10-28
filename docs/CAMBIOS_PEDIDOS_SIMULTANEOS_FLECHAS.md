# Pedidos simultáneos y flechas de dirección

Fecha: 28/10/2026. Rama: Axel-avance; base main 173c2fb.

## Cambio implementado

Los indicadores y la hoja de pedidos consultaban la instantánea más reciente de la ejecución, mientras el mapa seleccionaba el plan del reloj visible. Al cambiar el plan, los pedidos anteriores podían desaparecer de esas consultas y los contadores podían anticipar datos.

Se añadió `pedidosEnInstante()` en `frontend/src/pedidosEnVivo.js`: reúne las asignaciones y los no asignados publicados hasta el instante elegido, conserva cada ID/fracción y respeta su fecha de recepción. Las asignaciones posteriores sustituyen a las anteriores para el mismo ID. La colección alimenta contadores y tabla; el mapa conserva destinos de pedidos activos que ya no están en el plan mostrado. No reconstruye rutas descartadas ni inventa recorridos ausentes del backend.

Las rutas actuales se siguen dibujando simultáneamente. La selección resalta una ruta sin filtrar las otras. Los destinos compartidos muestran un contador y una ficha con los IDs coincidentes; no se alteran sus coordenadas reales. Los entregados de planes anteriores se conservan en la tabla y aparecen como destino adicional solo al filtrar Entregados.

Por preferencia del usuario se retiraron las flechas superpuestas a los vehículos. Se conserva la orientación existente de los pictogramas y la representación de pedidos simultáneos.

## Archivos

- `frontend/src/pedidosEnVivo.js`: colección temporal.
- `frontend/src/pedidosEnVivo.test.js`: concurrencia, recepción, reasignación, fracciones y direcciones.
- `frontend/src/mapa.js`: omite estados de planes futuros.
- `frontend/src/App.jsx`: usa colección temporal y filtra destinos aún no recibidos.
- `frontend/src/components/Mapa.jsx`: destinos activos adicionales y coincidencias.
- `frontend/src/pages/HojaPedidos.jsx`: tabla y métricas del instante, etiquetas actualizadas.

## Verificación realizada

- `npm test --prefix frontend`: 64 pruebas satisfactorias, incluidas 5 nuevas; se retiraron las dos pruebas exclusivas de las flechas eliminadas.
- `npm run build --prefix frontend`: compilación de producción satisfactoria.
- `git diff --check`: sin errores de espacios.

No se modificó el algoritmo, la base de datos ni el servidor desplegado. No se han medido todavía memoria o planificación en la VM ni completado una corrida de cinco días con esta versión. Estas verificaciones no sustituyen la aceptación visual en dos dispositivos.

## CPA: casos añadidos para aceptación visual

| Caso | Procedimiento | Resultado esperado | Estado |
| --- | --- | --- | --- |
| CPA-VIVO-01 | Preparar 3 pedidos recibidos, 2 unidades y rutas concurrentes; avanzar a un instante común. | Todos los IDs activos visibles y tabla/contadores coincidentes; selección resalta sin ocultar otros. | Lógica automatizada aprobada; demo integrada pendiente. |
| CPA-VIVO-02 | Replanificar quitando una asignación de un pedido todavía activo del plan actual. | Su destino y registro no desaparecen del historial conocido; no se inventa una nueva ruta. | Lógica automatizada aprobada; demo integrada pendiente. |
| CPA-VIVO-03 | Dos pedidos con destino idéntico y otro de recepción futura. | Contador de coincidencia permite identificar ambos; el futuro no aparece antes de recepción. | Recepción automatizada aprobada; ficha visual pendiente. |
| CPA-VIVO-04 | Observar vehículos en ruta con giros y esperas. | Pictogramas mantienen su orientación existente, sin flechas añadidas. | Inspección visual pendiente. |

Cada ejecución visual debe guardar commit, entorno, UUID, reloj de observación, IDs, captura y resultado obtenido. No marcar aprobada toda la prueba E2E por la existencia del test unitario.

## Límites y siguientes pasos

La colección depende del historial que publica el servidor y que ya descargó el cliente. Si ese historial se trunca, no se garantiza cobertura de toda la corrida. No hay cancelación operativa de pedidos en la API actual; incorporar cancelaciones exige publicar el evento/estado explícito. La tabla identifica fracciones; sus contadores no equivalen automáticamente a originales completos. Las métricas globales oficiales se conservan en el resumen del backend.

El resto de preparación del desempeño individual/equipo, mediciones de software y paquete integrado está especificado en `PLAN_ACCION_PEDIDOS_EN_VIVO_SEM11.md`.
