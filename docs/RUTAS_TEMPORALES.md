# Rutas y pedidos durante la reproducción

Antes, el mapa conservaba los fotogramas de posición pero dibujaba siempre
`ejecucion.rutas`, que contiene únicamente el plan más reciente. Por eso un
periodo completo parecía tener una sola salida y una sola llegada.

Ahora el backend guarda cada replanificación como un plan con hora simulada,
lista de rutas y todas sus paradas. La GUI descarga ese historial paginado y,
para cada fotograma, elige el último plan cuyo reloj no supera el instante
reproducido. Si dos vehículos tienen rutas vigentes al mismo tiempo, ambas
se dibujan con colores distintos. Cada viaje traza todos los segmentos:
almacén → pedido 1 → pedido 2 → ...; las paradas se numeran y la última
lleva un aro. La lista de rutas sigue el mismo instante del mapa.

Al comenzar una replanificación, el backend publica
`calculandoRutas=true`/`CALCULANDO_RUTAS` y vacía las rutas del estado actual.
En el borde vivo del reproductor, mapa y lista muestran «Calculando rutas…»
hasta recibir el plan nuevo. Si el usuario vuelve a un fotograma histórico,
puede seguir viendo su plan mientras se calcula el actual.
En una prueba local de operación diaria con GRASP y pausa de demostración,
el WebSocket publicó 36 eventos `CALCULANDO_RUTAS` con rutas vacías y después
36 planes (`PROGRESO`) con el indicador desactivado.

El trazo entre nodos es una representación cardinal esquemática de la grilla,
no el recorrido GPS exacto. Las paradas corresponden al plan previsto en ese
instante; no deben contarse como entregas efectivas. Para ello se mantienen
las métricas reales del simulador.

## Prueba manual

1. Iniciar `PERIODO_5_DIAS` con datos `202902` y esperar el resultado.
2. Pausar la reproducción y mover el deslizador a una hora con pedidos.
3. Confirmar varias rutas simultáneas cuando el plan las tenga; seleccionar
   una y seguir sus paradas numeradas. Mover el deslizador a otro día y
   comprobar que las rutas cambian. Volver al inicio: no debe aparecer el
   último plan desde el primer fotograma.
4. En un plan con una ruta de varios pedidos, verificar el orden de los
   números y el aro de última parada. Hover o Tab resalta cada ruta.

Las instantáneas se mantienen en memoria (hasta 2048 planes y 2048
fotogramas por corrida); no persisten tras reiniciar el backend.

## Evidencia local (18/10/2026)

Con MySQL Docker, periodo `202902`, GRASP y semilla 7, la corrida
`b75f12b5-96d0-4a95-bdbe-c77661fcc5fc` terminó y devolvió **112 planes**
entre el 1 y el 5 de febrero. **63 planes** contenían más de una ruta
simultánea y al menos una ruta contenía **2 paradas**. La selección temporal
del plan y el orden de varias paradas están cubiertos por la prueba del
frontend. Queda la revisión visual manual en la GUI a distintos anchos de
pantalla y la validación en la VM.
