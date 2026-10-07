# MySQL local de PaqRap con Docker

Esta opción no modifica tu servicio Windows `MySQL80`: el contenedor escucha solo en
`127.0.0.1:3307`. El esquema y las semillas se cargan **solo la primera vez**
que se crea su volumen `paqrap_mysql_data`. Las contraseñas se generan en
`.env.docker`, archivo ignorado por Git; no se publican en el repositorio.

## Arranque

1. Abre Docker Desktop y espera a que indique que el motor está activo.
2. Desde la raíz del repositorio, en PowerShell:

```powershell
& .\scripts\start-local-db.ps1
docker compose --env-file .env.docker ps
```

No ejecutes `docker compose config` sin `--quiet`: puede imprimir las contraseñas
interpoladas. El script verifica la configuración sin mostrarlas y espera el
healthcheck. Si ya existe `.env.docker`, reutiliza las mismas credenciales.

## Conectar el backend local

Detén primero cualquier backend anterior que use el puerto `8081`. Luego
ejecuta el lanzador local desde la raíz del repositorio:

```powershell
& .\scripts\start-local-backend-mysql.ps1
```

El script lee la contraseña local sin imprimirla, compila con `-Pmysql` y
activa el perfil `mysql`. Busca Maven en `PATH` y, en este equipo, en la
instalación local `..\tmp\maven`. Si prefieres hacerlo manualmente, consulta
[database/README.md](README.md) para los comandos equivalentes.
Mantén Vite en `http://127.0.0.1:5173/` (`cd frontend; npm run dev`) para ver la GUI.

Verifica en otra terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/salud
Invoke-RestMethod http://127.0.0.1:8081/api/datos/periodos
```

La salud debe indicar `fuente: MYSQL`, y los periodos incluir `202901`,
`202902` y `202903`. Para probarlos en la GUI:

| Periodo | Fecha sugerida | Escenario | Esperado en la base |
| --- | --- | --- | --- |
| `202901` | `2029-01-15` | Operación día a día | 40 pedidos, 1 bloqueo, 1 mantenimiento. |
| `202902` | `2029-02-01` | Simulación de 5 días | 75 pedidos, 2 bloqueos, 2 mantenimientos. |
| `202903` | `2029-03-01` | Colapso logístico | 35 pedidos de estrés, 1 bloqueo, 2 mantenimientos. |

Los tres almacenes y 37 vehículos vienen del esquema base. El bloqueo se
resalta en el mapa **solo durante su vigencia**; ajusta fecha y hora para
verlo. El resultado de colapso no está precargado: lo determina el simulador.

## Detener sin perder datos

```powershell
docker compose --env-file .env.docker down
```

El volumen se conserva. **No uses `down -v`** salvo que quieras borrar
deliberadamente toda la base de pruebas y reinicializar las semillas. Cambiar
los SQL montados no altera una base ya inicializada; para nuevas pruebas usa
migraciones o ejecuta los scripts adicionales manualmente. Para ampliar una
instalación local que ya tenga las semillas anteriores, sin borrar el volumen:

```powershell
Get-Content database/seed_ampliacion.sql -Raw |
  docker compose --env-file .env.docker exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root paqrap'
```

`seed_ampliacion.sql` es idempotente: repetirlo no duplica pedidos. En una
instalación nueva, Compose lo ejecuta automáticamente después de las tres
semillas base.

Verificación local realizada el 06/10/2026 con semilla de algoritmo `7`:
operación diaria terminó con 40 pedidos, cinco días terminó con 75 y el caso
de estrés (`TABU`, horizonte de 1 día) terminó en estado `COLAPSADA` con 35.

Esta base es solo para desarrollo local. `paqrap_app` queda con permiso `SELECT`;
los resultados de las corridas aún permanecen en memoria del backend y no se
guardan en MySQL.

## Mapa de la beta

El visualizador dibuja los nodos `0..70` y `0..50` de la ciudad del caso. Cada
arista mide 1 km y el origen `(0,0)` está abajo a la izquierda. Para cada par
almacén/parada, calcula un **trazo esquemático** por pasos cardinales (norte,
sur, este u oeste) evitando los nodos bloqueados en la instantánea mostrada.
Si un destino está aislado, no inventa una línea de ruta. El trazo no equivale
a un historial GPS ni reproduce los tiempos de paso exactos del motor de Java.
Al finalizar la ejecución se atenúa; al pasar el cursor o enfocar con Tab se
resalta. La salida de una ruta se marca con `S`, su última llegada con `F` y
las demás paradas con puntos de alto contraste. Los almacenes se muestran con
su ubicación y stock **inicial**; esta
beta todavía no expone el inventario dinámico de cada almacén.

Pruebas del trazador y compilación del frontend: `cd frontend; npm test; npm run build`.
Verificación realizada el 06/10/2026 contra el MySQL local: `/api/salud`
informó `MYSQL/OK`, el periodo `202902` devolvió 75 pedidos, dos bloqueos y
tres almacenes. Con semilla `7`, la corrida diaria `202901` terminó con 40
pedidos recibidos (39 entregados), cinco días `202902` con 75 recibidos
(74 entregados) y estrés `202903` con Búsqueda Tabú quedó `COLAPSADA` (35
recibidos). Esto valida la conexión y la lectura de semillas, no la calidad
logística del resultado. Los pedidos entregados y recibidos no tienen por qué
coincidir: el resto puede quedar pendiente al finalizar el horizonte.
