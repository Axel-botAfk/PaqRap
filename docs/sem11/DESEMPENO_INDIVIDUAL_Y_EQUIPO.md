# Desempeño individual y del equipo — Semana 11

Proyecto PaqRap · Equipo 2B · Corte de evidencia: 28/10/2026.

## 1. Propósito y fuentes

Presentar el avance de los compromisos de semana 11 en gestión y construcción técnica. El informe se basa en los compromisos comunicados por el equipo, el historial del [repositorio](https://github.com/Axel-botAfk/PaqRap), el código integrado hasta `173c2fb` y los cambios de esta rama. El estado desplegado y las mediciones deben comprobarse por separado.

Un merge acredita integración; no acredita autoría de todos sus cambios. Los compromisos se valoran como cumplidos solo cuando existe evidencia del resultado completo. No se asignan horas, calificaciones ni porcentajes sin RHA y evaluación del equipo.

## 2. Ariana Milagros Silva Flores

**Compromisos:** completar listas de unidades, almacenes y envíos con ocupación/semáforo; representar averías de tipos 1/2/3 durante su vigencia; ensayar GUI y presentación; actualizar RHA.

**Avance técnico comprobable.** El repositorio registra la integración de sem08_max mediante [5971416](https://github.com/Axel-botAfk/PaqRap/commit/5971416). La solución tiene mapa, listado/seguimiento de pedidos, inventario y funciones de carga/stock. Las mejoras gráficas posteriores de Leslie y las modificaciones actuales de Axel se identifican separadamente. Actualmente se dispone de componentes que permiten verificar el frente de GUI, pero no se acredita una lista completa de flota con todos los indicadores requeridos ni marcadores temporales diferenciados de cada avería.

| Compromiso | Estado sustentable | Evidencia y cierre necesario |
| --- | --- | --- |
| Listas y ocupación | Parcial | Inventario y pedidos disponibles; falta cerrar lista de todas las UT, porcentajes/colores y coherencia temporal con pruebas. |
| Averías 1/2/3 en mapa | Pendiente de cierre | Registro/lista de averías existen. Falta demostrar marcador por tipo, posición y duración en 5D y diario. |
| Demo y presentación | Material preparado, ensayo no acreditado | Ejecutar ambos escenarios y conservar captura/video y resultado de prueba. |
| RHA | No verificado | Cada integrante debe actualizar su registro con horas reales. |

**Sustentación técnica:** explicar `Mapa.jsx`, filtros, agrupaciones, relación reloj/estado y diferencia entre carga actual y carga planificada. Revisar que varias entregas concurrentes puedan identificarse y que una selección no oculte las otras sin filtro explícito.

## 3. Melvin Alejandro Custodio Romaní

**Compromisos:** mantener VM/AWS y demostrar dos dispositivos; implementar control de acceso acordado y configuración nginx/servidor con WS; completar y ejecutar CPA E2E; actualizar instrucciones/diagrama y presentar; actualizar RHA.

**Avance técnico comprobable.** El historial registra configuración de despliegue bajo `AleCore01`: [899b498](https://github.com/Axel-botAfk/PaqRap/commit/899b498), [5e43ad0](https://github.com/Axel-botAfk/PaqRap/commit/5e43ad0) y [81ad1b9](https://github.com/Axel-botAfk/PaqRap/commit/81ad1b9). El equipo debe confirmar la correspondencia de esa cuenta y las acciones personales de Melvin antes de atribuirle esos commits. El proyecto dispone de REST, WebSocket, configuración systemd/nginx y MySQL; su presencia en Git no acredita que toda configuración esté aplicada en la VM.

| Compromiso | Estado sustentable | Evidencia y cierre necesario |
| --- | --- | --- |
| VM/AWS y dos dispositivos | Parcial | Antecedente web funcional con MYSQL y dos pestañas. Falta prueba de 5D/diario en dos dispositivos físicos y versión desplegada. |
| Acceso y corrección WS | Sin cierre verificable | Falta registrar mecanismo de acceso acordado, config efectiva y prueba autorizado/no autorizado; no inventar login o roles. |
| CPA E2E ejecutado | Parcial | Casos y documentación disponibles; faltan resultados desplegados por caso y evidencia reproducible. |
| Instrucciones/diagrama | Parcial | Hay documentación previa; debe alinearse con puertos, MySQL, ventanas, contrato actual y acceso acordado. |
| Presentación y RHA | Material preparado; registros por verificar | Ensayar y actualizar actividades reales. |

**Sustentación técnica:** una corrida compartida por UUID, consultas REST y canal WS, filtros locales por navegador, acciones compartidas del motor, reconexión y versión del servidor. Separar acceso al sitio de una funcionalidad académica de inicio de sesión.

## 4. Leslie Pierina Díaz Meza

**Compromisos:** consolidar ZIP sol.integrada.sem11 e instrucciones desde cero; comprobar tres escenarios con datos reales completos; ejecutar CPA de datos; preparar presentación y actualizar RHA.

**Avance técnico comprobable.** El historial `LesslieD` documenta [5680366](https://github.com/Axel-botAfk/PaqRap/commit/5680366), integración DBManager/configuración MySQL; [b53c35d](https://github.com/Axel-botAfk/PaqRap/commit/b53c35d), iconos; y [318b901](https://github.com/Axel-botAfk/PaqRap/commit/318b901), ajustes Operación/Simulación. Estos aportes están integrados en `173c2fb`.

| Compromiso | Estado sustentable | Evidencia y cierre necesario |
| --- | --- | --- |
| ZIP e instalación desde cero | Pendiente de consolidación de esta versión | Preparar paquete tras integración/revisión y probar en carpeta limpia; registrar commit e instrucciones. |
| Datos completos y tres escenarios | Parcial | Conexión MySQL y semillas disponibles. Falta conciliación de registros oficiales por mes y ejecución integral de tres escenarios. |
| CPA de datos ejecutado | No acreditado completo | Registrar lector/importación, válidos/inválidos, conteos, duplicados y rechazos por causa. |
| Presentación y RHA | Material preparado; registros por verificar | Vincular documentos consolidados y actualizar horas reales. |

**Sustentación técnica:** distinguir importación/validación en navegador, datos persistidos en MySQL y corridas temporales en memoria. Mostrar conteos oficiales frente a demostrativos. Las correcciones de diseño ya registradas no sustituyen los compromisos de validación de datos.

## 5. Axel Eduardo Arista Montero

**Compromisos:** medir Ta, tiempo y memoria en VM en tres escenarios; verificar consumo Sc/Sa; ejecutar CPA API/escenarios/E2E; preparar presentación y actualizar RHA.

**Avance técnico comprobable.** Historial previo: [73aec3e](https://github.com/Axel-botAfk/PaqRap/commit/73aec3e), backend de simulación; [e81926f](https://github.com/Axel-botAfk/PaqRap/commit/e81926f), rutas temporales, averías e inventario. En esta iteración se incorporó main a Axel-avance y se ajustaron mapa/tabla/contadores para compartir pedidos conocidos hasta el instante visible. Se añadieron cinco pruebas y se retiraron flechas añadidas tras revisión visual del usuario.

| Compromiso | Estado sustentable | Evidencia y cierre necesario |
| --- | --- | --- |
| Ta/tiempo/memoria VM, tres escenarios | Pendiente de medición | La instrumentación de Ta existe; no hay resultados VM actuales para cerrar la tabla. |
| Explicación Sc/Sa | Documentada; verificación empírica pendiente | El ritmo agrupa publicación; no coincide necesariamente con una sola llamada al algoritmo por ventana. Registrar traza. |
| CPA API/escenarios/E2E | Parcial | 64 tests frontend y build aprobados; no son pruebas API/E2E. Repetir casos integrales en la versión final. |
| Presentación y RHA | Guion/documentos preparados; ensayo/horas pendientes | Usar resultados medidos y actividades reales. |

## 6. Desempeño conjunto en gestión

El equipo ha integrado frentes de motor/API, datos, despliegue y visualización mediante ramas y merges. La revisión ha permitido identificar diferencias entre planes, relojes y contadores, separar semillas de datos oficiales y conservar una lista de pendientes aplicables. La trazabilidad no permite afirmar cumplimiento total de semana 11: las mediciones VM, el acceso acordado, las pruebas completas y la entrega reproducible siguen siendo condiciones de cierre.

La coordinación necesaria es: Leslie confirma datos y versión; Melvin mantiene el entorno autorizado y el canal; Axel mide y ejecuta pruebas de API/escenario; Ariana valida que la GUI refleje resultados e incidencias. Cada prueba debe conservar commit, datos y UUID para que los cuatro expliquen la misma ejecución.

| Actividad de gestión | Situación y evidencia requerida |
| --- | --- |
| Compromisos | Registrados en este informe; estados parciales/pendientes con acciones de cierre. |
| Integración | main incorporado a Axel-avance; cambios nuevos propuestos por PR, sin afirmar despliegue automático exitoso. |
| Calidad | 64 pruebas frontend aprobadas y build; lista CPA integral por cerrar. |
| Riesgos | Priorizar datos incompletos, diferencias temporales, carga/averías y falta de ventana VM. Actualizar CGR con estado real. |
| Horas y actividades | RHA de los cuatro: pendiente de consultar/actualizar por cada integrante. |
| Adhesión ISO/IEC 29110 | Presentar artefactos y actividades de semana 11; no declarar certificación ni porcentaje sin evaluación. |
| Entrega | ZIP sol.integrada.sem11 y ejecución desde cero pendientes hasta consolidar y verificar versión final. |

## 7. Presentación y cierre

Cada integrante explica: compromiso → aporte identificable → prueba/resultado → pendiente. La duración se ajusta a la instrucción actual del docente. El software se presenta con datos y reloj visibles; las medidas del segundo documento se completan únicamente al ejecutar pruebas.

Para cerrar: evidencias de dos dispositivos; resultados en tres escenarios; versión desplegada/PR; acceso acordado probado; CPA ejecutado; ZIP reproducible; RHA/CGR/adhesión actualizados y ensayo. El equipo puede presentar el avance real antes de cerrar todo, pero debe mantener explícitos estos pendientes.
