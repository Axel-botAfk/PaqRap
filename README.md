# PaqRap

PaqRap es un proyecto de software orientado a mejorar la planificación y el monitoreo de entregas de una empresa de distribución urbana. La solución busca reemplazar el manejo manual de pedidos y rutas por un sistema capaz de organizar la operación, reducir costos de recorrido y ayudar a cumplir los plazos comprometidos con los clientes.

## Sobre el proyecto

La empresa PaqRap distribuye un único tipo de producto mediante una flota compuesta por autos, motos y bicicletas. Atiende pedidos regulares y priorizados, cuenta con un almacén central y dos almacenes intermedios, y debe responder ante situaciones como bloqueos de calles o fallas de vehículos.

El proyecto consiste en desarrollar una solución informática que permita gestionar esta operación y apoyar la toma de decisiones logísticas.

## Qué se está desarrollando

La solución contemplará principalmente:

- Registro de pedidos y cantidades solicitadas por cada cliente.
- Planificación de rutas considerando plazos de entrega, capacidad, velocidad y costo de los vehículos.
- Replanificación de rutas ante bloqueos, fallas u otras incidencias durante la operación.
- Monitoreo gráfico de vehículos, pedidos y entregas mediante un mapa interactivo.
- Evaluación de la operación diaria, una simulación de cinco días y una simulación hasta el colapso logístico.
- Comparación de dos algoritmos metaheurísticos implementados en Java para el componente de planificación.

## Estado actual

Está implementado el componente planificador con sus dos algoritmos metaheurísticos en Java:
GRASP para la fase constructiva y búsqueda tabú para la fase de mejora, sobre la ciudad real
del caso (retícula de 70x50 km), la flota real (10 autos, 15 motos, 12 bicicletas) y los tres
almacenes en sus posiciones definitivas.

Quedan pendientes los bloqueos de calles, las rutas con recarga en almacenes intermedios, las
entregas parciales, los turnos con hora de alimentación, las averías, el mantenimiento
preventivo, la lectura de los archivos de datos, la replanificación y el componente
visualizador.

## Estructura

```text
codigo/
  paqrap-grasp_v02/   Primera iteración del planificador (congelada como evidencia)
  paqrap-tabu_v02/    Planificador vigente: GRASP + búsqueda tabú
```

