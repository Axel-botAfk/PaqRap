package com.paqrap.api;

import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Vehiculo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Fuente de datos que ven la API y las corridas: la base (archivos o MySQL) con los meses de
 * ventas cargados desde la pantalla. Un mes cargado reemplaza los pedidos de la base; los
 * bloqueos y el mantenimiento de ese mes siguen siendo los de la base, si existen.
 */
final class DatosConCargas implements DatosFuente {
    private final DatosFuente base;
    private final CargasDePedidos cargas;

    DatosConCargas(DatosFuente base, CargasDePedidos cargas) {
        this.base = base;
        this.cargas = cargas;
    }

    @Configuration
    static class Configuracion {
        @Bean
        @Primary
        DatosFuente datosFuente(ObjectProvider<DatosService> archivos,
                                ObjectProvider<MysqlDatosService> mysql, CargasDePedidos cargas) {
            MysqlDatosService deMysql = mysql.getIfAvailable();
            DatosFuente base = deMysql != null ? deMysql : archivos.getObject();
            return new DatosConCargas(base, cargas);
        }
    }

    @Override public String tipo() { return base.tipo(); }

    @Override
    public List<String> periodos() {
        TreeSet<String> meses = new TreeSet<>();
        try { meses.addAll(base.periodos()); }
        catch (ApiException e) { if (cargas.todas().isEmpty()) throw e; }
        for (var carga : cargas.todas()) meses.add(aaaamm(carga.periodo()));
        return List.copyOf(meses);
    }

    @Override
    public PeriodoVista periodo(String aaaamm) {
        YearMonth mes = DatosService.parsearPeriodo(aaaamm);
        var carga = cargas.de(mes);
        if (carga.isEmpty()) return base.periodo(aaaamm);
        DatosEntrada entorno = base.entorno(mes);
        List<Pedido> pedidos = carga.get().pedidos();
        LocalDate primera = pedidos.stream().map(p -> p.getFechaRegistro().toLocalDate())
                .min(LocalDate::compareTo).orElse(null);
        int jornadas = jornadasDelMes(entorno.mantenimiento(), mes);
        return new PeriodoVista(aaaamm, pedidos.size(), entorno.bloqueos().size(), jornadas,
                jornadas > 0, primera);
    }

    @Override
    public MapaDatosVista mapa(String aaaamm) {
        YearMonth mes = DatosService.parsearPeriodo(aaaamm);
        if (cargas.de(mes).isEmpty()) return base.mapa(aaaamm);
        return new MapaDatosVista(base.almacenes().stream().map(Vistas::almacen).toList(),
                base.entorno(mes).bloqueos().stream().map(Vistas::bloqueo).toList());
    }

    @Override
    public DatosEntrada cargar(LocalDateTime inicio, int dias) {
        List<YearMonth> meses = DatosReales.mesesQueCubren(inicio, dias);
        if (meses.stream().noneMatch(mes -> cargas.de(mes).isPresent())) {
            return base.cargar(inicio, dias);
        }
        List<Pedido> ventas = new ArrayList<>();
        List<Bloqueo> bloqueos = new ArrayList<>();
        PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
        for (YearMonth mes : meses) {
            var carga = cargas.de(mes);
            DatosEntrada entorno = base.entorno(mes);
            if (carga.isPresent()) {
                ventas.addAll(carga.get().pedidos());
            } else {
                try {
                    ventas.addAll(base.cargar(mes.atDay(1).atStartOfDay(), mes.lengthOfMonth()).ventas());
                } catch (ApiException sinDatos) {
                    // Un mes sin ventas en la base, al lado de uno cargado, no aporta pedidos.
                }
            }
            bloqueos.addAll(entorno.bloqueos());
            mantenimiento = mantenimiento.mas(entorno.mantenimiento());
        }
        LocalDateTime fin = inicio.plusDays(dias);
        if (ventas.stream().noneMatch(p -> !p.getFechaRegistro().isBefore(inicio)
                && p.getFechaRegistro().isBefore(fin))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_PEDIDOS",
                    "No hay pedidos en las fechas seleccionadas.");
        }
        return new DatosEntrada(List.copyOf(ventas), List.copyOf(bloqueos), mantenimiento);
    }

    @Override public DatosEntrada entorno(YearMonth mes) { return base.entorno(mes); }

    @Override public List<Almacen> almacenes() { return base.almacenes(); }

    @Override public List<Vehiculo> flota() { return base.flota(); }

    private static int jornadasDelMes(PlanMantenimiento plan, YearMonth mes) {
        int jornadas = 0;
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            if (!plan.unidadesDelDia(dia).isEmpty()) jornadas++;
        }
        return jornadas;
    }

    static String aaaamm(YearMonth mes) {
        return String.format("%04d%02d", mes.getYear(), mes.getMonthValue());
    }
}
