package com.paqrap.api;

import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Una respuesta de éxito nunca oculta un fallo al guardar en la carpeta configurada. */
final class ArchivosDePedidos {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ArchivosDePedidos.class);
    private ArchivosDePedidos() { }

    static void escribir(Path carpeta, Path destino, String texto) {
        Path temporal = null;
        try {
            Files.createDirectories(carpeta);
            temporal = Files.createTempFile(carpeta, "pedido-", ".tmp");
            Files.writeString(temporal, texto, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("No se pudo persistir una carga de pedidos", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CARGA_NO_GUARDADA",
                    "No se pudo guardar el pedido o archivo en el servidor. No se aplicaron los cambios.");
        } finally {
            if (temporal != null) try { Files.deleteIfExists(temporal); } catch (IOException ignored) { }
        }
    }
}
