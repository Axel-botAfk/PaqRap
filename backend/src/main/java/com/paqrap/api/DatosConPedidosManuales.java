package com.paqrap.api;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Vehiculo;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Los pedidos individuales se suman tanto a MySQL como a los archivos/cargas mensuales. */
final class DatosConPedidosManuales implements DatosFuente {
    private final DatosFuente base;
    private final PedidosManuales manuales;

    DatosConPedidosManuales(DatosFuente base, PedidosManuales manuales) {
        this.base = base;
        this.manuales = manuales;
    }

    @Override public String tipo() { return base.tipo(); }
    @Override public List<String> periodos() {
        var meses = new TreeSet<>(base.periodos());
        for (Pedido p : manuales.todos()) meses.add(DatosConCargas.aaaamm(YearMonth.from(p.getFechaRegistro())));
        return List.copyOf(meses);
    }

    @Override public ApiModels.PeriodoVista periodo(String valor) {
        YearMonth mes = DatosService.parsearPeriodo(valor);
        var extra = manuales.delMes(mes);
        if (extra.isEmpty()) return base.periodo(valor);
        ApiModels.PeriodoVista anterior = base.periodos().contains(valor) ? base.periodo(valor) : null;
        var entorno = base.entorno(mes);
        int jornadas = DatosConCargas.jornadasDelMes(entorno.mantenimiento(), mes);
        var primera = extra.get(0).getFechaRegistro().toLocalDate();
        if (anterior != null && anterior.primeraFechaPedido() != null
                && anterior.primeraFechaPedido().isBefore(primera)) primera = anterior.primeraFechaPedido();
        return new ApiModels.PeriodoVista(valor, extra.size() + (anterior == null ? 0 : anterior.pedidos()),
                entorno.bloqueos().size(), jornadas,
                jornadas > 0 || (anterior != null && anterior.mantenimientoDisponible()), primera);
    }

    @Override public ApiModels.MapaDatosVista mapa(String valor) {
        if (manuales.delMes(DatosService.parsearPeriodo(valor)).isEmpty()) return base.mapa(valor);
        return new ApiModels.MapaDatosVista(base.almacenes().stream().map(Vistas::almacen).toList(),
                base.entorno(DatosService.parsearPeriodo(valor)).bloqueos().stream().map(Vistas::bloqueo).toList());
    }

    @Override public DatosEntrada cargar(LocalDateTime inicio, int dias) {
        var meses = com.paqrap.datos.DatosReales.mesesQueCubren(inicio, dias);
        if (meses.stream().allMatch(m -> manuales.delMes(m).isEmpty())) return base.cargar(inicio, dias);
        List<Pedido> pedidos = new ArrayList<>();
        List<com.paqrap.modelo.Bloqueo> bloqueos = new ArrayList<>();
        PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
        var disponibles = base.periodos();
        for (YearMonth mes : meses) {
            DatosEntrada entrada = disponibles.contains(DatosConCargas.aaaamm(mes))
                    ? base.cargar(mes.atDay(1).atStartOfDay(), mes.lengthOfMonth()) : base.entorno(mes);
            pedidos.addAll(entrada.ventas());
            pedidos.addAll(manuales.delMes(mes));
            bloqueos.addAll(entrada.bloqueos());
            mantenimiento = mantenimiento.mas(entrada.mantenimiento());
        }
        if (pedidos.stream().noneMatch(p -> !p.getFechaRegistro().isBefore(inicio)
                && p.getFechaRegistro().isBefore(inicio.plusDays(dias)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_PEDIDOS", "No hay pedidos en las fechas seleccionadas.");
        }
        return new DatosEntrada(DatosFuente.distinguirMeses(pedidos), List.copyOf(bloqueos), mantenimiento);
    }

    @Override public DatosEntrada entorno(YearMonth mes) { return base.entorno(mes); }
    @Override public List<Almacen> almacenes() { return base.almacenes(); }
    @Override public List<Vehiculo> flota() { return base.flota(); }
}
