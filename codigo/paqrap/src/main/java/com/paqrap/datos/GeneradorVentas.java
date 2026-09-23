package com.paqrap.datos;

import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generador de archivos de ventas sintéticos, en el mismo formato que los del caso.
 *
 * Sirve para dos cosas: alimentar la simulación de cinco días con una demanda que la flota sí
 * puede atender, y construir el escenario de colapso, donde la demanda crece cada día hasta
 * que la flota deja de dar abasto.
 *
 * Los pedidos se reparten de forma uniforme sobre la ciudad y a lo largo del día, con la mezcla
 * de plazos del caso: la mayoría regulares de 36 horas y una fracción priorizados.
 */
public final class GeneradorVentas {
    /** Plazos priorizados del caso, con la proporción en que se sortean. */
    private static final int[] PLAZOS_PRIORIZADOS = {4, 8, 12, 18};

    private final Random aleatorio;
    private final double proporcionPriorizados;
    private final int cantidadMinima;
    private final int cantidadMaxima;

    public GeneradorVentas(long semilla) {
        this(semilla, 0.30, 1, 12);
    }

    public GeneradorVentas(
            long semilla,
            double proporcionPriorizados,
            int cantidadMinima,
            int cantidadMaxima
    ) {
        if (proporcionPriorizados < 0.0 || proporcionPriorizados > 1.0) {
            throw new IllegalArgumentException("La proporción de priorizados debe estar entre 0 y 1.");
        }
        if (cantidadMinima <= 0 || cantidadMaxima < cantidadMinima) {
            throw new IllegalArgumentException("El rango de cantidades es inválido.");
        }
        this.aleatorio = new Random(semilla);
        this.proporcionPriorizados = proporcionPriorizados;
        this.cantidadMinima = cantidadMinima;
        this.cantidadMaxima = cantidadMaxima;
    }

    /**
     * Demanda con una cantidad fija de pedidos por día.
     *
     * @param primerDia día del mes en que arranca la simulación.
     */
    public List<Pedido> demandaConstante(YearMonth periodo, int primerDia, int dias, int pedidosPorDia) {
        return generar(periodo, primerDia, dias, dia -> pedidosPorDia);
    }

    /**
     * Demanda que crece cada día. Es el escenario de colapso: tarde o temprano la flota deja de
     * poder cumplir los plazos y algún pedido vence sin entregarse.
     */
    public List<Pedido> demandaCreciente(
            YearMonth periodo,
            int primerDia,
            int dias,
            int pedidosElPrimerDia,
            int incrementoDiario
    ) {
        return generar(periodo, primerDia, dias, dia -> pedidosElPrimerDia + dia * incrementoDiario);
    }

    @FunctionalInterface
    private interface PedidosDelDia {
        int cuantos(int diaRelativo);
    }

    private List<Pedido> generar(YearMonth periodo, int primerDia, int dias, PedidosDelDia cuantos) {
        List<Pedido> pedidos = new ArrayList<>();
        int correlativo = 0;

        for (int dia = 0; dia < dias; dia++) {
            int fecha = primerDia + dia;
            if (fecha > periodo.lengthOfMonth()) {
                break;
            }
            int cantidadDelDia = cuantos.cuantos(dia);
            for (int i = 0; i < cantidadDelDia; i++) {
                correlativo++;
                pedidos.add(unPedido(periodo, fecha, correlativo));
            }
        }

        pedidos.sort((uno, otro) -> uno.getFechaRegistro().compareTo(otro.getFechaRegistro()));
        return List.copyOf(pedidos);
    }

    private Pedido unPedido(YearMonth periodo, int dia, int correlativo) {
        LocalDateTime llegada = periodo.atDay(dia)
                .atTime(aleatorio.nextInt(24), aleatorio.nextInt(60));

        Ubicacion destino = new Ubicacion(
                aleatorio.nextInt(Ciudad.ANCHO_KM + 1),
                aleatorio.nextInt(Ciudad.ALTO_KM + 1)
        );

        int cantidad = cantidadMinima + aleatorio.nextInt(cantidadMaxima - cantidadMinima + 1);

        int plazo = aleatorio.nextDouble() < proporcionPriorizados
                ? PLAZOS_PRIORIZADOS[aleatorio.nextInt(PLAZOS_PRIORIZADOS.length)]
                : 36;

        return new Pedido(
                String.format("P-%05d", correlativo),
                String.format("c%04d", aleatorio.nextInt(10_000)),
                destino,
                cantidad,
                llegada,
                plazo
        );
    }

    /** Vuelca los pedidos al formato del archivo de ventas del caso. */
    public static List<String> aLineas(List<Pedido> pedidos) {
        List<String> lineas = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            lineas.add(LectorVentas.aRegistro(pedido));
        }
        return lineas;
    }
}
