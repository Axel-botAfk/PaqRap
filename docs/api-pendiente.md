# API pendiente

Lo que la interfaz necesita y la API todavía no ofrece. Cada punto tiene una pantalla que
ya existe en el frontend y que hoy muestra un aviso «Pendiente» en lugar de inventar datos.

Mientras no existan, la interfaz no se rompe: degrada a un estado declarado.

Los puntos 1 y 4 ya están implementados y se conservan marcados como resueltos, con el
endpoint que los cubre, para no volver a levantarlos como faltantes.

## 1. Stock por almacén, en vivo — RESUELTO

**Para:** panel «Inventario de almacenes» en Operación diaria.

Lo cubre `GET /api/ejecuciones/{id}/inventario` (`ApiController`), que devuelve
`InventarioVista`: el reloj del corte y, por almacén, `disponible` e `ilimitado`. El frontend
lo consume en `services/api.js` y elige el corte que corresponde al instante mostrado en
`stock.js`, sin adelantar un inventario futuro.

```
GET /api/ejecuciones/{id}/inventario
-> 200 { "reloj": "2029-01-15T10:40:00",
         "almacenes": [ { "id", "tipo", "ubicacion", "disponible", "ilimitado" } ] }
```

El inventario publica también la `capacidad` de cada almacén intermedio; el llenado se mide
contra ella y el `stockInicial` queda como referencia para servidores anteriores.

## 2. Listado de ejecuciones

**Para:** que alguien que abre la aplicación sin el enlace pueda llegar a una corrida.

Hoy una ejecución solo existe en su URL (`?ejecucion=<uuid>`). Si en la exposición la corrida
se lanza en una máquina y el profesor abre la página en otro dispositivo, no ve nada salvo que
le pasen el enlace exacto. `EjecucionesService` ya mantiene el mapa en memoria y lo limpia a
las dos horas, así que el dato existe.

```
GET /api/ejecuciones
-> 200 [ { "id", "escenario", "algoritmo", "estado", "inicio", "reloj", "actualizadoEn" } ]
```

## 3. Carga de archivos del mes

**Para:** la pestaña «Carga masiva del mes» en Pedidos.
**Alcance acordado:** ventas y calles bloqueadas. Mantenimiento no por ahora.

La validación de formato va en el servidor, que es quien conoce `LectorVentas` y
`LectorBloqueos`. El cliente solo comprueba el nombre antes de subir.

```
POST /api/datos/archivos        (multipart: tipo=VENTAS|BLOQUEOS, archivo)
-> 200 { "periodo": "202608", "registros": 6240, "descartados": 4,
         "avisos": ["4 registros con cantidad 0: se descartan por validación"] }
-> 400 { "codigo": "ARCHIVO_INVALIDO", "mensaje": "..." }
```

Ojo con dos cosas al implementarlo: `client_max_body_size` en
[deploy/nginx/paqrap.conf](../deploy/nginx/paqrap.conf) está en **16k**, insuficiente para un
archivo mensual de ventas; y el nombre del archivo no debe usarse para construir una ruta en
disco a partir de lo que mande el cliente.

## 4. Registro de avería — RESUELTO

**Para:** la sección «Registrar una avería» en Averías de la flota.

Lo cubren `POST /api/ejecuciones/{id}/averias`, que acepta la avería solo en una corrida
`EN_CURSO`, y `GET /api/ejecuciones/{id}/averias`, que lista las registradas con su estado y
la cantidad de pedidos replanificados.

```
POST /api/ejecuciones/{id}/averias
     { "unidadId": "TA03", "tipo": "TIPO_1" | "TIPO_2" | "TIPO_3" }
-> 201 { "unidadId", "tipo", "instante", "estado", "pedidosAfectados" }
```

El motor distingue los tres tipos y calcula la ventana de indisponibilidad con los turnos
oficiales (07:00, 15:00, 23:00). Queda por confirmar contra el motor la permanencia de 4 h en
el lugar de los tipos 2 y 3, que el mapa ya usa para retirar la marca cuando vence.

## 5. Seguimiento de un pedido con entregas parciales

**Para:** la vista «Seguimiento del pedido» del prototipo.
**Acordado:** no entra en este avance.

`PedidoVista` describe un pedido dentro de una ruta. Falta la vista contraria: dado un pedido,
sus fracciones repartidas entre unidades y viajes, que el motor ya modela en `RepartoParcial`.
Mientras tanto, «Seguimiento del pedido» la reconstruye en el cliente recorriendo el historial
de planes; por eso depende de los planes que ya se descargaron y no de un endpoint propio.

```
GET /api/ejecuciones/{id}/pedidos/{pedidoId}
-> 200 { "id", "cantidad", "entregado", "pendiente",
         "fracciones": [ { "orden": 1, "de": 2, "vehiculoId", "cantidad",
                           "almacenId", "eta", "estado" } ] }
```

## 6. Mantenimiento de unidades de transporte — RESUELTO

**Para:** la pantalla «Mantenimiento de flota».

Lo cubren `GET /api/flota` y sus operaciones (`PUT /api/flota/tipos/{tipo}`,
`POST|PUT|DELETE /api/flota/unidades`, `POST /api/flota/restablecer`). Los cambios se guardan en
memoria del servidor y rigen para las corridas que se inicien después. Queda pendiente
persistirlos en la base de datos: el usuario de la aplicación es de solo lectura.

## Lo que no hace falta

**Levantar un bloqueo a mano.** Los bloqueos son planificados: llegan en el archivo mensual
con vigencia horaria y dejan de aplicar solos al vencer. El botón «Levantar el cierre» del
prototipo HTML corresponde a una intervención manual que el modelo no contempla, y no se
implementó.
