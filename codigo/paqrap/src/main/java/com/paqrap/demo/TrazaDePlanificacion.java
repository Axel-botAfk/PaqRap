package com.paqrap.demo;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.simulacion.AvanceDeLaOperacion;
import com.paqrap.simulacion.MedicionDePlanificacion;
import com.paqrap.simulacion.Observador;
import com.paqrap.simulacion.Ritmo;

import java.time.LocalDateTime;

/**
 * Una línea por planificación, para ver cómo evoluciona la carga mientras la corrida avanza.
 *
 * <h2>Qué se ve acá que no se ve en el resumen final</h2>
 *
 * El resumen dice cómo terminó todo. Esto dice <b>cómo llegó hasta ahí</b>: si la cola sube y
 * baja con el ritmo del día o si sube y ya no baja, si las unidades se van llenando, y en qué
 * momento el algoritmo empieza a tardar más de lo que dura el salto. Son las tres formas en que
 * una operación avisa que se está quedando corta, y las tres se ven venir varias horas antes de
 * que venza el primer pedido.
 *
 * <h2>Se dispara antes de ejecutar</h2>
 *
 * El observador se avisa con el plan recién calculado, o sea que lo que se imprime es la
 * <b>intención</b> del planificador para el tramo que sigue, no lo que acabó pasando. Una unidad
 * solo recorre lo que alcanza antes del corte, así que «asigna» es lo prometido y no lo
 * entregado. Lo entregado aparece en el resumen del final.
 *
 * <h2>Las columnas</h2>
 *
 * <pre>
 *   instante   momento de la operación, no de la pantalla
 *   entreg     pedidos entregados hasta ahora, acumulado
 *   venc       pedidos que perdieron su plazo, acumulado. El primero declara el colapso
 *   enCola     pedidos que siguen esperando
 *   enRuta     producto que ya viaja encima de alguna unidad
 *   viaj/unid  viajes del plan y unidades distintas que los hacen
 *   asigna     producto comprometido en el plan
 *   deja       producto que el plan no pudo colocar
 *   Ta         lo que costó planificar
 *   %Sa        qué fracción del salto ocupó. Pasado el 100% la corrida se atropella
 * </pre>
 *
 * Entre entregados, vencidos, en cola y en ruta está todo lo que ha llegado. Lo en ruta se cuenta
 * en producto y no en pedidos porque el producto es fungible: lo que una unidad lleva encima no
 * tiene nombre, sirve para cualquier cliente, y eso es lo que permite redirigirla a mitad de
 * camino.
 */
public final class TrazaDePlanificacion implements Observador {

    private final int cadaCuantas;
    private final Ritmo ritmo;
    private int planificaciones;

    /**
     * @param cadaCuantas imprime una de cada tantas. Con 1 salen todas; en la 5D, que hace unas
     *                    550 planificaciones, conviene un valor más alto para poder leerlas.
     * @param ritmo       para calcular la ocupación del salto. Puede ser nulo si la corrida va a
     *                    fondo, y entonces esa columna queda vacía.
     */
    public TrazaDePlanificacion(int cadaCuantas, Ritmo ritmo) {
        if (cadaCuantas <= 0) {
            throw new IllegalArgumentException("cadaCuantas debe ser mayor que cero.");
        }
        this.cadaCuantas = cadaCuantas;
        this.ritmo = ritmo;
    }

    /** Lee {@code -Dpaqrap.traza}: ausente apaga la traza, y un número es cada cuántas imprimir. */
    public static TrazaDePlanificacion configurada(Ritmo ritmo) {
        return configurada(ritmo, null);
    }

    /**
     * Igual, pero con un valor por defecto cuando la propiedad no se indica.
     *
     * Lo usa el escenario de colapso, donde la traza va encendida de serie: sin ella la corrida
     * pasa horas sin decir nada y no hay forma de ver venir el quiebre.
     */
    public static TrazaDePlanificacion configurada(Ritmo ritmo, String porDefecto) {
        String valor = System.getProperty("paqrap.traza", porDefecto);
        if ("0".equals(valor)) {
            return null;
        }
        if (valor == null || valor.isBlank() || valor.equalsIgnoreCase("false")) {
            return null;
        }
        int cada = valor.equalsIgnoreCase("true") ? 1 : Integer.parseInt(valor.trim());
        return new TrazaDePlanificacion(cada, ritmo);
    }

    @Override
    public void alPlanificar(
            LocalDateTime reloj,
            EstadoOperacion estado,
            Solucion plan,
            MedicionDePlanificacion medicion,
            AvanceDeLaOperacion avance
    ) {
        planificaciones++;
        if (planificaciones == 1) {
            imprimirCabecera();
        }
        if (planificaciones % cadaCuantas != 0) {
            return;
        }

        System.out.printf(
                "  %s  %6d %6d %6d %6d  %5d %5d  %7d %6d  %7d %s%n",
                instante(reloj),
                avance.pedidosEntregados(),
                avance.pedidosVencidos(),
                medicion.pedidosEnCola(),
                avance.productosABordo(),
                plan.getCantidadRutas(),
                unidadesDistintas(plan),
                plan.getCantidadProductosAsignados(),
                plan.getCantidadProductosNoAsignados(),
                medicion.milisegundos(),
                ocupacion(medicion)
        );
    }

    private void imprimirCabecera() {
        System.out.println();
        System.out.println("=== TRAZA DE PLANIFICACION ===");
        System.out.printf(
                "  %-12s %6s %6s %6s %6s  %5s %5s  %7s %6s  %7s %s%n",
                "instante", "entreg", "venc", "enCola", "enRuta", "viaj", "unid",
                "asigna", "deja", "Ta ms", "%Sa");
        System.out.println("  entreg/venc son acumulados; enCola son pedidos y enRuta productos.");
    }

    /** Día y hora de la operación. El día del mes basta y ocupa menos que la fecha entera. */
    private static String instante(LocalDateTime reloj) {
        return String.format("d%02d %02d:%02d",
                reloj.getDayOfMonth(), reloj.getHour(), reloj.getMinute());
    }

    private static int unidadesDistintas(Solucion plan) {
        java.util.Set<String> unidades = new java.util.HashSet<>();
        for (Ruta viaje : plan.getRutas()) {
            unidades.add(viaje.getVehiculo().getId());
        }
        return unidades.size();
    }

    private String ocupacion(MedicionDePlanificacion medicion) {
        if (ritmo == null) {
            return "";
        }
        double fraccion = ritmo.ocupacion(medicion.milisegundos());
        // Se marca el rebase porque es el que invalida la corrida: la planificación siguiente
        // arrancaría antes de que ésta termine.
        return String.format("%5.0f%%%s", fraccion * 100, fraccion >= 1.0 ? " <-- rebasa" : "");
    }

    /** Cuántos pedidos hay en la cola, por si alguien quiere el dato sin releer la traza. */
    public static int productosDe(java.util.List<Pedido> pedidos) {
        int unidades = 0;
        for (Pedido pedido : pedidos) {
            unidades += pedido.getCantidad();
        }
        return unidades;
    }
}
