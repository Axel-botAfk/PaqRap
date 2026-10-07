# PaqRap

PaqRap es un proyecto de software orientado a mejorar la planificación y el monitoreo de entregas de una empresa de distribución urbana. La solución reemplaza el manejo manual de pedidos y rutas por un sistema capaz de organizar la operación, reducir costos de recorrido y ayudar a cumplir los plazos comprometidos con los clientes.

## Sobre el proyecto

La empresa PaqRap distribuye un único tipo de producto mediante una flota compuesta por autos, motos y bicicletas. Atiende pedidos regulares y priorizados, cuenta con un almacén central y dos almacenes intermedios, y debe responder ante situaciones como bloqueos de calles o averías de vehículos.

La ciudad se modela como una retícula de 70 km por 50 km con nodos cada kilómetro, calles de doble sentido y sin diagonales. Los pedidos, los bloqueos y el mantenimiento preventivo se leen de los archivos mensuales del curso.

## Qué está construido

- **Planificador** con dos metaheurísticas independientes sobre el mismo modelo y la misma función objetivo: GRASP (construcción golosa aleatorizada con LRC más búsqueda local) y Búsqueda Tabú iniciada con `ConstructorVecinoMasCercano`. La experimentación numérica comparó ambas sobre cuatro periodos reales y diez semillas.
- **Simulador** de la operación con bloqueos de calles vigentes por horario, averías de tres tipos, mantenimiento preventivo y recarga de almacenes intermedios cada 24 horas.
- **API REST y WebSocket** (Java 17 + Spring Boot) que ejecuta los tres escenarios del caso y publica el avance en vivo: operación día a día, simulación de cinco días y simulación hasta el colapso logístico.
- **Interfaz web** (React + Vite) con mapa de la operación, rutas, pedidos y averías de la flota. Una corrida se identifica por su UUID en la URL, de modo que la misma simulación se puede seguir desde cualquier dispositivo.

## Estado actual

Entrega beta. No hay base de datos: las corridas viven en memoria mientras el servidor está activo y se pierden al reiniciar. La API es de solo lectura sobre el dominio —se puede crear, consultar y cancelar una ejecución, pero no registrar pedidos ni averías—; lo que falta está anotado en [docs/api-pendiente.md](docs/api-pendiente.md). Tampoco hay autenticación, así que el despliegue debe permanecer bajo acceso controlado.

## Estructura

```text
backend/     Spring Boot: API REST, WebSocket y orquestación de las corridas
codigo/      Planificador, simulador y modelo de dominio (Java)
frontend/    Interfaz React/Vite
datos/       Muestras pequeñas de los archivos del curso
docs/        Documentos de diseño y diagramas
deploy/      Configuración de nginx y systemd para la VM, sin secretos
```

## Arranque local

Requiere JDK 17 o superior, Maven 3.9 o superior y Node 20.19+ o 22.12+.

```bash
mvn clean package && java -jar backend/target/paqrap-backend-1.0-SNAPSHOT.jar
```

```bash
npm ci --prefix frontend && npm run dev --prefix frontend
```

El backend escucha en `127.0.0.1:8081` y Vite reenvía `/api` y `/ws` hacia él. Detalles de despliegue en [deploy/README.md](deploy/README.md); cómo trabajamos, en [CONTRIBUTING.md](CONTRIBUTING.md).
