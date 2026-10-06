package com.paqrap.planificador.alns;

import com.paqrap.modelo.Pedido;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Formas de volver a meter en el plan las entregas que la destrucción sacó.
 *
 * <h2>Goloso contra arrepentimiento</h2>
 *
 * El goloso coloca en cada paso la entrega más barata de colocar. Es rápido y suele bastar, pero
 * tiene un defecto conocido: deja para el final a los pedidos difíciles, y para entonces ya no
 * queda sitio donde ponerlos sin pagar de más.
 *
 * El del arrepentimiento mira lo contrario: cuánto se encarece atender un pedido si su mejor
 * hueco se lo lleva otro. Coloca primero al que más perdería esperando, aunque no sea el más
 * barato ahora. Cuesta más por paso, pero deja muchos menos pedidos sin atender.
 *
 * Ninguno es mejor siempre, y por eso el algoritmo sortea entre los dos con pesos que se ajustan
 * según cuál le viene funcionando.
 */
final class Reparadores {

    private Reparadores() {
    }

    interface Reparador {
        String nombre();

        void reparar(Plan plan, ParametrosAlns ajustes, Random sorteo);
    }

    static List<Reparador> todos() {
        return List.of(new Goloso(), new ConArrepentimiento());
    }

    /** En cada paso, la entrega que ahora mismo sale más barata de colocar. */
    /**
     * Las alternativas ya calculadas de cada pedido, para no rehacerlas en cada vuelta.
     *
     * <h2>Por que hace falta</h2>
     *
     * Las dos reparaciones colocan un pedido por vuelta y necesitan, para decidir cual, las
     * alternativas de todos los que quedan. Recalcularlas enteras en cada vuelta las vuelve
     * cuadraticas: medido sobre una planificacion real de 57 pedidos, ALNS hacia 3,7 millones de
     * evaluaciones de viaje y tardaba cuatro minutos, contra veinte segundos de los otros dos.
     *
     * <h2>Que se invalida y por que solo eso</h2>
     *
     * Meter un pedido en una unidad cambia el programa de <b>esa</b> unidad y de ninguna otra, asi
     * que solo hay que olvidar las alternativas que apuntaban a ella. Las demas siguen siendo
     * exactas: sus viajes no se movieron, sus indices tampoco -los viajes nuevos se anaden al
     * final- y las posiciones dentro de ellos siguen donde estaban.
     *
     * Un pedido para el que no habia ningun hueco factible se recuerda asi y no se vuelve a
     * intentar en esta pasada. Es correcto ademas de barato: insertar solo consume capacidad y
     * tiempo, nunca los libera, de modo que lo que no cabia no puede empezar a caber.
     */
    private static final class Memoria {
        private final Plan plan;
        private final ParametrosAlns ajustes;
        private final Map<String, Plan.Alternativas> porPedido = new HashMap<>();

        Memoria(Plan plan, ParametrosAlns ajustes) {
            this.plan = plan;
            this.ajustes = ajustes;
        }

        Plan.Alternativas de(Pedido pedido) {
            return porPedido.computeIfAbsent(
                    pedido.getId(), id -> plan.alternativasPara(pedido, ajustes));
        }

        /** Tras colocar un pedido, olvida lo que dependia de la unidad que lo recibio. */
        void anotar(Plan.Opcion aplicada) {
            porPedido.remove(aplicada.pedido().getId());
            String unidad = aplicada.unidad().getId();
            porPedido.values().removeIf(alternativas -> apuntaA(alternativas, unidad));
        }

        private static boolean apuntaA(Plan.Alternativas alternativas, String unidadId) {
            return tocaA(alternativas.mejor(), unidadId) || tocaA(alternativas.segunda(), unidadId);
        }

        private static boolean tocaA(Plan.Opcion opcion, String unidadId) {
            return opcion != null && opcion.unidad().getId().equals(unidadId);
        }
    }

    private static final class Goloso implements Reparador {
        @Override
        public String nombre() {
            return "goloso";
        }

        @Override
        public void reparar(Plan plan, ParametrosAlns ajustes, Random sorteo) {
            Memoria memoria = new Memoria(plan, ajustes);
            while (true) {
                Plan.Opcion mejor = null;
                for (Pedido pedido : List.copyOf(plan.noAsignados())) {
                    Plan.Opcion candidata = memoria.de(pedido).mejor();
                    if (candidata != null
                            && (mejor == null || candidata.incremento() < mejor.incremento())) {
                        mejor = candidata;
                    }
                }
                if (mejor == null) {
                    // A ninguno le queda hueco factible; los que sobran se quedan sin atender.
                    return;
                }
                plan.aplicar(mejor);
                memoria.anotar(mejor);
            }
        }
    }

    /**
     * En cada paso, la entrega que más perdería si cede su mejor hueco.
     *
     * Sin alternativa el arrepentimiento es infinito: perder ese hueco significa no poder
     * atenderla, y eso pesa más que cualquier diferencia de costo.
     */
    private static final class ConArrepentimiento implements Reparador {
        @Override
        public String nombre() {
            return "arrepentimiento";
        }

        @Override
        public void reparar(Plan plan, ParametrosAlns ajustes, Random sorteo) {
            Memoria memoria = new Memoria(plan, ajustes);
            while (true) {
                Pedido elegido = null;
                Plan.Opcion destino = null;
                double mayorArrepentimiento = Double.NEGATIVE_INFINITY;

                for (Pedido pedido : List.copyOf(plan.noAsignados())) {
                    Plan.Alternativas alternativas = memoria.de(pedido);
                    if (alternativas.mejor() == null) {
                        continue;
                    }
                    double arrepentimiento = alternativas.arrepentimiento();
                    if (arrepentimiento > mayorArrepentimiento) {
                        mayorArrepentimiento = arrepentimiento;
                        elegido = pedido;
                        destino = alternativas.mejor();
                    }
                }

                if (elegido == null) {
                    return;
                }
                plan.aplicar(destino);
                memoria.anotar(destino);
            }
        }
    }
}
