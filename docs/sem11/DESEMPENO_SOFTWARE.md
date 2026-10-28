# Desempeño del software — Semana 11

Proyecto PaqRap · Corte: 28/10/2026 · Rama Axel-avance, base main `173c2fb`.

## 1. Objetivo y estado

Presentar funcionamiento y desempeño en simulación de cinco días y operaciones día a día. El compromiso de Axel y la validación de datos de Leslie incluyen también colapso; se registra como tercera ejecución. Este informe contiene los resultados efectivamente observados y el protocolo para completar las mediciones pendientes. No es un informe de rendimiento VM finalizado.

## 2. Resultados disponibles

| Verificación | Resultado | Alcance |
| --- | --- | --- |
| Pruebas frontend | 64 aprobadas, 0 fallidas, ejecutadas el 28/10/2026 | Incluye cinco casos nuevos de concurrencia temporal, recepción, fracciones, reasignación e hitos futuros. No acredita API completa ni E2E. |
| Compilación frontend | Vite terminó satisfactoriamente | Código compilable; no constituye validación visual o rendimiento del servidor. |
| Entorno local | Frontend 5175 → backend 8082 → MySQL Docker 3307 | Salud MYSQL y períodos disponibles comprobados. No mide VM/AWS. |
| Pedidos simultáneos | Colección común hasta el reloj visible; destinos activos conservados, coincidencias identificables | Lógica cubierta; pendiente demo integrada de versión final con captura/UUID. |
| Antecedente web 24/10 | Avería 1 de TA06; pedido 4-D-014 reasignado a TA10; API registró aplicación y replanificación | Evidencia de otra fecha/versión, no repetición del candidato actual. |
| Multivisualizador previo | Dos pestañas siguieron mismo UUID y mantuvieron filtros/historial independientes | No equivale a dos dispositivos físicos. |

Comandos de regresión: `npm test --prefix frontend` y `npm run build --prefix frontend`.

## 3. Consumo de datos, Ta, Sa y Sc

`Ta` es el tiempo de cada llamada a `planificador.planificar(estado, parametros)`, medido en `Simulador` y registrado con tamaño de la cola. `Sa` es el intervalo real de publicación y `Sc` el avance simulado objetivo de cada ventana. `K=Sc/Sa` relaciona ambos ejes.

`RitmoEscenario` define el ritmo y `EjecucionesService` acumula/publica resultados de las iteraciones; `Marcapasos` administra esperas. El simulador conserva cortes propios según eventos/latido. Por tanto no se debe afirmar que hay exactamente una ejecución del algoritmo por cada Sa. Debe registrarse cuántas hubo y cuántos datos entraron en cada intervalo.

| Configuración por defecto en código | Sa | Sc objetivo | Observación |
| --- | --- | --- | --- |
| Cinco días | 20 s | 60 min simulados | 120 ventanas objetivo; 40 min reales previstos si el cálculo sostiene el ritmo. |
| Operación diaria | 20 s | 20 min simulados a 60x | Demo acelerada por defecto; el alcance de operación real requiere verificar 1x, configurado desde backend sin control de aceleración en la presentación. |
| Colapso | 10 s | 120 min simulados | Hasta el primer incumplimiento o fin del horizonte. |

Son valores de configuración, no resultados de medición. Las ventanas publicadas pueden cruzar varios cortes/eventos; comprobar el intervalo real mediante relojDesde/relojHasta y duración de ventana. `avance.milisegundosPlanificacion` no garantiza conservar todas las llamadas de una ventana: guardar la serie completa del motor para estadísticas y carga total.

Traza requerida: número de ventana, reloj inicio/fin simulado, instante real de publicación, número de llamadas, Ta por llamada, pedidos recién incorporados y pendientes, datos de bloqueo/mantenimiento aplicados y retraso de publicación. Los pedidos no deben adelantarse a su recepción y ningún registro debe perderse al cruzar un límite de bloque.

## 4. Protocolo de ejecución

1. Leslie fija fuente oficial y cobertura; concilia ventas, bloqueos y mantenimiento por mes/fecha contra BD. Separar semillas demostrativas.
2. Melvin coordina ventana de VM, registra commit desplegado, Java, CPU/RAM, política de acceso y dispositivo/red. No reiniciar ni reemplazar servicios ajenos a la prueba.
3. Axel fija escenario, inicio, horizonte, algoritmo/semilla y parámetros; guarda petición/respuesta y UUID.
4. Registrar Ta de todas las llamadas, suma por ventana, duración real completa o intervalo diario, errores/retrasos y memoria RSS del proceso Java. Registrar heap por separado si se utiliza.
5. Ariana verifica mapa/indicadores al mismo reloj y Leslie conserva evidencias/casos CPA. Dos dispositivos físicos siguen el mismo UUID.
6. Conservar resumen final, serie de medidas, conteos y gráficos/tablas del resultado. Cancelar solo pruebas propias cuando corresponda.

Propuesta del equipo: tres repeticiones por combinación para contrastar variabilidad, sin atribuir ese número al cronograma. Una corrida de cómputo sin esperas puede servir para medir el motor, pero no reemplaza la demostración de cinco días en 30–60 min.

## 5. Métricas y formato de registro

- **Rendimiento:** Ta promedio/máximo/p95, observaciones y tamaño de cola; cómputo total por ventana; RSS máximo observado y frecuencia de muestreo; retrasos de publicación.
- **Tiempo de presentación:** desde inicio hasta estado final, pausas/incidencias registradas; cinco días debe medirse en la banda 30–60 min, no deducirse de configurar 40.
- **Operación:** pedidos originales recibidos/completos/en plazo/pendientes/vencidos, fracciones y productos por separado; distancia y costo. Cada tasa declara denominador.
- **Servicio:** latencia de arranque/primer movimiento, errores API, continuidad WS y resultados en dos dispositivos.

| Entorno | Escenario | Datos/inicio/semilla | Commit/UUID | Tiempo real | Ta medio/máx./p95 | RSS máx. | Resultado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| VM | Cinco días | Pendiente de datos oficiales confirmados | Pendiente | No medido | No medido | No medido | Pendiente de ejecución |
| VM | Diario 1x | Pendiente de intervalo y datos | Pendiente | No medido | No medido | No medido | Pendiente de ejecución |
| VM | Colapso | Pendiente de datos y horizonte | Pendiente | No medido | No medido | No medido | Pendiente de ejecución |

Para RSS en Linux: `ps -o rss= -p <PID_JAVA>` devuelve KiB; dividir entre 1024 para MiB. Es una muestra del proceso completo, no heap ni RAM de toda la VM. Identificar PID, frecuencia y número de muestras. Registrar pico observado, no presentarlo como pico exacto si el muestreo es periódico.

## 6. Casos de aceptación a ejecutar

| ID de ejecución propuesta | Prueba | Resultado esperado | Estado actual |
| --- | --- | --- | --- |
| E2E-S11-01 | 3 pedidos activos, 2 rutas y otro pedido futuro | Todos los activos identificables; futuro ausente hasta recepción; tabla/contador/mapa coherentes | Lógica frontend aprobada; E2E pendiente |
| E2E-S11-02 | Dos pedidos en mismo destino y original fraccionado | IDs accesibles, cantidades conservadas y originales/fracciones diferenciados | Demo pendiente |
| E2E-S11-03 | Avería con unidad asignada, tipos 1/2/3 por separado | Registro/aplicación/reasignación o no asignado; carga, nodo, permanencia y reingreso correctos | Antecedente tipo 1; versión final y reglas completas pendientes |
| E2E-S11-04 | Archivo real completo e inválidos controlados | Conteos conciliados; rechazo identificable, sin duplicación ni pérdida silenciosa | Pendiente |
| E2E-S11-05 | Cinco días completos con bloqueos/mantenimiento | Datos consumidos según reloj; duración real 30–60 min, resultado y reporte | Pendiente |
| E2E-S11-06 | Operación diaria 1x durante intervalo medido | Reloj a tiempo real; llegada concurrente y atención de incidencias | Pendiente |
| E2E-S11-07 | Dos dispositivos y desconexión/reconexión | Misma corrida, consultas locales independientes, avisos y recuperación consistente | Dos pestañas previas; dos dispositivos pendientes |
| E2E-S11-08 | Colapso | Primer incumplimiento con pedido/instante y último plan estable identificables | Pendiente |
| E2E-S11-09 | Acceso acordado: autorizado/no autorizado | Política efectiva y canal WS protegidos según decisión del equipo | Pendiente de política/evidencia |

Estos IDs son un complemento propuesto del CPA, no resultados aprobados ni sustitutos de sus IDs vigentes. Melvin consolida el catálogo; Axel ejecuta API/escenarios; Leslie valida datos y Ariana GUI. Cada caso conserva petición, respuesta, esperado/obtenido, commit/UUID, fecha y captura/CSV/log sin secretos.

## 7. Lectura del desempeño y conclusión provisional

El frontend supera su regresión y compilación; el entorno local sirve la aplicación con MySQL. Se corrigió una fuente de incoherencia temporal entre planes y listados, pero aún no hay mediciones suficientes de VM para concluir que la versión candidata cumple el desempeño completo. La aceptación de cinco días, diario y colapso depende de ejecutar este protocolo, cerrar discrepancias de reglas del motor y documentar resultados.

Para la exposición: mostrar primero el comportamiento de negocio y luego explicar datos, reloj/bloques, algoritmo y mediciones. No usar pruebas unitarias como sustituto de evidencia de integración. No ofrecer controles para acelerar/avanzar/retroceder en la demostración evaluada, conforme al cronograma.

## 8. Material a adjuntar al cierre

CSV/tabla de mediciones y traza Sa/Sc, manifiesto de datos, resultados CPA, video/capturas de cinco días y diario cuando se soliciten, versión desplegada y ZIP reproducible `sol.integrada.sem11`. El documento de desempeño individual/equipo complementa esta evidencia con aportes, compromisos y gestión.
