package com.paqrap.api;

import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;

/** Conciliación de contenido contra los archivos montados, sin modificar datos ni aceptar rutas HTTP. */
@RestController
@RequestMapping("/api/datos/auditoria")
final class AuditoriaDatosController {
    private final DatosFuente fuente;
    private final Path carpeta;
    AuditoriaDatosController(DatosFuente fuente, @Value("${paqrap.data-dir}") String carpeta) {
        this.fuente = fuente;
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
    }
    public record Comparacion(int oficiales, int cargados, boolean mismoContenido) { }
    public record Archivo(String nombre, String sha256, boolean presente) { }
    public record Informe(String periodo, String fuente, Comparacion pedidos,
            Comparacion bloqueos, Comparacion mantenimiento, List<Archivo> archivos,
            boolean conforme) { }

    @GetMapping("/{periodo}")
    Informe consultar(@PathVariable String periodo) {
        YearMonth mes = DatosService.parsearPeriodo(periodo);
        try {
            var oficial = DatosReales.cargar(carpeta, mes);
            var inicio = mes.atDay(1).atStartOfDay();
            var fin = mes.plusMonths(1).atDay(1).atStartOfDay();
            var cargado = fuente.cargar(inicio, mes.lengthOfMonth());
            var pedidos = comparar(ventas(oficial.ventas(), inicio, fin), ventas(cargado.ventas(), inicio, fin));
            var bloqueos = comparar(bloqueos(oficial.bloqueos(), inicio, fin), bloqueos(cargado.bloqueos(), inicio, fin));
            var mantenimiento = comparar(mantenimiento(oficial.mantenimiento(), mes), mantenimiento(cargado.mantenimiento(), mes));
            var archivos = List.of(archivo(oficial.archivoVentas()), archivo(oficial.archivoBloqueos()),
                    archivo(oficial.archivoMantenimiento()));
            return new Informe(periodo, fuente.tipo(), pedidos, bloqueos, mantenimiento, archivos,
                    pedidos.mismoContenido() && bloqueos.mismoContenido() && mantenimiento.mismoContenido());
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ARCHIVOS_OFICIALES_NO_DISPONIBLES",
                    "Monte los archivos oficiales del período para comparar su contenido con la fuente activa.");
        }
    }
    static Comparacion comparar(List<String> a, List<String> b) {
        return new Comparacion(a.size(), b.size(), a.stream().sorted().toList().equals(b.stream().sorted().toList()));
    }
    private static List<String> ventas(List<Pedido> datos, LocalDateTime inicio, LocalDateTime fin) {
        return datos.stream().filter(p -> !p.getFechaRegistro().isBefore(inicio) && p.getFechaRegistro().isBefore(fin))
                .map(p -> p.getClienteId()+"|"+p.getDestino()+"|"+p.getCantidad()+"|"+p.getFechaRegistro()+"|"+p.getHorasPlazo()).toList();
    }
    private static List<String> bloqueos(List<Bloqueo> datos, LocalDateTime inicio, LocalDateTime fin) {
        return datos.stream().filter(b -> b.inicio().isBefore(fin) && b.fin().isAfter(inicio))
                .map(b -> b.inicio()+"|"+b.fin()+"|"+b.nodos().stream().map(Object::toString).sorted().toList()).toList();
    }
    private static List<String> mantenimiento(PlanMantenimiento plan, YearMonth mes) {
        List<String> filas = new ArrayList<>();
        for (int dia=1; dia<=mes.lengthOfMonth(); dia++) {
            var fecha = mes.atDay(dia);
            for (String unidad : plan.unidadesDelDia(fecha)) filas.add(fecha+"|"+unidad);
        }
        return filas;
    }
    private static Archivo archivo(Path path) throws java.io.IOException {
        if (!Files.isRegularFile(path)) return new Archivo(path.getFileName().toString(), null, false);
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            try (var entrada = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n=entrada.read(buffer))!=-1) hash.update(buffer,0,n);
            }
            return new Archivo(path.getFileName().toString(), HexFormat.of().formatHex(hash.digest()), true);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
