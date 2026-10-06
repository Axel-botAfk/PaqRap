# Backend de PaqRap

API REST y WebSocket para ejecutar el simulador Java con GRASP o Busqueda Tabu
iniciada con Vecino Mas Cercano. Lee los archivos de `codigo/paqrap/datos/reales`.

## Requisitos y arranque

- JDK 17 o superior y Maven 3.9 o superior.
- Desde la raiz del repositorio: `mvn clean package`.
- Desde la raiz: `java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar`.
- Por defecto escucha solo en `127.0.0.1:8081`. `GET /api/salud` debe
  responder `{"estado":"OK"}`.
- Si se arranca fuera de la raiz, configurar `PAQRAP_DATA_DIR` con la ruta
  absoluta a `codigo/paqrap/datos/reales`. `PAQRAP_PORT` y
  `PAQRAP_BIND_ADDRESS` son opcionales.

No se necesita `.env` ni base de datos. Las ejecuciones se mantienen en
memoria y se pierden al reiniciar el proceso.

## Contrato con la GUI

| Metodo y ruta | Descripcion |
| --- | --- |
| `GET /api/salud` | Salud basica. |
| `GET /api/datos/periodos` | Lista de periodos `aaaamm` disponibles. |
| `GET /api/datos/periodos/{aaaamm}` | Conteos del periodo. |
| `POST /api/ejecuciones` | Inicia una corrida; devuelve 202 e identificador. |
| `GET /api/ejecuciones/{id}` | Estado e instantanea mas reciente. |
| `POST /api/ejecuciones/{id}/cancelar` | Cancela la corrida. |
| `WS /ws/ejecuciones/{id}` | Instantanea inicial y eventos posteriores. |

Ejemplo de cuerpo para operacion diaria:

```json
{"escenario":"OPERACION_DIARIA","algoritmo":"GRASP","inicio":"2026-09-01T00:00:00","semilla":20260901}
```

Para cinco dias usar `PERIODO_5_DIAS`; para colapso usar `COLAPSO` y,
opcionalmente, `horizonteDias` entre 1 y 60. El segundo algoritmo se llama
`TABU`. El `inicio` debe caer en un periodo con archivos disponibles; no se
admiten nombres de archivo enviados por el cliente. Los eventos son objetos
`{"tipo":"PROGRESO","ejecucion":{...}}`; `INSTANTANEA`, `INICIADA`,
`PLAN_SIN_ASIGNACIONES`, `COLAPSO`, `TERMINADA`, `FALLIDA` y `CANCELADA` son
otros tipos posibles. Ante desconexion del WebSocket, consultar el mismo ID
por REST. Las `rutas` son el plan vigente, no entregas ya realizadas; la
cantidad entregada se toma de `avance` o `resumen`.

Las coordenadas son celdas de la grilla del modelo, no latitud/longitud. La
GUI debe distinguir pedido planificado, pendiente y entregado, y acompanar
todo color de semaforo con texto. El HTML v6 es un prototipo visual estatico;
aun falta conectar sus controles y vistas a este contrato.

## Seguridad y limites

- `.gitignore` excluye `.env`, llaves y artefactos de compilacion. No colocar
  secretos en archivos versionados ni en el frontend.
- Se validan formato de fecha, enums, horizonte, UUID y periodo. Los errores
  de API no devuelven trazas ni rutas del servidor.
- El servidor ejecuta una simulacion por vez y admite dos en cola; las instantaneas
  expiran tras dos horas. No hay SQL en esta entrega: la proteccion frente a
  inyeccion SQL es no construir consultas; si se agrega BD, usar parametros
  enlazados, nunca concatenacion de valores de entrada.
- El despliegue publico requiere autenticacion/autorizacion o restriccion de
  acceso, ademas de limite por cliente. Actualmente quien conoce un UUID
  puede leer o cancelar su corrida; no usar este backend como servicio publico
  multiusuario sin esos controles.

La guia de decisiones y pruebas pendientes esta en [GUIA_IMPLEMENTACION.md](GUIA_IMPLEMENTACION.md).
