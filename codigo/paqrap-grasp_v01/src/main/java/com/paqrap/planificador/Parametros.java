package com.paqrap.planificador;

/**
 * Parámetros de los dos metaheurísticos del componente planificador.
 *
 * Los tres primeros corresponden a GRASP (pseudocódigo/ISA). El resto configura la
 * búsqueda tabú y tiene valores por defecto, de modo que el constructor original
 * `new Parametros(maxIteraciones, alfa, semilla)` sigue siendo válido.
 */
public final class Parametros {
    private static final int TENENCIA_TABU_POR_DEFECTO = 10;
    private static final int ITERACIONES_TABU_POR_DEFECTO = 200;
    private static final int MUESTRA_VECINDARIO_POR_DEFECTO = 40;
    private static final int ITERACIONES_SIN_MEJORA_POR_DEFECTO = 30;
    private static final int REINICIOS_TABU_POR_DEFECTO = 3;
    private static final long LIMITE_MS_POR_DEFECTO = 5_000L;
    private static final double PENALIDAD_NO_ASIGNADO_POR_DEFECTO = 10_000.0;

    private final int maxIteraciones;
    private final double alfa;
    private final long semilla;

    private final int iteracionesTabu;
    private final int tenenciaTabu;
    private final int tamanoMuestraVecindario;
    private final int iteracionesSinMejora;
    private final int reiniciosTabu;
    private final long limiteMilisegundos;
    private final double penalidadNoAsignado;
    private final double radioVecindarioKm;

    public Parametros(int maxIteraciones, double alfa, long semilla) {
        this(new Constructor(maxIteraciones, alfa, semilla));
    }

    private Parametros(Constructor constructor) {
        this.maxIteraciones = constructor.maxIteraciones;
        this.alfa = constructor.alfa;
        this.semilla = constructor.semilla;
        this.iteracionesTabu = constructor.iteracionesTabu;
        this.tenenciaTabu = constructor.tenenciaTabu;
        this.tamanoMuestraVecindario = constructor.tamanoMuestraVecindario;
        this.iteracionesSinMejora = constructor.iteracionesSinMejora;
        this.reiniciosTabu = constructor.reiniciosTabu;
        this.limiteMilisegundos = constructor.limiteMilisegundos;
        this.penalidadNoAsignado = constructor.penalidadNoAsignado;
        this.radioVecindarioKm = constructor.radioVecindarioKm;
    }

    public static Constructor constructor(int maxIteraciones, double alfa, long semilla) {
        return new Constructor(maxIteraciones, alfa, semilla);
    }

    public int getMaxIteraciones() {
        return maxIteraciones;
    }

    public double getAlfa() {
        return alfa;
    }

    public long getSemilla() {
        return semilla;
    }

    /** Iteraciones de la búsqueda tabú (independiente de las construcciones de GRASP). */
    public int getIteracionesTabu() {
        return iteracionesTabu;
    }

    /** Iteraciones que un atributo permanece prohibido. */
    public int getTenenciaTabu() {
        return tenenciaTabu;
    }

    /** Cantidad de vecinos muestreados por iteración. */
    public int getTamanoMuestraVecindario() {
        return tamanoMuestraVecindario;
    }

    /** Iteraciones sin mejora antes de intensificar volviendo a la mejor solución. */
    public int getIteracionesSinMejora() {
        return iteracionesSinMejora;
    }

    public int getReiniciosTabu() {
        return reiniciosTabu;
    }

    public long getLimiteMilisegundos() {
        return limiteMilisegundos;
    }

    /** Penalidad por pedido sin atender dentro de la función objetivo. */
    public double getPenalidadNoAsignado() {
        return penalidadNoAsignado;
    }

    /**
     * Radio para el vecindario granular. Por defecto es infinito (sin restricción),
     * porque las distancias del modelo actual pueden estar registradas de forma parcial.
     */
    public double getRadioVecindarioKm() {
        return radioVecindarioKm;
    }

    public static final class Constructor {
        private final int maxIteraciones;
        private final double alfa;
        private final long semilla;

        private int iteracionesTabu = ITERACIONES_TABU_POR_DEFECTO;
        private int tenenciaTabu = TENENCIA_TABU_POR_DEFECTO;
        private int tamanoMuestraVecindario = MUESTRA_VECINDARIO_POR_DEFECTO;
        private int iteracionesSinMejora = ITERACIONES_SIN_MEJORA_POR_DEFECTO;
        private int reiniciosTabu = REINICIOS_TABU_POR_DEFECTO;
        private long limiteMilisegundos = LIMITE_MS_POR_DEFECTO;
        private double penalidadNoAsignado = PENALIDAD_NO_ASIGNADO_POR_DEFECTO;
        private double radioVecindarioKm = Double.POSITIVE_INFINITY;

        private Constructor(int maxIteraciones, double alfa, long semilla) {
            if (maxIteraciones <= 0) {
                throw new IllegalArgumentException("maxIteraciones debe ser mayor que cero.");
            }
            if (alfa < 0.0 || alfa > 1.0) {
                throw new IllegalArgumentException("alfa debe estar entre 0 y 1.");
            }
            this.maxIteraciones = maxIteraciones;
            this.alfa = alfa;
            this.semilla = semilla;
        }

        public Constructor iteracionesTabu(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("iteracionesTabu debe ser mayor que cero.");
            }
            this.iteracionesTabu = valor;
            return this;
        }

        public Constructor tenenciaTabu(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("tenenciaTabu debe ser mayor que cero.");
            }
            this.tenenciaTabu = valor;
            return this;
        }

        public Constructor tamanoMuestraVecindario(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("tamanoMuestraVecindario debe ser mayor que cero.");
            }
            this.tamanoMuestraVecindario = valor;
            return this;
        }

        public Constructor iteracionesSinMejora(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("iteracionesSinMejora debe ser mayor que cero.");
            }
            this.iteracionesSinMejora = valor;
            return this;
        }

        public Constructor reiniciosTabu(int valor) {
            if (valor < 0) {
                throw new IllegalArgumentException("reiniciosTabu no puede ser negativo.");
            }
            this.reiniciosTabu = valor;
            return this;
        }

        public Constructor limiteMilisegundos(long valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("limiteMilisegundos debe ser mayor que cero.");
            }
            this.limiteMilisegundos = valor;
            return this;
        }

        public Constructor penalidadNoAsignado(double valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("penalidadNoAsignado debe ser mayor que cero.");
            }
            this.penalidadNoAsignado = valor;
            return this;
        }

        public Constructor radioVecindarioKm(double valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("radioVecindarioKm debe ser mayor que cero.");
            }
            this.radioVecindarioKm = valor;
            return this;
        }

        public Parametros construir() {
            return new Parametros(this);
        }
    }
}
