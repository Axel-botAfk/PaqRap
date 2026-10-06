# Guia de implementacion del backend PaqRap (semana 8)

## Objetivo y alcance

Conectar el prototipo `PaqRap Prototipo v6.html` con la logica Java existente para
mostrar, desde la web, la operacion diaria, la simulacion de cinco dias y el
colapso logistico. El backend no debe inventar rutas, pedidos ni incidencias:
todos los datos presentados como reales deben proceder de los archivos del
curso y de la ejecucion del `Planificador` y `Simulador`.

Esta guia es el contrato de trabajo inicial. El HTML v6 es una referencia
visual, no un cliente API terminado. Cuando exista un frontend ejecutable, se
verificara el contrato con Ariana antes de cambiar nombres o estructuras.

## Base de codigo y decisiones

- Rama de trabajo: `codex/backend-sem08`, nacida del `main` observado el
  2026-10-06. No trabajar directamente sobre `main`.
- Reutilizar `codigo/paqrap` de `codex/semana7-vecino-cercano-diseno` como
  modulo de dominio. La busqueda tabu arranca de
  `ConstructorVecinoMasCercano`; GRASP permanece independiente.
- Java 17 como objetivo de compilacion, Maven y Spring Boot 3.5.x para la API.
- Persistencia inicial: archivos del curso y estado de ejecuciones en memoria.
  No se introduce una base de datos ni autenticacion porque no son necesarios
  para la entrega de semana 8. Documentar que un reinicio pierde ejecuciones.
- nginx sirve el frontend y reenvia `/api/` y `/ws/` al proceso local en
  `127.0.0.1:8081`, segun `deploy/`.

## Modulos y responsabilidades

1. **Dominio (`codigo/paqrap`)**: modelos, datos, GRASP, vecino cercano,
   busqueda tabu y simulador; sin dependencias de Spring ni de la GUI.
2. **API (`backend`)**: validacion HTTP, DTO, errores, ejecuciones asincronas,
   conversion de eventos, REST y WebSocket.
3. **Visualizador (`frontend`)**: mapa, semaforos, controles y mensajes; no
   decide rutas ni recalcula resultados del algoritmo.

El `Observador` actual notifica cada planificacion, pero un plan describe el
futuro. Para la GUI tambien se necesita un estado ejecutado y acumulado por
salto; nunca marcar una entrega como realizada solo porque esta en una ruta.

## Contrato HTTP y eventos (version inicial)

| Operacion | Respuesta o uso |
| --- | --- |
| `GET /api/salud` | Estado del proceso, sin exponer secretos. |
| `GET /api/datos/periodos` | Periodos con archivos de ventas disponibles. |
| `GET /api/datos/periodos/{aaaamm}` | Conteos y disponibilidad de ventas, bloqueos y mantenimiento. |
| `POST /api/ejecuciones` | Valida escenario, algoritmo, fecha/hora y horizonte; devuelve `202` e ID. |
| `GET /api/ejecuciones/{id}` | Ultima instantanea y estado (`PENDIENTE`, `EN_CURSO`, `TERMINADA`, `COLAPSADA`, `FALLIDA`, `CANCELADA`). |
| `POST /api/ejecuciones/{id}/cancelar` | Pide detener una corrida identificada por UUID. Sin autenticacion, el UUID no equivale a autorizacion. |
| `WS /ws/ejecuciones/{id}` | Instantanea inicial y eventos de plan, progreso, incidencia y cierre. |

Tipos de escenario: `OPERACION_DIARIA`, `PERIODO_5_DIAS`, `COLAPSO`.
Algoritmos expuestos: `GRASP` y `TABU`. El backend devuelve IDs, coordenadas,
estado textual, fechas ISO-8601, unidades, rutas, pedidos, metricas y motivos
estructurados de rechazo/no asignacion. Los colores del semaforo pertenecen al
frontend y siempre deben acompanarse de texto. La conexion WebSocket puede
interrumpirse: `GET /api/ejecuciones/{id}` permite recuperar la instantanea.

## Seguridad minima obligatoria

- No subir `.env`, claves, tokens, contrasenas, llaves SSH ni datos personales
  adicionales. Mantener solo ejemplos sin secretos y variables de entorno.
- Validar todos los campos de entrada (tipos, rangos, fechas, IDs y longitudes).
  Responder `400` con un codigo de error estable; no devolver trazas Java.
- Los clientes eligen un periodo permitido, no una ruta de archivo arbitraria.
  Resolver archivos exclusivamente dentro de la carpeta de datos configurada,
  evitando traversal y lecturas fuera de ella.
- Limitar tamano de peticiones, concurrencia y tiempo de vida de las
  ejecuciones. No permitir que una llamada HTTP bloquee el hilo hasta terminar
  una simulacion pesada.
- No habilitar CORS comodin. Usar rutas relativas bajo el mismo origen de
  nginx; configurar explicitamente el origen permitido para WebSocket.
- No incluir SQL en esta fase. Si posteriormente se agrega una base de datos,
  usar consultas parametrizadas (`PreparedStatement`, JPA o repositorios) y
  nunca concatenar valores del usuario en SQL. Probar un valor como
  `' OR 1=1 --` para verificar que se trata como dato, no como instruccion.
- No presentar una autenticacion ficticia como control de seguridad. El API
  actual es para demostracion controlada: cualquier persona con el UUID puede
  leer y cancelar la ejecucion. Antes de exponerlo publicamente se requiere
  autenticar/autorizar o restringir la red, y limitar por cliente la creacion
  de ejecuciones. Los cupos globales no sustituyen esas medidas.

## Orden de implementacion y criterio de terminado

1. Importar el modulo Java vigente sin cambiar la semantica de los algoritmos.
2. Compilar y ejecutar pruebas existentes; fijar versiones y reproducibilidad.
3. Entregar salud y catalogo de periodos a partir de archivos reales.
4. Ejecutar asincronamente la simulacion de cinco dias y obtener una
   instantanea final por REST.
5. Emitir progreso y resultados por WebSocket; permitir que otro dispositivo
   se conecte tarde y reciba la instantanea actual.
6. Cubrir operacion diaria y colapso con el mismo motor; agregar cancelacion.
7. Integrar la GUI: eliminar datos simulados de las vistas presentadas como
   reales, conectar controles, mapa, indicadores y mensajes de error.
8. Probar entradas invalidas, archivos ausentes, plan rechazado, desconexion,
   dos dispositivos simultaneos y los tres escenarios de punta a punta.
9. Construir ZIP reproducible con instrucciones de arranque desde cero. No
   declarar la entrega completa solo por tener controladores o pantallas.

## Fuera de alcance de esta entrega

Base de datos, usuarios y login real, pagos, administracion remota y
persistencia historica de ejecuciones. Se agregaran solo si la lista de
exigencias final o el profesor los requiere de forma explicita.
