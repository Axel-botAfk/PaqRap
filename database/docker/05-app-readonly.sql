-- Docker crea paqrap_app con permisos amplios antes de ejecutar los scripts de inicio.
-- El backend actual solo lee entradas y catalogos, por lo que restringimos la cuenta.
REVOKE ALL PRIVILEGES ON paqrap.* FROM 'paqrap_app'@'%';
GRANT SELECT ON paqrap.* TO 'paqrap_app'@'%';
