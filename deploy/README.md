# deploy

Copias de la configuración del servidor (máquina virtual de la PUCP) y guía del despliegue.

- **Responsables:** Leslie y Axel.
- **Atención:** aquí **no** va ninguna contraseña ni llave. Solo configuración sin secretos.

## Cómo está armado el servidor

```
Internet ──► nginx (80/443, HTTPS con Certbot)
               ├── /          → archivos estáticos del frontend  (/var/www/paqrap)
               ├── /api/...   → backend Spring Boot en 127.0.0.1:8081
               └── /ws...     → backend (WebSocket) en 127.0.0.1:8081
```

- El backend corre como servicio `paqrap` (systemd) con el usuario `paqrap`, desde `/opt/paqrap/paqrap.jar`.
- El Tomcat que ya existe en el puerto 8080 pertenece al curso y no se toca.
- Solo los puertos 22, 80 y 443 son accesibles desde fuera.

## Archivos de esta carpeta

| Archivo | Se instala en el servidor como |
|---|---|
| `nginx/paqrap.conf` | `/etc/nginx/sites-available/paqrap` |
| `systemd/paqrap.service` | `/etc/systemd/system/paqrap.service` |

`nginx/paqrap.conf` es la parte HTTP. Certbot agrega solo el bloque HTTPS al ejecutar `certbot --nginx`.

## Despliegue automático (GitHub Actions)

Cada push a `main` compila el backend y el frontend en GitHub, copia el resultado al servidor por SSH y reinicia el servicio.

Secretos requeridos en GitHub (Settings → Secrets and variables → Actions):

| Secreto | Contenido |
|---|---|
| `SSH_HOST` | Nombre DNS de la máquina virtual |
| `SSH_USER` | Usuario SSH del servidor |
| `SSH_PRIVATE_KEY` | Llave privada dedicada al despliegue (solo en GitHub) |

El usuario SSH solo puede ejecutar sin contraseña tres comandos: `systemctl restart paqrap`, `systemctl reload nginx` y `nginx -t` (archivo `/etc/sudoers.d/paqrap` en el servidor).

## Reglas

- Nunca subir contraseñas, llaves privadas ni archivos `.env`.
- Los cambios al servidor se documentan aquí, para poder reconstruirlo si hace falta.
