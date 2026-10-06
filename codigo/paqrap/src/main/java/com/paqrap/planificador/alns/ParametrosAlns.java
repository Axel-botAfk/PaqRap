package com.paqrap.planificador.alns;

/**
 * Ajustes de la búsqueda adaptativa de vecindarios grandes.
 *
 * Viven aparte de {@link com.paqrap.planificador.Parametros} a propósito: ese objeto lo comparten
 * GRASP y la búsqueda tabú, y meterle las perillas de un tercer algoritmo lo volvería un cajón de
 * sastre donde cada uno ignora dos tercios de lo que trae. De ahí solo se toma la semilla, que sí
 * es del escenario y no del algoritmo.
 *
 * <h2>El esfuerzo se mide en iteraciones</h2>
 *
 * Igual que en los otros dos, nada corta por reloj. Un corte por milisegundos haría que el
 * resultado dependiera de la máquina y la comparación numérica dejaría de significar nada.
 *
 * @param iteraciones            destrucciones y reparaciones que se intentan.
 * @param fraccionMinima         porción de la solución que se destruye como poco.
 * @param fraccionMaxima         porción que se destruye como mucho.
 * @param temperaturaInicial     cuánto peor que la solución inicial se acepta al principio,
 *                               en tanto por uno; con 0,05 se admite empeorar un 5%.
 * @param enfriamiento           cuánto se multiplica la temperatura en cada iteración.
 * @param premioMejorGlobal      puntos para el operador que encuentra una solución récord.
 * @param premioMejora           puntos si mejora la solución en curso.
 * @param premioAceptada         puntos si empeora pero el criterio la acepta igual.
 * @param reaccion               cuánto pesa lo recién observado frente al historial de pesos.
 * @param viajesCandidatos       viajes cercanos que se prueban al reinsertar un pedido.
 * @param unidadesCandidatas     unidades cercanas que se prueban al estrenar un viaje.
 */
public record ParametrosAlns(
        int iteraciones,
        double fraccionMinima,
        double fraccionMaxima,
        double temperaturaInicial,
        double enfriamiento,
        double premioMejorGlobal,
        double premioMejora,
        double premioAceptada,
        double reaccion,
        int viajesCandidatos,
        int unidadesCandidatas
) {
    public ParametrosAlns {
        if (iteraciones <= 0) {
            throw new IllegalArgumentException("Las iteraciones deben ser más de cero.");
        }
        if (fraccionMinima <= 0 || fraccionMaxima > 1 || fraccionMinima > fraccionMaxima) {
            throw new IllegalArgumentException(
                    "Las fracciones de destrucción deben cumplir 0 < minima <= maxima <= 1.");
        }
        if (enfriamiento <= 0 || enfriamiento >= 1) {
            throw new IllegalArgumentException("El enfriamiento debe estar entre 0 y 1.");
        }
    }

    /**
     * Valores de arranque.
     *
     * Destruir entre el 10% y el 35% es el rango que la literatura de ruteo reporta como útil:
     * por debajo el vecindario deja de ser grande y el método degenera en búsqueda local; por
     * encima se pierde tanta estructura que reparar cuesta lo mismo que construir de cero, que
     * es justamente el defecto de GRASP que se quiere evitar.
     */
    public static ParametrosAlns porDefecto() {
        return new ParametrosAlns(
                200, 0.10, 0.35, 0.05, 0.995, 10.0, 5.0, 1.0, 0.2, 12, 8);
    }

    public ParametrosAlns conIteraciones(int cuantas) {
        return new ParametrosAlns(cuantas, fraccionMinima, fraccionMaxima, temperaturaInicial,
                enfriamiento, premioMejorGlobal, premioMejora, premioAceptada, reaccion,
                viajesCandidatos, unidadesCandidatas);
    }
}
