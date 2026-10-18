# Medición de rendimiento en la VM — protocolo pendiente

No se han registrado aún tiempos ni memoria en la VM del curso. Las pruebas locales funcionales y el modo interactivo no son evidencia de rendimiento. La máquina la administra otro integrante: coordinar una ventana y no reiniciar el servicio sin él.

1. Fijar commit, versión de Java, CPU/RAM de la VM, fuente de datos, periodo, semilla y algoritmo. Usar `pausaMs` ausente (o 0) y evitar otras corridas simultáneas.
2. Para cada escenario —operación diaria, cinco días y colapso— iniciar por API y medir desde la solicitud hasta `TERMINADA`/`COLAPSADA`/`FALLIDA`. Repetir al menos tres veces por combinación; registrar cada corrida, no solo el promedio.
3. Muestrear el RSS del proceso Java mientras corre (`ps -o rss= -p <PID>` en Linux; valor en KiB) y anotar el máximo observado. Opcional: heap JVM mediante `jcmd <PID> GC.heap_info`. No confundir RSS, heap ni memoria total del servidor.
4. Registrar tiempo real de pared, RSS máximo, estado final, pedidos recibidos/entregados, iteraciones, algoritmo y observaciones. Comparar en la misma VM y con los mismos datos.
5. Adjuntar salida anonimizada y comandos utilizados al CPA. No publicar IP privada, contraseñas, claves ni identificadores de acceso.

| Fecha | Commit | Escenario | Periodo | Algoritmo | Semilla | Repetición | Tiempo real (s) | RSS máx. (MiB) | Estado | Observaciones |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pendiente | | | | | | | | | | |
