package com.paqrap.api;

import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Vehiculo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Solo acepta periodos calculados a partir de fechas, nunca rutas proporcionadas por HTTP. */
@Service
@Profile("!mysql")
final class DatosService implements DatosFuente {
    private final Path carpeta;

    DatosService(@Value("${paqrap.data-dir}") String carpetaConfigurada) {
        this.carpeta = Path.of(carpetaConfigurada).toAbsolutePath().normalize();
    }

    Path carpeta() { return carpeta; }

    public String tipo() { return "ARCHIVOS"; }

    public List<String> periodos() {
        try {
            if (!Files.isDirectory(carpeta)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DATOS_NO_DISPONIBLES",
                        "La carpeta de datos no está disponible.");
            }
            return DatosReales.mesesDisponibles(carpeta).stream()
                    .map(mes -> String.format("%04d%02d", mes.getYear(), mes.getMonthValue()))
                    .toList();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DATOS_NO_DISPONIBLES",
                    "No se pudo consultar el catálogo de datos.");
        }
    }

    public PeriodoVista periodo(String aaaamm) {
        YearMonth mes = parsearPeriodo(aaaamm);
        try {
            DatosReales datos = DatosReales.cargar(carpeta, mes);
            return new PeriodoVista(aaaamm, datos.ventas().size(), datos.bloqueos().size(),
                    datos.mantenimiento().getCantidadDeJornadas(),
                    Files.isRegularFile(datos.archivoMantenimiento()),
                    datos.ventas().stream().map(p -> p.getFechaRegistro().toLocalDate())
                            .min(java.time.LocalDate::compareTo).orElse(null));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERIODO_NO_DISPONIBLE",
                    "Faltan archivos de ventas o bloqueos para ese periodo.");
        }
    }

    public MapaDatosVista mapa(String aaaamm) {
        YearMonth mes = parsearPeriodo(aaaamm);
        try {
            DatosReales datos = DatosReales.cargar(carpeta, mes);
            return new MapaDatosVista(almacenes().stream().map(Vistas::almacen).toList(),
                    datos.bloqueos().stream().map(Vistas::bloqueo).toList());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERIODO_NO_DISPONIBLE",
                    "Faltan archivos para ese periodo.");
        }
    }

    public List<Almacen> almacenes() { return DatosCaso.almacenes(); }

    public List<Vehiculo> flota() { return DatosCaso.flota(); }

    public DatosEntrada cargar(LocalDateTime inicio, int dias) {
        List<Pedido> ventas = new ArrayList<>();
        List<Bloqueo> bloqueos = new ArrayList<>();
        PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
        try {
            YearMonth ultimo = YearMonth.from(inicio.plusDays(dias).minusNanos(1));
            for (YearMonth mes = YearMonth.from(inicio); !mes.isAfter(ultimo);
                    mes = mes.plusMonths(1)) {
                DatosReales datos = DatosReales.cargar(carpeta, mes);
                ventas.addAll(datos.ventas());
                bloqueos.addAll(datos.bloqueos());
                mantenimiento = mantenimiento.mas(datos.mantenimiento());
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATOS_INCOMPLETOS",
                    "No hay datos de ventas y bloqueos para todo el horizonte solicitado.");
        }
        if (ventas.stream().noneMatch(p -> !p.getFechaRegistro().isBefore(inicio)
                && p.getFechaRegistro().isBefore(inicio.plusDays(dias)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_PEDIDOS",
                    "No hay pedidos en las fechas seleccionadas.");
        }
        return new DatosEntrada(List.copyOf(ventas), List.copyOf(bloqueos), mantenimiento);
    }

    static YearMonth parsearPeriodo(String valor) {
        if (!valor.matches("[0-9]{6}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERIODO_INVALIDO",
                    "El periodo debe tener formato aaaamm.");
        }
        try {
            return YearMonth.of(Integer.parseInt(valor.substring(0, 4)),
                    Integer.parseInt(valor.substring(4, 6)));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERIODO_INVALIDO",
                    "El periodo indicado no es válido.");
        }
    }

}
