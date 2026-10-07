package com.paqrap.api;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuración externa del perfil MySQL; conserva el DataSource usado por MysqlDatosService. */
@Configuration
@Profile("mysql")
class MysqlConnectionConfig {
    @Bean
    DataSource dataSource() throws IOException {
        String location = System.getenv().getOrDefault("PAQRAP_DB_CONFIG", "db.properties");
        DbSettings settings = load(Path.of(location), System.getenv("PAQRAP_DB_KEY"));
        return DataSourceBuilder.create()
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .url(settings.url())
                .username(settings.user())
                .password(settings.password())
                .build();
    }

    static DbSettings load(Path path, String key) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("No existe db.properties en " + path.toAbsolutePath()
                    + "; configura PAQRAP_DB_CONFIG.");
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        String host = required(properties, "db.host");
        String port = required(properties, "db.port");
        String name = required(properties, "db.name");
        String user = required(properties, "db.user");
        String encrypted = required(properties, "db.password.encrypted");
        int parsedPort;
        try {
            parsedPort = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("db.port debe ser un número válido.");
        }
        if (parsedPort < 1 || parsedPort > 65535) {
            throw new IllegalArgumentException("db.port debe estar entre 1 y 65535.");
        }
        if (!host.matches("[A-Za-z0-9.-]+") || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("db.host o db.name tiene un formato inválido.");
        }
        String generatedUrl = "jdbc:mysql://" + host + ":" + parsedPort + "/" + name;
        String configuredUrl = properties.getProperty("db.url", "").trim();
        String url = configuredUrl.isEmpty() ? generatedUrl : configuredUrl;
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("db.url debe usar jdbc:mysql://.");
        }
        return new DbSettings(url, user, DbPasswordCipher.decrypt(encrypted, key));
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta " + name + " en db.properties.");
        }
        return value.trim();
    }

    record DbSettings(String url, String user, String password) { }
}
