package com.paqrap.planificador.alns;

import com.paqrap.modelo.Pedido;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Formas de romper una solución para que la reparación pueda rearmarla mejor.
 *
 * <h2>Por qué hace falta más de una</h2>
 *
 * Cada operador expone un tipo distinto de error. El aleatorio saca cualquier cosa y sirve para
 * salir de rincones donde los demás se quedan quietos. El del peor va a por lo que más cuesta
 * mantener, que suele ser una entrega mal colocada. El de relacionados saca un grupo de entregas
 * parecidas entre sí —cercanas y con plazos similares— que es lo que permite reagruparlas en una
 * ruta distinta, algo que sacándolas de a una nunca se consigue. Y el de viaje entero libera una
 * unidad completa para que su carga se reparta.
 *
 * Cuál conviene depende de la instancia y del momento, y eso no se sabe de antemano: por eso el
 * algoritmo lleva la cuenta de cuál le está funcionando y los sortea con pesos.
 */
final class Destructores {

    private Destructores() {
    }

    /** Un operador que saca del plan la cantidad de entregas indicada. */
    interface Destructor {
        String nombre();

        void destruir(Plan plan, int cuantas, Random sorteo);
    }

    static List<Destructor> todos() {
        return List.of(
                new Aleatoria(), new DelPeor(), new DeRelacionados(), new DeViajeEntero());
    }

    /** Saca entregas al azar. Es el que menos sabe y el que más desatasca. */
    private static final class Aleatoria implements Destructor {
        @Override
        public String nombre() {
            return "aleatoria";
        }

        @Override
        public void destruir(Plan plan, int cuantas, Random sorteo) {
            for (int i = 0; i < cuantas; i++) {
                List<int[]> ubicaciones = plan.ubicaciones();
                if (ubicaciones.isEmpty()) {
                    return;
                }
                int[] elegida = ubicaciones.get(sorteo.nextInt(ubicaciones.size()));
                plan.quitar(elegida[0], elegida[1]);
            }
        }
    }

    /**
     * Saca las entregas que más caro salen de mantener donde están.
     *
     * Con algo de ruido a propósito: tomar siempre las peores hace que el método visite una y
     * otra vez las mismas, porque tras repararlas suelen volver a ser las peores.
     */
    private static final class DelPeor implements Destructor {
        private static final double SESGO = 3.0;

        @Override
        public String nombre() {
            return "del peor";
        }

        @Override
        public void destruir(Plan plan, int cuantas, Random sorteo) {
            for (int i = 0; i < cuantas; i++) {
                List<int[]> ubicaciones = plan.ubicaciones();
                if (ubicaciones.isEmpty()) {
                    return;
                }

                List<int[]> ordenadas = new ArrayList<>(ubicaciones);
                ordenadas.sort(Comparator.comparingDouble(
                        (int[] u) -> plan.ahorroAlQuitar(u[0], u[1])).reversed());

                // Sesgo hacia el principio de la lista sin quedarse clavado siempre en el primero.
                int posicion = (int) (Math.pow(sorteo.nextDouble(), SESGO) * ordenadas.size());
                int[] elegida = ordenadas.get(Math.min(posicion, ordenadas.size() - 1));
                plan.quitar(elegida[0], elegida[1]);
            }
        }
    }

    /**
     * Saca un grupo de entregas parecidas: cerca unas de otras y con plazos similares.
     *
     * Es el operador que de verdad aporta un vecindario grande. Sacar entregas sueltas y volver a
     * meterlas casi siempre las devuelve donde estaban; sacar un grupo relacionado permite que la
     * reparación las junte en otra ruta, que es un cambio que ningún movimiento local alcanza.
     */
    private static final class DeRelacionados implements Destructor {
        /** Cuánto pesa la diferencia de plazos frente a la distancia, en kilómetros por hora. */
        private static final double PESO_DEL_PLAZO = 2.0;

        @Override
        public String nombre() {
            return "relacionados";
        }

        @Override
        public void destruir(Plan plan, int cuantas, Random sorteo) {
            List<int[]> ubicaciones = plan.ubicaciones();
            if (ubicaciones.isEmpty()) {
                return;
            }

            int[] semilla = ubicaciones.get(sorteo.nextInt(ubicaciones.size()));
            Pedido referencia = plan.pedidoEn(semilla[0], semilla[1]);
            plan.quitar(semilla[0], semilla[1]);

            for (int i = 1; i < cuantas; i++) {
                List<int[]> restantes = plan.ubicaciones();
                if (restantes.isEmpty()) {
                    return;
                }

                int[] masParecida = null;
                double menorDistancia = Double.POSITIVE_INFINITY;
                for (int[] candidata : restantes) {
                    double distancia = parecido(referencia, plan.pedidoEn(candidata[0], candidata[1]));
                    if (distancia < menorDistancia) {
                        menorDistancia = distancia;
                        masParecida = candidata;
                    }
                }
                plan.quitar(masParecida[0], masParecida[1]);
            }
        }

        /** Cuanto menor, más se parecen. Mezcla distancia con cercanía de fechas límite. */
        private double parecido(Pedido uno, Pedido otro) {
            double kilometros = uno.getDestino().distanciaManhattanKm(otro.getDestino());
            double horas = Math.abs(java.time.Duration.between(
                    uno.getFechaLimite(), otro.getFechaLimite()).toMinutes()) / 60.0;
            return kilometros + PESO_DEL_PLAZO * horas;
        }
    }

    /**
     * Vacía un viaje completo.
     *
     * Libera una unidad de golpe y obliga a que su carga se reparta entre las demás. Es el que
     * corrige las asignaciones de unidad, que los otros tres tocan solo de rebote.
     */
    private static final class DeViajeEntero implements Destructor {
        @Override
        public String nombre() {
            return "viaje entero";
        }

        @Override
        public void destruir(Plan plan, int cuantas, Random sorteo) {
            int sacadas = 0;
            while (sacadas < cuantas && plan.cantidadDeViajes() > 0) {
                int viaje = sorteo.nextInt(plan.cantidadDeViajes());
                int entregas = plan.ubicacionesDe(viaje);
                if (entregas == 0) {
                    return;
                }
                for (int i = entregas - 1; i >= 0; i--) {
                    plan.quitar(viaje, i);
                    sacadas++;
                }
            }
        }
    }
}
