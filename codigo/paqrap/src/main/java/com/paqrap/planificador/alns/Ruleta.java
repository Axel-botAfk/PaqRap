package com.paqrap.planificador.alns;

import java.util.List;
import java.util.Random;

/**
 * Sorteo con pesos que se ajustan según lo que va funcionando.
 *
 * Es la parte <i>adaptativa</i> del método. Todos los operadores empiezan pesando igual, y en
 * cada uso se anota cuánto rindió: mucho si encontró una solución récord, algo si mejoró la
 * actual, poco si solo fue aceptada. Cada cierto número de iteraciones los pesos se mueven hacia
 * el rendimiento observado.
 *
 * <h2>Por qué el ajuste es parcial</h2>
 *
 * El peso nuevo es una mezcla del viejo y de lo recién observado, gobernada por la reacción. Con
 * reacción 1 el método olvidaría todo su historial en cada tanda y perseguiría el ruido de las
 * últimas iteraciones; con 0 no aprendería nunca. Un valor bajo deja que el aprendizaje se note
 * sin volverse errático.
 *
 * Ningún peso baja de un mínimo, de modo que un operador que rindió mal al principio siga
 * saliendo de vez en cuando: lo que no sirve en las primeras iteraciones, cuando la solución
 * todavía es mala, puede ser justo lo que haga falta al final.
 *
 * @param <T> el tipo de operador que se sortea.
 */
final class Ruleta<T> {
    /** Cada cuántos usos se recalculan los pesos. */
    private static final int TANDA = 25;

    /** Suelo del peso, para que ningún operador desaparezca del sorteo. */
    private static final double PESO_MINIMO = 0.05;

    private final List<T> operadores;
    private final double reaccion;
    private final double[] pesos;
    private final double[] puntos;
    private final int[] usos;
    private int desdeElUltimoAjuste;

    Ruleta(List<T> operadores, double reaccion) {
        this.operadores = List.copyOf(operadores);
        this.reaccion = reaccion;
        this.pesos = new double[this.operadores.size()];
        this.puntos = new double[this.operadores.size()];
        this.usos = new int[this.operadores.size()];
        java.util.Arrays.fill(pesos, 1.0);
    }

    /** Elige uno al azar, con probabilidad proporcional a su peso. */
    int elegir(Random sorteo) {
        double total = 0.0;
        for (double peso : pesos) {
            total += peso;
        }
        double corte = sorteo.nextDouble() * total;
        for (int i = 0; i < pesos.length; i++) {
            corte -= pesos[i];
            if (corte <= 0) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    T operador(int indice) {
        return operadores.get(indice);
    }

    /** Anota lo que rindió el operador y, si toca, recalcula los pesos. */
    void anotar(int indice, double premio) {
        puntos[indice] += premio;
        usos[indice]++;
        if (++desdeElUltimoAjuste >= TANDA) {
            ajustar();
            desdeElUltimoAjuste = 0;
        }
    }

    private void ajustar() {
        for (int i = 0; i < pesos.length; i++) {
            if (usos[i] == 0) {
                continue;
            }
            double rendimiento = puntos[i] / usos[i];
            pesos[i] = Math.max(PESO_MINIMO, (1 - reaccion) * pesos[i] + reaccion * rendimiento);
            puntos[i] = 0.0;
            usos[i] = 0;
        }
    }

    /** Los pesos finales, para poder informar qué operadores acabaron mandando. */
    double[] pesos() {
        return pesos.clone();
    }
}
