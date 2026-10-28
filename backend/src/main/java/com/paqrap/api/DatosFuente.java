package com.paqrap.api;

import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.List;

/** Entrada del simulador: archivos por defecto o MySQL con el perfil mysql. */
interface DatosFuente {
    String tipo();
    List<String> periodos();
    PeriodoVista periodo(String aaaamm);
    MapaDatosVista mapa(String aaaamm);
    /**
     * Datos del horizonte. Con {@code exigirPedidos} en falso se admite un horizonte sin ventas
     * registradas: es el caso del día a día en tiempo real, que arranca en el instante presente
     * con la cola vacía y se va llenando con los pedidos que entran por la API mientras corre.
     */
    DatosEntrada cargar(LocalDateTime inicio, int dias, boolean exigirPedidos);

    default DatosEntrada cargar(LocalDateTime inicio, int dias) {
        return cargar(inicio, dias, true);
    }
    List<Almacen> almacenes();
    List<Vehiculo> flota();

    record DatosEntrada(List<Pedido> ventas, List<Bloqueo> bloqueos,
                        PlanMantenimiento mantenimiento) { }
}
