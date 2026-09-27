package com.paqrap.planificador.alns;

import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.RepartoParcial;
import com.paqrap.planificador.ruteo.CalculadorDistancia;

import java.util.Objects;
import java.util.Random;

/**
 * Búsqueda adaptativa de vecindarios grandes (ALNS) para el planificador de PaqRap.
 *
 * <h2>La idea</h2>
 *
 * En vez de construir planes enteros una y otra vez, parte de uno y lo perturba: en cada
 * iteración <b>destruye</b> una porción —saca entre el 10% y el 35% de las entregas— y la
 * <b>repara</b> volviéndolas a colocar. El plan resultante se acepta si mejora, y a veces aunque
 * empeore, para no quedarse encerrado.
 *
 * Lo que lo hace distinto de una búsqueda local no es el tamaño del cambio sino su naturaleza:
 * sacar veinte entregas relacionadas y recolocarlas permite reagruparlas en rutas distintas, algo
 * que ninguna secuencia de movimientos pequeños alcanza sin pasar por soluciones intermedias
 * mucho peores.
 *
 * <h2>Por qué aquí y no otra iteración de GRASP</h2>
 *
 * GRASP reconstruye el plan completo en cada iteración y tira el anterior, de modo que volver a
 * intentarlo es volver a muestrear la misma distribución golosa: medido sobre este caso, pasar de
 * 8 a 32 construcciones mejoró menos de medio punto porcentual. ALNS conserva lo bueno y solo
 * rehace una parte, así que cada iteración parte de mejor sitio y además cuesta bastante menos:
 * reinsertar quince entregas no es lo mismo que insertar cincuenta desde cero.
 *
 * <h2>Qué tiene de adaptativo</h2>
 *
 * No hay un operador de destrucción bueno para todo. El algoritmo lleva la cuenta de cuál le está
 * dando resultado en esta instancia y sortea con pesos que se ajustan solos. Lo mismo con las dos
 * formas de reparar. Ver {@link Ruleta}.
 *
 * <h2>Independiente de los otros dos</h2>
 *
 * No comparte nada con GRASP ni con la búsqueda tabú salvo el {@link Evaluador}, que es
 * deliberado: los tres tienen que medir con la misma regla para que compararlos signifique algo,
 * pero ninguno debe heredar las decisiones de otro.
 *
 * Es reproducible: el esfuerzo se mide en iteraciones y toda la aleatoriedad sale de la semilla.
 */
public final class BusquedaAlns implements Planificador {
    public static final String NOMBRE = "ALNS";

    private static final double EPSILON = 1e-9;

    private final Evaluador evaluador;
    private final ParametrosAlns ajustes;

    public BusquedaAlns(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia));
    }

    public BusquedaAlns(Evaluador evaluador) {
        this(evaluador, ParametrosAlns.porDefecto());
    }

    public BusquedaAlns(Evaluador evaluador, ParametrosAlns ajustes) {
        this.evaluador = Objects.requireNonNull(evaluador);
        this.ajustes = Objects.requireNonNull(ajustes);
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Random sorteo = new Random(parametros.getSemilla());

        Ruleta<Destructores.Destructor> destructores =
                new Ruleta<>(Destructores.todos(), ajustes.reaccion());
        Ruleta<Reparadores.Reparador> reparadores =
                new Ruleta<>(Reparadores.todos(), ajustes.reaccion());

        RepartoParcial reparto = new RepartoParcial(evaluador);

        Plan actual = construccionInicial(estado, parametros, sorteo);
        actual.repartirLoQueNoCupo(reparto);
        double valorActual = actual.objetivo(parametros);

        Plan mejor = actual.copiar();
        double valorMejor = valorActual;

        double temperatura = Math.max(EPSILON, Math.abs(valorMejor) * ajustes.temperaturaInicial());

        for (int iteracion = 0; iteracion < ajustes.iteraciones(); iteracion++) {
            int cuantas = cuantasDestruir(actual, sorteo);
            if (cuantas == 0) {
                break;
            }

            int indiceDestructor = destructores.elegir(sorteo);
            int indiceReparador = reparadores.elegir(sorteo);

            Plan candidato = actual.copiar();
            destructores.operador(indiceDestructor).destruir(candidato, cuantas, sorteo);
            candidato.depurar();
            // El reparto por partes va dentro del bucle y no al final: si se aplicara solo a
            // la mejor solucion ya elegida, el algoritmo estaria comparando planes sin partir y
            // devolviendo uno partido, y mas presupuesto podria dar un resultado peor.
            reparadores.operador(indiceReparador).reparar(candidato, ajustes, sorteo);
            candidato.repartirLoQueNoCupo(reparto);

            double valorCandidato = candidato.objetivo(parametros);
            double premio = premiar(valorCandidato, valorActual, valorMejor, temperatura, sorteo);

            if (premio > 0) {
                actual = candidato;
                valorActual = valorCandidato;
            }
            if (valorCandidato < valorMejor - EPSILON) {
                mejor = candidato.copiar();
                valorMejor = valorCandidato;
            }

            destructores.anotar(indiceDestructor, premio);
            reparadores.anotar(indiceReparador, premio);
            temperatura *= ajustes.enfriamiento();
        }

        Solucion solucion = mejor.solucion();
        solucion.depurarRutasVacias();
        evaluador.sincronizarMetricas(solucion, estado);
        solucion.setAlgoritmo(NOMBRE);
        return solucion;
    }

    /**
     * Plan de partida: se repara un plan vacío, o sea que se insertan todos los pedidos con el
     * criterio del arrepentimiento.
     *
     * No se toma prestado el constructivo de la búsqueda tabú ni una construcción de GRASP a
     * propósito. Si ALNS arrancara del resultado de otro algoritmo, compararlos mediría uno y su
     * fase de mejora, no dos métodos.
     */
    private Plan construccionInicial(
            EstadoOperacion estado,
            Parametros parametros,
            Random sorteo
    ) {
        Plan plan = Plan.vacio(estado, evaluador, parametros.getPenalidadEspera());
        Reparadores.todos().get(1).reparar(plan, ajustes, sorteo);
        return plan;
    }

    /** Cuántas entregas se sacan esta vez, dentro del rango configurado. */
    private int cuantasDestruir(Plan plan, Random sorteo) {
        int colocadas = plan.ubicaciones().size();
        if (colocadas == 0) {
            return 0;
        }
        double fraccion = ajustes.fraccionMinima()
                + sorteo.nextDouble() * (ajustes.fraccionMaxima() - ajustes.fraccionMinima());
        return Math.max(1, (int) Math.round(colocadas * fraccion));
    }

    /**
     * Cuánto rindió el intento, y de paso si se acepta.
     *
     * Un récord vale mucho; mejorar la solución en curso vale menos; y empeorarla vale poco pero
     * no cero, porque aceptar de vez en cuando algo peor es lo único que saca a la búsqueda de un
     * óptimo local. Esa aceptación es la del recocido simulado: cuanto peor el candidato y más
     * fría la temperatura, menos probable.
     *
     * @return el premio, que es cero exactamente cuando el candidato se rechaza.
     */
    private double premiar(
            double valorCandidato,
            double valorActual,
            double valorMejor,
            double temperatura,
            Random sorteo
    ) {
        if (valorCandidato < valorMejor - EPSILON) {
            return ajustes.premioMejorGlobal();
        }
        if (valorCandidato < valorActual - EPSILON) {
            return ajustes.premioMejora();
        }
        if (Double.isInfinite(valorCandidato)) {
            return 0.0;
        }
        double probabilidad = Math.exp(-(valorCandidato - valorActual) / temperatura);
        return sorteo.nextDouble() < probabilidad ? ajustes.premioAceptada() : 0.0;
    }
}
