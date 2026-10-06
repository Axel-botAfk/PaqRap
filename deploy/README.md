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

**No existe todavía un workflow de GitHub Actions para despliegue automático.**
La preparación y verificación manual está en [BETA_ENTREGA.md](BETA_ENTREGA.md).
