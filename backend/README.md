# Backend de PaqRap

API REST y WebSocket para ejecutar el simulador Java con GRASP o Busqueda Tabu
iniciada con Vecino Mas Cercano. Lee los archivos de `codigo/paqrap/datos/reales`.

## Requisitos y arranque

- JDK 17 o superior y Maven 3.9 o superior.
- Desde la raiz del repositorio: `mvn clean package`.
- Desde la raiz: `java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar`.
- Por defecto escucha solo en `127.0.0.1:8080`. `GET /api/salud` debe
  responder `{"estado":"OK"}`.
- Si se arranca fuera de la raiz, configurar `PAQRAP_DATA_DIR` con la ruta
  absoluta a `codigo/paqrap/datos/reales`. `PAQRAP_PORT` y
  `PAQRAP_BIND_ADDRESS` son opcionales.

No se necesita `.env` ni base de datos. Las ejecuciones se mantienen en
memoria y se pierden al reiniciar el proceso.

Para usar MySQL como fuente de pedidos, bloqueos, mantenimiento, almacenes y
vehículos, consulta [`database/README.md`](../database/README.md). La conexión
se activa con `-Pmysql` al compilar y `--spring.profiles.active=mysql` al
ejecutar. `db.properties` externo contiene la contraseña cifrada y
`PAQRAP_DB_KEY` contiene la clave de descifrado. Las ejecuciones y sus
resultados **todavía no** se persisten.

## Contrato con la GUI

| Metodo y ruta | Descripcion |
| --- | --- |
| `GET /api/salud` | Salud basica. |
| `GET /api/datos/periodos` | Lista de periodos `aaaamm` disponibles. |
| `GET /api/datos/periodos/{aaaamm}` | Conteos del periodo. |
| `GET /api/datos/mapa/{aaaamm}` | Almacenes y bloqueos del periodo para la GUI. |
| `POST /api/ejecuciones` | Inicia una corrida; devuelve 202 e identificador. |
| `GET /api/ejecuciones/{id}` | Estado e instantanea mas reciente. |
| `GET /api/ejecuciones/{id}/fotogramas?desde=N` | Posiciones ejecutadas al cierre de cada salto, en páginas de hasta 100 fotogramas. |
| `GET /api/ejecuciones/{id}/planes?desde=N` | Versiones históricas del plan, paginadas, para sincronizar rutas con el reloj reproducido. |
| `GET /api/ejecuciones/{id}/inventario` | Existencias actuales de los almacenes al reloj publicado. |
| `GET /api/ejecuciones/{id}/averias` | Averías registradas y su estado. |
| `POST /api/ejecuciones/{id}/averias` | Registra una avería durante una corrida activa. |
| `POST /api/ejecuciones/{id}/cancelar` | Detiene la corrida (estado `CANCELADA`). |
| `POST /api/ejecuciones/{id}/pausar` | Congela el ritmo; estado `PAUSADA`. |
| `POST /api/ejecuciones/{id}/reanudar` | Retoma la ventana donde quedó; estado `EN_CURSO`. |
| `GET /api/ejecuciones/{id}/eventos?desde=N` | Hechos de la operación: `AVERIA_REGISTRADA`, `AVERIA_APLICADA`, `REPLANIFICACION` (pedido, unidad anterior y nueva o por planificar) y `COLAPSO`. |
| `WS /ws/ejecuciones/{id}` | Instantanea inicial y eventos posteriores. |

Ejemplo de cuerpo para operacion diaria:

```json
{"escenario":"OPERACION_DIARIA","algoritmo":"GRASP","inicio":"2026-09-01T00:00:00","semilla":20260901}
```

Para cinco dias usar `PERIODO_5_DIAS`; para colapso usar `COLAPSO` y,
opcionalmente, `horizonteDias` entre 1 y 60. El segundo algoritmo se llama
`TABU`. El campo `pausaMs` (0 a 500) se sigue aceptando por compatibilidad,
pero ya no hace falta para registrar averías. El cuerpo de avería es
`{"unidadId":"TA01","tipo":"TIPO_3"}`. El stock de `/datos/mapa` es inicial;
`/ejecuciones/{id}/inventario` es dinámico y el central devuelve
`ilimitado=true`, `disponible=null`.

El `inicio` debe caer en un periodo con archivos disponibles; no se
admiten nombres de archivo enviados por el cliente. Los eventos son objetos
`{"tipo":"PROGRESO","ejecucion":{...}}`; `INSTANTANEA`, `INICIADA`,
`CALCULANDO_RUTAS`, `PLAN_SIN_ASIGNACIONES`, `COLAPSO`, `TERMINADA`, `FALLIDA` y `CANCELADA` son
otros tipos posibles. Ante desconexion del WebSocket, consultar el mismo ID
por REST. Las `rutas` son el plan vigente, no entregas ya realizadas; la
cantidad entregada se toma de `avance` o `resumen`.
Durante una replanificación, `calculandoRutas=true` y `rutas=[]` evitan
presentar el plan anterior como si fuera el nuevo. Al publicarse el plan,
el indicador vuelve a `false`.

Los fotogramas registran las posiciones de las unidades **después** de avanzar
el reloj, sin ralentizar el motor ni falsear las mediciones de tiempo. La
respuesta incluye `primero`, `siguiente`, `completo` y `fotogramas`; el cliente
usa `siguiente` para consultar la página posterior. Se conservan como máximo
2048 fotogramas por ejecución en memoria, por lo que una corrida muy larga
puede descartar los más antiguos. El último fotograma alimenta las posiciones
de vehículos de la respuesta final. Como las ejecuciones, este historial se
pierde al reiniciar el backend.

Cada replanificación guarda además un `PlanVista` con `reloj` y todas las
rutas y paradas simultáneas de ese instante. `/planes` se pagina igual que
`/fotogramas` y conserva hasta 2048 versiones por ejecución. El plan sirve
para mostrar lo previsto en ese momento; no equivale a un historial GPS ni
certifica la entrega de cada pedido.

## Ritmo de la simulación

La corrida se publica por ventanas: cada `Sa` segundos reales sale una
ventana de unos `Sc` minutos simulados (`K = Sc / Sa`). El simulador conserva
su propia cadencia de replanificación (latido de 30 min, mínimo de 15 y cortes
por llegadas, bloqueos y averías), así que **el ritmo no cambia ninguna
decisión**: solo decide cuándo se publica. El backend junta las iteraciones
hasta cruzar el siguiente límite de una grilla fija de `Sc`, publica la
ventana entera (planes, recorridos, posiciones e inventario) y espera n × Sa
(n = límites cruzados, más de uno solo a tiempo real) descontando lo que tardó
el cálculo. Así la duración total es N × Sa.

| Escenario | Por defecto | Campos opcionales |
| --- | --- | --- |
| `PERIODO_5_DIAS` | 40 min reales: 120 ventanas de 1 h cada 20 s | `duracionObjetivoMin` (30 a 60) o `minutosPorVentana` + `segundosPorVentana` (el total debe quedar entre 30 y 60 min) |
| `OPERACION_DIARIA` | 60×: 20 min simulados cada 20 s (24 min el día) | `velocidad` 1, 10, 60 o 240 (1 = tiempo real) |
| `COLAPSO` | 2 h simuladas cada 10 s (30 días ≤ 1 h); se corta en el primer pedido vencido | `minutosPorVentana`, `segundosPorVentana` |

`ejecucion.ritmo` trae `saMs`, `minutosPorVentana`, `ventana`/`ventanas`,
`relojDesde`/`relojHasta` de la ventana visible, su `duracionVentanaMs` y su
`k` real, `publicadoMs` (cuándo se publicó) y `servidorAhoraMs`; con eso la
GUI ancla su reloj al tiempo real.
En pausa llega `transcurridoAlPausarMs`. `ejecucion.trayectos` son los hitos
de cada unidad en la ventana (salida, almacén, entrega, fin de entrega y
corte) con su hora; cada `PlanVista` los guarda junto a `hasta`. Si la
corrida colapsa, `ejecucion.colapso` detalla pedido, instante, ventana,
tiempo real y métricas en ese momento. Hasta tres corridas avanzan a la vez.

Las coordenadas son celdas de la grilla del modelo, no latitud/longitud. La
GUI React consume este contrato; el HTML v6 permanece como referencia visual.

## Seguridad y limites

- `.gitignore` excluye `.env`, llaves y artefactos de compilacion. No colocar
  secretos en archivos versionados ni en el frontend.
- Se validan formato de fecha, enums, horizonte, UUID y periodo. Los errores
  de API no devuelven trazas ni rutas del servidor.
- El servidor ejecuta una simulacion por vez y admite dos en cola; las instantaneas
  expiran tras dos horas. El perfil MySQL usa consultas parametrizadas y un
  usuario de solo lectura; no construye SQL con entrada HTTP concatenada.
- El despliegue publico requiere autenticacion/autorizacion o restriccion de
  acceso, ademas de limite por cliente. Actualmente quien conoce un UUID
  puede leer o cancelar su corrida; no usar este backend como servicio publico
  multiusuario sin esos controles.

La guia de decisiones y pruebas pendientes esta en [GUIA_IMPLEMENTACION.md](GUIA_IMPLEMENTACION.md).
