package com.paqrap.api;

import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ubicacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Altas individuales: se suman a la fuente base, sin reemplazar ventas ni escribir SQL. */
@Service
final class PedidosManuales {
    private final Path carpeta;
    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();

    PedidosManuales(@Value("${paqrap.upload-dir:}") String ruta) {
        carpeta = ruta == null || ruta.isBlank() ? null : Path.of(ruta).toAbsolutePath().normalize();
        recuperar();
    }

    static Pedido validar(ApiModels.NuevoPedidoRequest r) {
        if (r == null || r.solicitudId() == null || r.destino() == null || r.cliente() == null
                || r.cliente().isBlank() || r.cliente().trim().length() > 32
                || r.cliente().contains(",") || r.cliente().codePoints().anyMatch(Character::isISOControl)
                || r.destino().x() == null || r.destino().y() == null
                || !Ciudad.contiene(r.destino().x(), r.destino().y())
                || r.cantidad() == null || r.cantidad() < 1 || r.cantidad() > 100_000
                || r.horasPlazo() == null || !List.of(4, 8, 12, 18, 36).contains(r.horasPlazo())
                || r.fechaRegistro() == null || r.fechaRegistro().getYear() < 1
                || r.fechaRegistro().getYear() > 9999 || r.fechaRegistro().getSecond() != 0
                || r.fechaRegistro().getNano() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO",
                    "Revise cliente (1 a 32 caracteres, sin comas ni saltos), nodo, cantidad "
                            + "(1 a 100000), plazo (4, 8, 12, 18 o 36 h) y fecha con precisión de minutos.");
        }
        return new Pedido("MAN-" + r.solicitudId(), r.cliente().trim(),
                new Ubicacion(r.destino().x(), r.destino().y()), r.cantidad(),
                r.fechaRegistro(), r.horasPlazo());
    }

    synchronized Pedido guardar(ApiModels.NuevoPedidoRequest solicitud) {
        Pedido pedido = validar(solicitud);
        Pedido anterior = pedidos.get(pedido.getId());
        if (anterior != null) {
            if (!LectorVentas.aRegistro(anterior).equals(LectorVentas.aRegistro(pedido))
                    || !anterior.getFechaRegistro().equals(pedido.getFechaRegistro())) {
                throw new ApiException(HttpStatus.CONFLICT, "SOLICITUD_REUTILIZADA",
                        "La solicitud ya se utilizó para otro pedido. Actualice el formulario.");
            }
            return anterior;
        }
        List<Pedido> mes = new java.util.ArrayList<>(delMes(YearMonth.from(pedido.getFechaRegistro())));
        if (mes.size() >= CargasDePedidos.MAX_LINEAS) {
            throw new ApiException(HttpStatus.CONFLICT, "LIMITE_PEDIDOS", "Se alcanzó el límite de altas del mes.");
        }
        mes.add(pedido);
        if (carpeta != null) {
            String texto = mes.stream().map(p -> p.getId() + "\t" + LectorVentas.aRegistro(p))
                    .collect(java.util.stream.Collectors.joining("\n"));
            ArchivosDePedidos.escribir(carpeta, archivo(YearMonth.from(pedido.getFechaRegistro())), texto);
        }
        pedidos.put(pedido.getId(), pedido);
        return pedido;
    }

    synchronized List<Pedido> todos() { return List.copyOf(pedidos.values()); }

    synchronized List<Pedido> delMes(YearMonth mes) {
        return pedidos.values().stream().filter(p -> YearMonth.from(p.getFechaRegistro()).equals(mes))
                .sorted(Comparator.comparing(Pedido::getFechaRegistro).thenComparing(Pedido::getId)).toList();
    }

    boolean persistente() { return carpeta != null; }

    ApiModels.PedidoRegistradoVista vista(Pedido p, UUID ejecucion) {
        return new ApiModels.PedidoRegistradoVista(p.getId(), p.getClienteId(), Vistas.coordenada(p.getDestino()),
                p.getCantidad(), p.getHorasPlazo(), p.getFechaRegistro(), p.getFechaLimite(), persistente(), ejecucion);
    }

    private Path archivo(YearMonth mes) { return carpeta.resolve("pedidos." + DatosConCargas.aaaamm(mes) + ".txt"); }

    private void recuperar() {
        if (carpeta == null || !Files.isDirectory(carpeta)) return;
        try (var archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.filter(p -> p.getFileName().toString()
                    .matches("pedidos\\.\\d{6}\\.txt")).toList()) {
                YearMonth mes = DatosService.parsearPeriodo(archivo.getFileName().toString().substring(8, 14));
                for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                    if (linea.isBlank()) continue;
                    String[] campos = linea.split("\t", 2);
                    if (campos.length != 2 || !campos[0].startsWith("MAN-")) throw new IllegalArgumentException();
                    var v = CargasDePedidos.validar(campos[1], mes);
                    if (v.pedidos().size() != 1 || !v.errores().isEmpty()) throw new IllegalArgumentException();
                    Pedido p = v.pedidos().get(0);
                    Pedido restaurado = validar(new ApiModels.NuevoPedidoRequest(UUID.fromString(campos[0].substring(4)),
                            p.getClienteId(), new ApiModels.NodoPedidoRequest(p.getDestino().x(), p.getDestino().y()), p.getCantidad(), p.getHorasPlazo(),
                            p.getFechaRegistro(), null));
                    pedidos.put(restaurado.getId(), restaurado);
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // Arrancar sin avisar ocultaría pedidos previamente aceptados.
            throw new IllegalStateException("No se pudieron recuperar los pedidos manuales; revise la carpeta de cargas.", e);
        }
    }
}
