# Entrega de beta al responsable de la VM

Esta guía es para coordinar la instalación. No autoriza modificar el servidor
sin el responsable del equipo y no contiene secretos.

## Qué está listo

- Frontend React/Vite que consulta la API por rutas relativas `/api` y `/ws`.
- Backend Java/Spring que lee archivos del curso, ejecuta GRASP o Vecino Más
  Cercano + Tabú y publica instantáneas por REST y WebSocket.
- Los tres escenarios tienen pruebas automatizadas con datos mínimos. GRASP
  y Tabú completaron una corrida diaria con datos del curso. La carga real de
  cinco días todavía requiere una prueba completa en el hardware de la VM.
- No hay BD, login real ni persistencia de ejecuciones: un reinicio borra las
  corridas y sus enlaces. Tampoco hay funciones de escritura de pedidos.

## Compilación reproducible

En una copia de la rama beta:

```sh
mvn clean package
cd frontend
npm ci
npm run build
```

Requiere JDK 17+, Maven 3.9+ y Node 20.19+ o 22.12+. El lockfile fija la
instalación de npm. Antes de instalar, revisar que el árbol Git esté limpio,
que las pruebas pasen y que no haya `.env`, tokens o llaves en los artefactos.

## Instalación a coordinar con el encargado

1. Respaldar el HTML y JAR anteriores; acordar una ventana corta de cambio.
2. Copiar el JAR a `/opt/paqrap/paqrap.jar` y los archivos de datos del curso
   a `/opt/paqrap/datos/reales`. Dar permisos de lectura al usuario `paqrap`.
3. Configurar `PAQRAP_DATA_DIR=/opt/paqrap/datos/reales` en systemd, sin
   introducir secretos en el repositorio. Reiniciar el servicio y comprobar
   `GET /api/salud` y `GET /api/datos/periodos` desde la VM.
4. Revisar nginx con `nginx -t`; confirmar que `/api/` y `/ws/` se reenvíen
   al puerto local 8081 y que HTTPS esté activo antes de exponer la beta.
5. Publicar los archivos de `frontend/dist/` en la raíz web existente. No
   sobrescribir el HTML actual hasta que el responsable apruebe el reemplazo.
6. Desde dos dispositivos abrir la misma URL con `?ejecucion=<id>` y verificar
   salud, datos, inicio/cancelación, mapa, métricas, recuperación tras cortar
   WebSocket y los tres escenarios. Comparar las cifras con la respuesta API.
7. Probar una corrida de cinco días en la VM midiendo tiempo y memoria; el
   servicio permite solo una simulación activa y dos en cola.

## Control de acceso de la beta

El backend **no tiene autenticación**. Conocer un ID permite leer o cancelar
una ejecución; además, cualquier visitante con acceso a `POST /api/ejecuciones`
puede consumir cómputo. No equivale a seguridad que el ID sea largo o que el
backend escuche solo en loopback si nginx publica la API.

Antes de abrir la beta a Internet, el encargado debe limitar el acceso al
sitio completo mediante HTTPS y, por ejemplo, autenticación básica de nginx
con un archivo de contraseñas fuera del repositorio o una lista de IPs. Esas
medidas deben cubrir `/`, `/api/` y `/ws/`; no poner claves en el frontend.
Si se desea una versión pública sin barrera, primero hay que diseñar
autorización y límites por cliente para las operaciones costosas y mutantes.

## Vuelta atrás

Si falla salud, lectura de datos, proxy WebSocket o la prueba entre dos
dispositivos, restaurar el HTML y JAR respaldados, reiniciar el servicio y
registrar el fallo. No borrar los archivos de datos ni cambiar el Tomcat 8080.
