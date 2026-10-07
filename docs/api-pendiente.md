# API pendiente

Lo que la interfaz necesita y la API todavía no ofrece. Cada punto tiene una pantalla que
ya existe en el frontend y que hoy muestra un aviso «Pendiente» en lugar de inventar datos.

Mientras no existan, la interfaz no se rompe: degrada a un estado declarado.

## 1. Existencias por almacén, en vivo

**Para:** panel «Inventario de almacenes» en Operación diaria.

`GET /api/datos/mapa/{aaaamm}` ya devuelve los almacenes con su `stockInicial`, y el mapa lo
usa. Lo que falta es el stock **durante** la corrida: el motor lo lleva en `Inventario`
(central ilimitado, intermedios con tope de 1000 unidades y recarga cada 24 h a las
23:59:59), pero no viaja en `EjecucionVista`. Solo hay que copiarlo a la vista, como ya se
hace con vehículos y rutas en `Vistas`.

```
EjecucionVista.existencias: [
  { "almacenId": "ALM-CENTRAL",   "disponible": 0, "reservado": 0, "ilimitado": true  },
  { "almacenId": "ALM-NOR-OESTE", "disponible": 812, "reservado": 40, "ilimitado": false }
]
```

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

## 4. Registro de avería

**Para:** la sección «Registrar una avería» en Averías de la flota.
**Acordado:** no entra en este avance, se deja anotado.

```
POST /api/ejecuciones/{id}/averias
     { "vehiculoId": "TA03", "tipo": 1|2|3, "nodo": {"x":31,"y":12}, "instante": "..." }
-> 202  (la reasignación la aplica el planificador en la siguiente iteración)
```

El motor ya distingue los tres tipos y calcula la ventana de indisponibilidad con los turnos
oficiales (07:00, 15:00, 23:00); lo que falta es inyectar el evento en una corrida viva.

## 5. Seguimiento de un pedido con entregas parciales

**Para:** la vista «Seguimiento del pedido» del prototipo.
**Acordado:** no entra en este avance.

`PedidoVista` describe un pedido dentro de una ruta. Falta la vista contraria: dado un pedido,
sus fracciones repartidas entre unidades y viajes, que el motor ya modela en `RepartoParcial`.

```
GET /api/ejecuciones/{id}/pedidos/{pedidoId}
-> 200 { "id", "cantidad", "entregado", "pendiente",
         "fracciones": [ { "orden": 1, "de": 2, "vehiculoId", "cantidad",
                           "almacenId", "eta", "estado" } ] }
```

## Lo que no hace falta

**Levantar un bloqueo a mano.** Los bloqueos son planificados: llegan en el archivo mensual
con vigencia horaria y dejan de aplicar solos al vencer. El botón «Levantar el cierre» del
prototipo HTML corresponde a una intervención manual que el modelo no contempla, y no se
implementó.
