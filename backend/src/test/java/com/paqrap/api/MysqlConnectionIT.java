package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Ejecutar explícitamente con -Pmysql -Dtest=MysqlConnectionIT. Solo hace SELECT 1. */
@SpringBootTest(properties = "spring.profiles.active=mysql")
class MysqlConnectionIT {
    @Autowired DataSource dataSource;

    @Test
    void cargaDescifraConectaYCierra() throws Exception {
        String location = System.getenv().getOrDefault("PAQRAP_DB_CONFIG", "db.properties");
        MysqlConnectionConfig.DbSettings settings = MysqlConnectionConfig.load(
                Path.of(location), System.getenv("PAQRAP_DB_KEY"));
        assertTrue(settings.url().startsWith("jdbc:mysql://"), "URL MySQL inválida");
        assertFalse(settings.user().isBlank(), "Usuario vacío");
        assertFalse(settings.password().isBlank(), "No se pudo descifrar la contraseña");

        try {
            Connection connection = dataSource.getConnection();
            try (connection; PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "MySQL no respondió");
                assertEquals(1, result.getInt(1));
            }
            assertTrue(connection.isClosed(), "La conexión no se cerró");
        } catch (SQLException e) {
            fail("Falló la conexión MySQL (SQLState " + e.getSQLState()
                    + "). Revisa host, puerto, usuario y contraseña.", e);
        }
        System.out.println("Conexión MySQL correcta; configuración, descifrado y cierre verificados.");
    }
}
