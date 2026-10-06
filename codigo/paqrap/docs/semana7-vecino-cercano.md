# Semana 7: constructor independiente de Búsqueda Tabú

## Decisión técnica

Búsqueda Tabú inicia ahora desde `ConstructorVecinoMasCercano`, sin invocar GRASP. La
clase anterior `InsercionPorHolgura` continúa disponible como variante experimental,
pero ya no es el constructor predeterminado de Tabú. Esto permite comparar dos
procedimientos independientes: GRASP construye sus propias soluciones y Tabú construye
una solución por vecino más cercano antes de mejorarla.

El vecino más cercano elige, en cada paso, el pedido factible con menor distancia
Manhattan desde el extremo del programa de alguna unidad candidata. Un viaje nuevo
incluye el desplazamiento hasta el almacén de carga. El evaluador compartido valida
plazos, bloqueos, disponibilidad, capacidad e inventario antes de asignar. Después de
cada asignación se recalculan todos los candidatos pendientes: una unidad puede
terminar más cerca de un pedido aunque no fuese su mejor opción anterior.

La distancia Manhattan es el criterio *constructivo* de cercanía, no el costo final
de la ruta. El costo y la factibilidad se obtienen con el evaluador, que contempla
el enrutamiento del caso. La lista de unidades candidatas está limitada a 18 por
pedido, por lo que se trata de una heurística de vecino más cercano factible, no de
una búsqueda exhaustiva en toda la flota.

## Verificación reproducible

Desde `codigo/paqrap`, con Java 17 o posterior y PowerShell:

```powershell
$sources = rg --files src/main/java -g '*.java'
New-Item -ItemType Directory -Path out -Force | Out-Null
javac -encoding UTF-8 -d out $sources
java -cp out com.paqrap.verificacion.VerificacionGrasp
java -cp out com.paqrap.verificacion.VerificacionTabu
java -cp out com.paqrap.verificacion.VerificacionBloqueos
java -cp out com.paqrap.verificacion.VerificacionAlns
java -cp out com.paqrap.verificacion.VerificacionSimulacion
```

Comprobado en la rama `codex/semana7-vecino-cercano-diseno` el 30/09/2026 con
Java 21: 14 comprobaciones de GRASP, 10 de Tabú, 10 de bloqueos, 7 de ALNS y 16 de
simulación; 57 en total. La prueba de Tabú comprueba que su solución inicial coincide
con la producida directamente por el constructor de vecino más cercano, que no es
una solución de GRASP y que un destino cercano se atiende antes que uno lejano con
mayor urgencia cuando ambos son factibles.

La antigua prueba de distribución exigía 20 vehículos de 37, umbral ligado al
constructor por holgura. El nuevo constructor utilizó 19 de 37 y los tres tipos de
vehículo. Se reemplazó el umbral fijo por una mayoría estricta de la flota, que
conserva la intención de detectar concentración excesiva sin afirmar que dos
constructores distintos deban usar exactamente el mismo número de unidades.

## Corrida exploratoria, no selección definitiva

```powershell
java '-Dpaqrap.algoritmos=tabu,grasp' '-Dpaqrap.semillas=11' -cp out com.paqrap.demo.BancoDePruebas 1
```

Datos: `datos/reales` de septiembre de 2026; horizonte: 1 día; semilla: 11.

| Algoritmo | Productos entregados | Pendientes | Vencidos | km | Costo (S/) |
|---|---:|---:|---:|---:|---:|
| Tabú + vecino más cercano | 811 | 172 | 0 | 4250 | 29033 |
| GRASP | 754 | 229 | 0 | 5321 | 35936 |

Estas dos corridas se ejecutaron en paralelo. Sus tiempos de ejecución no sirven
para calibrar el intervalo del simulador. Una sola semilla y un solo día tampoco
justifican declarar un algoritmo ganador. El IEN debe fijar antes las hipótesis,
la configuración, las semillas y los escenarios, y luego ejecutar las réplicas
correspondientes, incluyendo el horizonte de cinco días que pide el caso.

## Pendiente para cerrar el entregable

- Alinear el pseudocódigo y la descripción de `21.dis.selec.algoritmos.v04` con el
  constructor efectivamente implementado, incluida la lista limitada de candidatos.
- Completar el IEN con datos de corrida final, configuración y criterios de
  comparación; no reutilizar resultados de la versión por holgura.
- Consolidar y revisar la DS entre las cuatro personas antes de convertirla a PDF.
