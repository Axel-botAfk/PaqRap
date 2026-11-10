package com.paqrap.api;

import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carga masiva del archivo mensual de ventas desde la pantalla de Pedidos.
 *
 * Un archivo cargado reemplaza los pedidos de ese mes de la fuente base (archivos del curso o
 * MySQL); los bloqueos y el mantenimiento siguen saliendo de la fuente base. Se guarda tal como
 * llegó en {@code paqrap.upload-dir}, con un nombre que arma el servidor a partir del periodo
 * —nunca con el nombre que manda el cliente—, y se vuelve a leer al arrancar. Si la carpeta no
 * está configurada o no se puede escribir, la carga vive en memoria y la respuesta lo dice.
 *
 * Las reglas de validación son las mismas de la pantalla ({@code pedidos.js}): las líneas con
 * error se descartan y se informan, y el correlativo avanza con cada línea leída, de modo que
 * los identificadores coinciden con los que muestra la vista previa.
 */
@Service
final class CargasDePedidos {
    static final int MAX_CARACTERES = 4 * 1024 * 1024;
    static final int MAX_LINEAS = 100_000;
    private static final int MAX_ERRORES_INFORMADOS = 50;
    private static final Pattern REGISTRO = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:(-?\\d+),(-?\\d+),([^,]+),(\\d+),(\\d+)$");
    private static final Logger LOG = LoggerFactory.getLogger(CargasDePedidos.class);

    record ErrorDeLinea(int numero, String linea, String motivo) { }

    record Validacion(List<Pedido> pedidos, List<ErrorDeLinea> errores, int leidas) { }

    record Carga(YearMonth periodo, String nombre, List<Pedido> pedidos, int leidas,
                 List<ErrorDeLinea> errores, LocalDateTime cargadoEn, boolean persistente) { }

    private final Path carpeta;
    private final Map<YearMonth, Carga> cargas = new ConcurrentSkipListMap<>();

    CargasDePedidos(@Value("${paqrap.upload-dir:}") String carpetaConfigurada) {
        this.carpeta = carpetaConfigurada == null || carpetaConfigurada.isBlank() ? null
                : Path.of(carpetaConfigurada).toAbsolutePath().normalize();
        recuperar();
    }

    Optional<Carga> de(YearMonth periodo) { return Optional.ofNullable(cargas.get(periodo)); }

    List<Carga> todas() { return List.copyOf(cargas.values()); }

    synchronized Carga guardar(YearMonth periodo, String nombre, String texto) {
        if (texto == null || texto.isBlank()) {
            throw invalido("El archivo está vacío.");
        }
        if (texto.length() > MAX_CARACTERES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
                    "El archivo supera los 4 MB permitidos.");
        }
        String normalizado = texto.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        Validacion validacion = validar(normalizado, periodo);
        if (validacion.pedidos().isEmpty()) {
            throw invalido("Ninguna línea del archivo es un pedido válido para "
                    + periodo + "; revise el formato y el periodo.");
        }
        boolean persistente = escribir(periodo, normalizado);
        Carga carga = new Carga(periodo, limpiarNombre(nombre), validacion.pedidos(),
                validacion.leidas(), validacion.errores(), LocalDateTime.now(), persistente);
        cargas.put(periodo, carga);
        return carga;
    }

    synchronized boolean eliminar(YearMonth periodo) {
        Carga quitada = cargas.remove(periodo);
        if (carpeta != null) {
            try { Files.deleteIfExists(archivo(periodo)); }
            catch (IOException e) { LOG.warn("No se pudo borrar la carga de {}", periodo, e); }
        }
        return quitada != null;
    }

    /** Recorre todas las líneas y junta los fallos; no se detiene en el primero. */
    static Validacion validar(String texto, YearMonth periodo) {
        String[] lineas = texto.split("\n", -1);
        if (lineas.length > MAX_LINEAS) {
            throw invalido("El archivo tiene más de " + MAX_LINEAS + " líneas.");
        }
        List<Pedido> pedidos = new ArrayList<>();
        List<ErrorDeLinea> errores = new ArrayList<>();
        int correlativo = 0;
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isEmpty() || linea.startsWith("#")) continue;
            correlativo++;
            String motivo = motivoDeRechazo(linea, periodo);
            if (motivo != null) {
                errores.add(new ErrorDeLinea(i + 1, recortar(linea, 120), motivo));
                continue;
            }
            pedidos.add(LectorVentas.parsear(linea, periodo, correlativo));
        }
        return new Validacion(List.copyOf(pedidos), List.copyOf(errores), correlativo);
    }

    private static String motivoDeRechazo(String linea, YearMonth periodo) {
        Matcher m = REGISTRO.matcher(linea);
        if (!m.matches()) return "No tiene el formato ##d##h##m:posX,posY,cliente,qq,hl.";
        try {
            int dia = Integer.parseInt(m.group(1));
            int hora = Integer.parseInt(m.group(2));
            int minuto = Integer.parseInt(m.group(3));
            int x = Integer.parseInt(m.group(4));
            int y = Integer.parseInt(m.group(5));
            String cliente = m.group(6).trim();
            long cantidad = Long.parseLong(m.group(7));
            long horas = Long.parseLong(m.group(8));
            if (dia < 1 || dia > periodo.lengthOfMonth()) {
                return "El día " + dia + " no existe en el periodo " + periodo + ".";
            }
            if (hora > 23 || minuto > 59) return "La hora " + hora + ":" + minuto + " no es válida.";
            if (!Ciudad.contiene(x, y)) {
                return "El nodo (" + x + ", " + y + ") cae fuera de la retícula de "
                        + Ciudad.ANCHO_KM + " × " + Ciudad.ALTO_KM + ".";
            }
            if (cliente.isEmpty() || cliente.length() > 32) {
                return "El código de cliente debe tener entre 1 y 32 caracteres.";
            }
            if (cantidad <= 0 || cantidad > 100_000) return "La cantidad debe ser mayor que cero.";
            if (horas <= 0 || horas > 24 * 31) return "El plazo debe ser mayor que cero.";
            return null;
        } catch (NumberFormatException e) {
            return "Contiene un número fuera de rango.";
        }
    }

    private boolean escribir(YearMonth periodo, String texto) {
        if (carpeta == null) return false;
        try {
            Files.createDirectories(carpeta);
            Path destino = archivo(periodo);
            Path temporal = Files.createTempFile(carpeta, "carga-", ".tmp");
            Files.writeString(temporal, texto, StandardCharsets.UTF_8);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("No se pudo guardar la carga de {} en {}; queda solo en memoria", periodo, carpeta, e);
            return false;
        }
    }

    private void recuperar() {
        if (carpeta == null || !Files.isDirectory(carpeta)) return;
        try (var archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.toList()) {
                String nombre = archivo.getFileName().toString();
                if (!nombre.matches("ventas\\.\\d{6}\\.txt")) continue;
                try {
                    YearMonth periodo = DatosService.parsearPeriodo(nombre.substring(7, 13));
                    Validacion v = validar(Files.readString(archivo, StandardCharsets.UTF_8), periodo);
                    if (v.pedidos().isEmpty()) continue;
                    cargas.put(periodo, new Carga(periodo, nombre, v.pedidos(), v.leidas(),
                            v.errores(), LocalDateTime.ofInstant(
                                    Files.getLastModifiedTime(archivo).toInstant(),
                                    java.time.ZoneId.systemDefault()), true));
                } catch (IOException | RuntimeException e) {
                    LOG.warn("Se ignora la carga {}", archivo, e);
                }
            }
        } catch (IOException e) {
            LOG.warn("No se pudo leer la carpeta de cargas {}", carpeta, e);
        }
    }

    private Path archivo(YearMonth periodo) {
        return carpeta.resolve(String.format("ventas.%04d%02d.txt",
                periodo.getYear(), periodo.getMonthValue()));
    }

    static List<ErrorDeLinea> primeros(List<ErrorDeLinea> errores) {
        return errores.subList(0, Math.min(errores.size(), MAX_ERRORES_INFORMADOS));
    }

    private static String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "ventas";
        return recortar(nombre.replaceAll("[\\p{Cntrl}/\\\\]", "").trim(), 120);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo) + "…";
    }

    private static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ARCHIVO_INVALIDO", mensaje);
    }
}
