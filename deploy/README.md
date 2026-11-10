# Despliegue de PaqRap

Esta carpeta contiene ejemplos de configuración para la VM del curso. El
servidor lo administra otro integrante del equipo; **ninguna instalación en
la VM se realiza desde esta rama**.

La arquitectura prevista es nginx (frontend estático en `/var/www/paqrap`),
API y WebSocket redirigidos al backend Java en `127.0.0.1:8081`, y datos del
curso en `/opt/paqrap/datos/reales`. El Tomcat del curso en 8080 no se toca.

| Archivo del repositorio | Destino sugerido en la VM |
| --- | --- |
| `nginx/paqrap.conf` | `/etc/nginx/sites-available/paqrap` |
| `systemd/paqrap.service` | `/etc/systemd/system/paqrap.service` |
| `backend/target/paqrap-backend-1.0-SNAPSHOT.jar` | `/opt/paqrap/paqrap.jar` |
| `frontend/dist/` | `/var/www/paqrap/` |
| `codigo/paqrap/datos/reales/` | `/opt/paqrap/datos/reales/` |

La configuración real de nginx, HTTPS, el usuario del servicio y cualquier
política de acceso deben revisarse con el responsable antes de reemplazar el
HTML que ya está publicado. No subir llaves, contraseñas ni `.env` al repo.

### Carga masiva de ventas (una sola vez en la VM)

El workflow de `main` no toca nginx ni systemd, así que para que la pantalla «Carga masiva»
funcione en la VM hay que aplicar a mano estos dos cambios:

1. Copiar el bloque `location = /api/datos/ventas` de `nginx/paqrap.conf` al sitio real
   (permite 4 MB solo en esa ruta; el resto de `/api/` sigue en 16 KB) y recargar:
   `sudo nginx -t && sudo systemctl reload nginx`. Sin esto, la carga responde 413.
2. Crear la carpeta donde el servidor guarda los archivos cargados, fuera de
   `datos/reales` (el despliegue la vacía): `sudo install -d -o paqrap -g paqrap /opt/paqrap/cargas`.
   Con `WorkingDirectory=/opt/paqrap` el backend ya la usa por defecto; si no puede
   escribir en ella, la carga queda solo en memoria y la pantalla lo avisa.

**No existe todavía un workflow de GitHub Actions para despliegue automático.**
La preparación y verificación manual está en [BETA_ENTREGA.md](BETA_ENTREGA.md).
