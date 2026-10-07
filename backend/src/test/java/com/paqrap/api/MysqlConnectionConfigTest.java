package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class MysqlConnectionConfigTest {
    @TempDir Path tempDir;

    @Test
    void cargaArchivoExternoYRechazaClaveIncorrecta() throws Exception {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);
        String encrypted = DbPasswordCipher.encrypt("contraseña-de-prueba", key);
        Path file = tempDir.resolve("db.properties");
        Files.writeString(file, "db.host=127.0.0.1\n"
                + "db.port=3307\n"
                + "db.name=paqrap\n"
                + "db.user=paqrap_app\n"
                + "db.password.encrypted=" + encrypted + "\n");

        MysqlConnectionConfig.DbSettings settings = MysqlConnectionConfig.load(file, key);
        assertEquals("jdbc:mysql://127.0.0.1:3307/paqrap", settings.url());
        assertEquals("paqrap_app", settings.user());
        assertEquals("contraseña-de-prueba", settings.password());

        byte[] otherBytes = new byte[32];
        new SecureRandom().nextBytes(otherBytes);
        String wrongKey = Base64.getEncoder().encodeToString(otherBytes);
        assertThrows(IllegalStateException.class, () -> MysqlConnectionConfig.load(file, wrongKey));
    }
}
