# Averías en marcha e inventario dinámico — beta local

## Alcance implementado

- `POST /api/ejecuciones/{id}/averias` acepta `{"unidadId":"TA01","tipo":"TIPO_3"}` únicamente mientras la corrida está `EN_CURSO`. Valida que la unidad pertenezca a la flota y evita registrar dos averías para el mismo vehículo en una corrida.
- El simulador recibe la avería concurrentemente y la aplica en su siguiente corte. Su lógica existente retira la unidad temporalmente y vuelve a planificar con las unidades disponibles. `GET /api/ejecuciones/{id}/averias` permite observar `REGISTRADA`, `APLICADA` o `NO_APLICADA`.
- `GET /api/ejecuciones/{id}/inventario` devuelve `{reloj, almacenes}`. Cada intermedio presenta `disponible` después del último salto; el central usa `ilimitado: true` y `disponible: null`, no un cero ficticio. El stock del mapa sin ejecución sigue siendo inicial.
- La GUI permite elegir vehículo y tipo de avería, muestra el resultado en Alertas y muestra el stock más reciente de los almacenes. No afirma que el stock corresponda a un fotograma histórico mientras se reproduce una corrida terminada.
- El campo opcional `pausaMs` al iniciar una ejecución (0–500) ralentiza cada salto para hacer posible la interacción humana. El interruptor de la GUI usa 100 ms. **No usar esa opción para medir rendimiento.**

## Prueba local

1. Arrancar Docker y seguir `database/DOCKER_LOCAL.md` para iniciar el backend con perfil MySQL. En este equipo, el puerto 8080 estaba ocupado por otro servicio, por lo que se usó `PAQRAP_PORT=8082` y `PAQRAP_API_TARGET=http://127.0.0.1:8082` para Vite en 5174.
2. Abrir `http://127.0.0.1:5174/`, elegir periodo `202901`, fecha `2029-01-15`, operación diaria, GRASP y activar **Modo interactivo**.
3. Iniciar; mientras indique `EN_CURSO`, seleccionar `TA01` y tipo 3, y pulsar **Registrar avería**. Revisar Alertas, vehículos, rutas e inventario.
4. Para comprobar por API, consultar `/api/ejecuciones/{id}/averias`, `/api/ejecuciones/{id}/inventario` y `/api/ejecuciones/{id}`. Una avería `APLICADA` demuestra que el motor la consumió; para evaluar la reasignación, comparar las rutas y pedidos antes y después del evento, no solo el estado del incidente.

## Evidencia ejecutada el 18/10/2026

- Docker MySQL saludable: 3 almacenes, 37 vehículos y 160 pedidos en la base local.
- `/api/salud` por el proxy Vite: `OK/MYSQL`; periodos `202609`, `202901`, `202902`, `202903`.
- Corrida local de `202901` con GRASP, semilla 7 y pausa de demostración: ID `77d9efd6-db14-4522-a9e7-52cbc92c8583`; avería `TA01/TIPO_3` recibida como `REGISTRADA` y consultada al finalizar como `APLICADA`; corrida `TERMINADA`; inventario con 3 almacenes y reloj final `2029-01-16T00:00:00`.
- Prueba dirigida local: corrida `7b623ce3-74d7-4453-9547-801f50986dcc`. El pedido `4-D-001` estaba en una ruta de `TA01`; tras registrar `TIPO_3` para `TA01`, apareció en una ruta de `TA06` a las `2029-01-15T08:15:00`. Esto demuestra reasignación observada, no solo registro del incidente.
- Cinco días `202902`, GRASP, semilla 7: `TERMINADA`, 75 pedidos recibidos, reloj final `2029-02-06T00:00:00` (ID `94819a26-90dd-4254-863b-b3b13367e720`). Colapso `202903`, TABU, horizonte 1, semilla 7: `COLAPSADA`, 35 pedidos recibidos, reloj final `2029-03-01T09:15:00` (ID `03d6d05a-cc4f-413f-96ef-e766e2ee76e4`).
- Maven: 18 pruebas del motor y 13 del backend aprobadas. Prueba de conexión MySQL aprobada. Frontend: 5 pruebas aprobadas y compilación Vite correcta.

La primera corrida demuestra registro y aplicación; la prueba dirigida demuestra el cambio de unidad de un pedido concreto en otra corrida. Las corridas e incidentes están en memoria y se pierden al reiniciar el backend.

## Seguridad y límites

La beta no tiene autenticación: quien acceda a la API y conozca el ID puede registrar averías o cancelar una corrida. Mantener el despliegue bajo control de acceso de nginx/HTTPS hasta implementar autorización por usuario y límites de solicitudes. El nuevo endpoint no ejecuta SQL ni acepta nombres de tablas o archivos. No exponer credenciales en la GUI o en Git.
