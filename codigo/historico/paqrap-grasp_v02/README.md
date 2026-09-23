# PaqRap — GRASP (segunda iteración / versión final)

Implementación en Java del algoritmo GRASP para el componente planificador de PaqRap.

Esta versión completa las dos fases definidas para GRASP en el ISA:

1. **Fase constructiva aleatorizada** mediante inserciones factibles y una Lista Restringida de Candidatos (LRC).
2. **Fase de búsqueda local** para mejorar el orden de entrega dentro de cada ruta antes de comparar la solución con la mejor solución global.

## Alcance implementado

- Pedidos con entrega regular (36 h) o priorizada (4/8/12/18 h).
- Almacén central con inventario infinito.
- Almacenes intermedios con stock controlado.
- Autos, motos y bicicletas con capacidad, velocidad y costo/km del caso.
- Asignación progresiva de:
  - pedido;
  - almacén;
  - vehículo;
  - posición en una ruta.
- Restricciones de la construcción:
  - stock;
  - capacidad del vehículo;
  - vehículo disponible;
  - cumplimiento del plazo.
- 1 hora de atención por destinatario.
- Costo de ruta calculado como distancia * costo/km del vehículo.
- Priorización por menor holgura y luego menor costo incremental.
- LRC con parámetro `alfa`.
- Selección aleatoria reproducible mediante semilla.
- Registro de pedidos no asignados.
- Varias iteraciones constructivas y conservación de la mejor solución.
- **Búsqueda local intra-ruta por reinserción de pedidos**.

## Búsqueda local implementada

Después de construir una solución, GRASP recorre cada ruta y explora un vecindario de reinserción:

1. retira temporalmente un pedido de la secuencia;
2. prueba todas las posiciones posibles dentro de la misma ruta;
3. recalcula distancia, costo, duración y horas de entrega;
4. descarta las secuencias que incumplen algún plazo;
5. aplica la mejor reinserción que reduzca el costo;
6. repite el proceso hasta que ya no exista una mejora factible.

La búsqueda local **no cambia el almacén ni el vehículo de la ruta**, por lo que conserva su carga y consumo de stock. Su objetivo en esta iteración es optimizar el orden de entrega de los pedidos ya asignados.

## Decisiones de diseño

1. Java 17 + Maven.
2. Paquetes bajo `com.paqrap`.
3. Una unidad del producto P se considera una unidad de capacidad/paquete.
4. Las ubicaciones se modelan mediante un identificador lógico.
5. Para las pruebas, las distancias se suministran con `MatrizDistancias`.
6. Cada vehículo disponible participa como máximo en una ruta dentro de una construcción.
7. Para iniciar una ruta, el vehículo debe encontrarse en la ubicación del almacén de salida.
8. Un pedido no se divide entre varios vehículos o almacenes en esta implementación.
9. Una ruta parte de un almacén y termina en el último destinatario; no se modela el retorno al almacén.
10. La hora de entrega usada para verificar el plazo incluye viaje + 1 hora de atención por destinatario.
11. Para elegir la mejor solución se minimiza primero la cantidad de pedidos no asignados y, en empate, el costo total.
12. La LRC usa `alfa=0` como comportamiento totalmente voraz y `alfa=1` como lista completamente abierta.
13. La búsqueda local utiliza un vecindario de reinserción dentro de la misma ruta y solo acepta mejoras factibles de costo.

## Fuera del alcance de esta implementación de GRASP

Estas funciones pertenecen a otras partes del sistema o a la replanificación y no forman parte de esta versión del algoritmo:

- bloqueos;
- replanificación ante incidencias;
- fallas durante una ruta;
- turnos y hora de alimentación;
- recarga automática;
- simulación 5D;
- escenario de colapso.

## Ejecución con Maven

```bash
mvn test
mvn package
java -cp target/classes com.paqrap.demo.DemoGrasp
```

## Verificación sin JUnit

Si solo se dispone de JDK 17, se puede compilar el código principal y ejecutar la verificación incluida:

```bash
mkdir -p build
javac -encoding UTF-8 -d build $(find src/main/java -name "*.java")
java -cp build com.paqrap.demo.VerificacionGrasp
```

La verificación cubre:

1. construcción y asignación válida;
2. restricción de capacidad;
3. uso del almacén central como respaldo;
4. rechazo de rutas que incumplen plazo;
5. mejora del orden de una ruta mediante búsqueda local.

## Uso principal

```java
Grasp grasp = new Grasp(calculadorDistancia);

Solucion solucion = grasp.construir(
    horaPlanificacion,
    pedidos,
    almacenes,
    vehiculos,
    new Parametros(20, 0.30, 12345L)
);
```

El método `planificar(...)` ejecuta, para cada iteración:

```text
CONSTRUIR_SOLUCION
        ↓
BUSQUEDA_LOCAL
        ↓
COMPARAR_CON_MEJOR_GLOBAL
```

## Archivos principales

- `Grasp.java`: orquesta construcción, búsqueda local y selección de la mejor solución.
- `Ruta.java`: representa la ruta y permite reemplazar el orden de pedidos validado por la búsqueda local.
- `EstadoOperacion.java`: agrupa la información de entrada del planificador.
- `Parametros.java`: contiene iteraciones, alfa y semilla.
- `MatrizDistancias.java`: implementación de distancias para pruebas y demostración.
- `GraspTest.java`: pruebas unitarias JUnit.
- `VerificacionGrasp.java`: pruebas ejecutables sin dependencias de test.
- `DemoGrasp.java`: ejemplo de generación de rutas.
