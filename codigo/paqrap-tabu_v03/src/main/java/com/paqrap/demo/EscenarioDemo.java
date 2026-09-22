package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CalculadorDistancia;
import com.paqrap.planificador.DistanciaManhattan;
import com.paqrap.planificador.EstadoOperacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Instancias de prueba sobre la ciudad real del caso.
 *
 * Antes cada demo inventaba su propio mapa y cargaba a mano una matriz de distancias. Con la
 * retícula, las distancias se calculan solas y las instancias se reducen a dónde están los
 * clientes y las unidades, de modo que conviene compartirlas.
 */
public final class EscenarioDemo {
    private final List<Almacen> almacenes;
    private final List<Vehiculo> vehiculos;
    private final List<Pedido> pedidos;
    private final EstadoOperacion estado;
    private final CalculadorDistancia distancias;

    private EscenarioDemo(
            LocalDateTime reloj,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos,
            List<Pedido> pedidos,
            CalculadorDistancia distancias
    ) {
        this.almacenes = almacenes;
        this.vehiculos = vehiculos;
        this.pedidos = pedidos;
        this.distancias = distancias;
        this.estado = new EstadoOperacion(reloj, pedidos, almacenes, vehiculos);
    }

    /**
     * Seis pedidos repartidos alrededor de los tres almacenes, con unidades ya situadas en
     * cada uno. Sirve para ejercitar la reasignación de almacén y de unidad.
     */
    public static EscenarioDemo pequeno(LocalDateTime reloj) {
        List<Vehiculo> vehiculos = List.of(
                unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL),
                unidad(TipoVehiculo.MOTO, 1, DatosCaso.UBICACION_NOR_OESTE),
                unidad(TipoVehiculo.MOTO, 2, DatosCaso.UBICACION_ESTE),
                unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_NOR_OESTE),
                unidad(TipoVehiculo.BICICLETA, 2, DatosCaso.UBICACION_ESTE)
        );

        List<Pedido> pedidos = List.of(
                pedido("P-001", 14, 40, 3, reloj, 8),
                pedido("P-002", 10, 41, 2, reloj, 12),
                pedido("P-003", 55, 29, 4, reloj, 8),
                pedido("P-004", 59, 24, 2, reloj, 18),
                pedido("P-005", 29, 16, 3, reloj, 36),
                pedido("P-006", 25, 11, 3, reloj, 12)
        );

        return new EscenarioDemo(reloj, DatosCaso.almacenes(), vehiculos, pedidos, new DistanciaManhattan());
    }

    /**
     * Trece pedidos sobre una zona más amplia, con la flota completa. El último excede la
     * capacidad de cualquier unidad y debe quedar sin asignar mientras no existan entregas
     * parciales.
     */
    public static EscenarioDemo mediano(LocalDateTime reloj) {
        List<Pedido> pedidos = List.of(
                pedido("P-001", 29, 16, 2, reloj, 4),
                pedido("P-002", 31, 12, 3, reloj, 8),
                pedido("P-003", 24, 19, 4, reloj, 12),
                pedido("P-004", 33, 20, 5, reloj, 8),
                pedido("P-005", 20, 10, 3, reloj, 18),
                pedido("P-006", 35, 8, 7, reloj, 8),
                pedido("P-007", 18, 22, 5, reloj, 12),
                pedido("P-008", 14, 36, 2, reloj, 18),
                pedido("P-009", 10, 40, 4, reloj, 36),
                pedido("P-010", 55, 30, 6, reloj, 36),
                pedido("P-011", 60, 25, 1, reloj, 18),
                pedido("P-012", 40, 18, 3, reloj, 12),
                pedido("P-013", 45, 43, 30, reloj, 36)
        );

        return new EscenarioDemo(reloj, DatosCaso.almacenes(), DatosCaso.flota(), pedidos, new DistanciaManhattan());
    }

    public static Pedido pedido(
            String id,
            int x,
            int y,
            int cantidad,
            LocalDateTime registro,
            int horasPlazo
    ) {
        return new Pedido(id, "c" + id, new Ubicacion(x, y), cantidad, registro, horasPlazo);
    }

    public static Vehiculo unidad(TipoVehiculo tipo, int numero, Ubicacion ubicacion) {
        return new Vehiculo(tipo.codigoUnidad(numero), tipo, EstadoVehiculo.DISPONIBLE, ubicacion);
    }

    public EstadoOperacion estado() {
        return estado;
    }

    public CalculadorDistancia distancias() {
        return distancias;
    }

    public List<Almacen> almacenes() {
        return almacenes;
    }

    public List<Vehiculo> vehiculos() {
        return vehiculos;
    }

    public List<Pedido> pedidos() {
        return pedidos;
    }

    public Pedido pedido(String id) {
        return pedidos.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    public Vehiculo vehiculo(String id) {
        return vehiculos.stream().filter(v -> v.getId().equals(id)).findFirst().orElseThrow();
    }

    public Almacen almacen(String id) {
        return almacenes.stream().filter(a -> a.getId().equals(id)).findFirst().orElseThrow();
    }

    /**
     * Plan armado a mano sobre el escenario pequeño: dos rutas con dos entregas cada una y un
     * pedido en la bolsa de pendientes.
     */
    public Solucion solucionDePrueba() {
        Solucion solucion = new Solucion();

        Ruta primera = new Ruta("R-1", almacen(DatosCaso.ID_NOR_OESTE), vehiculo("TM01"));
        primera.insertarPedido(0, pedido("P-001"));
        primera.insertarPedido(1, pedido("P-002"));
        solucion.agregarRuta(primera);

        Ruta segunda = new Ruta("R-2", almacen(DatosCaso.ID_ESTE), vehiculo("TM02"));
        segunda.insertarPedido(0, pedido("P-003"));
        segunda.insertarPedido(1, pedido("P-004"));
        solucion.agregarRuta(segunda);

        solucion.agregarPedidoNoAsignado(pedido("P-005"));
        return solucion;
    }

    /** Copia del escenario con otra lista de pedidos, para construir casos límite. */
    public EscenarioDemo conPedidos(List<Pedido> otrosPedidos) {
        return new EscenarioDemo(
                estado.getReloj(), almacenes, vehiculos, List.copyOf(otrosPedidos), distancias);
    }

    /** Copia del escenario con otros almacenes, para probar faltantes de stock. */
    public EscenarioDemo conAlmacenes(List<Almacen> otrosAlmacenes) {
        return new EscenarioDemo(
                estado.getReloj(), List.copyOf(otrosAlmacenes), vehiculos, pedidos, distancias);
    }

    /** Copia del escenario con otra flota. */
    public EscenarioDemo conVehiculos(List<Vehiculo> otrosVehiculos) {
        List<Vehiculo> copia = new ArrayList<>(otrosVehiculos);
        return new EscenarioDemo(
                estado.getReloj(), almacenes, List.copyOf(copia), pedidos, distancias);
    }

    /** Copia del escenario calculando las distancias con bloqueos. */
    public EscenarioDemo conDistancias(CalculadorDistancia otrasDistancias) {
        return new EscenarioDemo(estado.getReloj(), almacenes, vehiculos, pedidos, otrasDistancias);
    }
}
