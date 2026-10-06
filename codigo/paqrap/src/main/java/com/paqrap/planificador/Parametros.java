package com.paqrap.planificador;

/**
 * Parámetros de los dos metaheurísticos del componente planificador.
 *
 * Los tres primeros corresponden a GRASP (pseudocódigo/ISA). El resto configura la
 * búsqueda tabú y tiene valores por defecto, de modo que el constructor original
 * `new Parametros(maxIteraciones, alfa, semilla)` sigue siendo válido.
 *
 * <h2>El esfuerzo se mide en iteraciones, nunca en milisegundos</h2>
 *
 * Ninguna búsqueda corta por reloj. Un corte por tiempo hace que el resultado dependa de la
 * máquina y de lo que esté corriendo al lado, de modo que dos ejecuciones con la misma semilla
 * pueden diferir y la experimentación numérica deja de ser reproducible: no se puede afirmar que
 * un algoritmo es mejor que otro si el mismo algoritmo no se repite a sí mismo.
 *
 * Para acotar el tiempo de ejecución cuando hace falta —la corrida con mapa, donde Ta tiene que
 * caber en el salto del algoritmo— se baja la cantidad de iteraciones y se mide el Ta que
 * resulta. El resultado es igual de acotado y además reproducible.
 */
public final class Parametros {
    private static final int TENENCIA_TABU_POR_DEFECTO = 10;
    private static final int ITERACIONES_TABU_POR_DEFECTO = 200;
    private static final int MUESTRA_VECINDARIO_POR_DEFECTO = 40;
    private static final int ITERACIONES_SIN_MEJORA_POR_DEFECTO = 30;
    private static final int REINICIOS_TABU_POR_DEFECTO = 3;
    private static final double PENALIDAD_NO_ASIGNADO_POR_DEFECTO = 10_000.0;
    private static final double PENALIDAD_ESPERA_POR_DEFECTO = 80.0;
    private static final int VIAJES_CANDIDATOS_POR_DEFECTO = 15;
    private static final int UNIDADES_CANDIDATAS_POR_DEFECTO = 10;
    private static final int PASADAS_BUSQUEDA_LOCAL_POR_DEFECTO = 2;
    private static final int VIAJES_BUSQUEDA_LOCAL_POR_DEFECTO = 4;

    private final int maxIteraciones;
    private final double alfa;
    private final long semilla;

    private final int iteracionesTabu;
    private final int tenenciaTabu;
    private final int tamanoMuestraVecindario;
    private final int iteracionesSinMejora;
    private final int reiniciosTabu;
    private final double penalidadNoAsignado;
    private final double penalidadEspera;
    private final double radioVecindarioKm;
    private final int viajesCandidatosPorPedido;
    private final int unidadesCandidatasPorPedido;
    private final int pasadasBusquedaLocal;
    private final int viajesCandidatosBusquedaLocal;

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
        this.penalidadNoAsignado = constructor.penalidadNoAsignado;
        this.penalidadEspera = constructor.penalidadEspera;
        this.radioVecindarioKm = constructor.radioVecindarioKm;
        this.viajesCandidatosPorPedido = constructor.viajesCandidatosPorPedido;
        this.unidadesCandidatasPorPedido = constructor.unidadesCandidatasPorPedido;
        this.pasadasBusquedaLocal = constructor.pasadasBusquedaLocal;
        this.viajesCandidatosBusquedaLocal = constructor.viajesCandidatosBusquedaLocal;
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

    /**
     * Cuanto cuesta, por producto y por hora, que una entrega se programe mas tarde.
     *
     * <h2>Que problema resuelve</h2>
     *
     * Sin este termino al planificador le sale gratis diferir. Consolidar las entregas en pocos
     * viajes bien llenos recorre menos kilometros y por tanto puntua mejor, pero el simulador solo
     * ejecuta el primer tramo del plan antes de rehacerlo: medido sobre una cola de 151 pedidos,
     * los planes consolidados no entregaban <b>ningun</b> producto en la media hora siguiente,
     * mientras que los repartidos entre mas unidades entregaban dieciseis. Las unidades se pasaban
     * el dia posicionandose para entregas que la replanificacion siempre volvia a aplazar.
     *
     * <h2>Por que es lineal en el tiempo</h2>
     *
     * Penalizar solo a los pedidos que llegan justos a su plazo no bastaria: aplazar uno que tiene
     * treinta horas de holgura seguiria siendo gratis, y es justo lo que hace la consolidacion.
     * Cobrar por cada hora de espera equivale a minimizar la suma de los tiempos de entrega, que
     * es lo que obliga a usar todas las unidades a la vez en lugar de unas pocas muy cargadas.
     *
     * Va ponderado por la urgencia del pedido, de modo que aplazar al que tiene el plazo encima
     * cuesta mucho mas que aplazar al holgado.
     */
    public double getPenalidadEspera() {
        return penalidadEspera;
    }

    /**
     * Penalidad por <b>producto</b> sin atender dentro de la función objetivo.
     *
     * Se cobra por unidad de producto y no por pedido, porque el producto es lo que ocupa
     * capacidad y lo que un pedido partido reparte entre varias unidades. Con las cantidades del
     * caso —entre 1 y 10 unidades por pedido— dejar afuera un pedido promedio cuesta unos 55 000,
     * de modo que la penalidad sigue dominando por varios órdenes de magnitud al costo marginal
     * de sumar una entrega y el criterio se mantiene lexicográfico.
     */
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

    /**
     * Cuántos viajes ya planificados se consideran al buscar dónde intercalar un pedido. Se
     * eligen los más cercanos al destino: probar contra todos los viajes del plan vuelve la
     * construcción cuadrática cuando hay cientos de pedidos pendientes.
     */
    public int getViajesCandidatosPorPedido() {
        return viajesCandidatosPorPedido;
    }

    /** Cuántas unidades se consideran al estrenar un viaje; se eligen las más cercanas. */
    public int getUnidadesCandidatasPorPedido() {
        return unidadesCandidatasPorPedido;
    }

    /**
     * Cuántas pasadas completas da la búsqueda local de GRASP sobre cada solución construida.
     * Se detiene antes si una pasada no encuentra ninguna mejora.
     */
    public int getPasadasBusquedaLocal() {
        return pasadasBusquedaLocal;
    }

    /** Cuántos viajes cercanos explora la búsqueda local por cada pedido. */
    public int getViajesCandidatosBusquedaLocal() {
        return viajesCandidatosBusquedaLocal;
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
        private double penalidadNoAsignado = PENALIDAD_NO_ASIGNADO_POR_DEFECTO;
        private double penalidadEspera = PENALIDAD_ESPERA_POR_DEFECTO;
        private double radioVecindarioKm = Double.POSITIVE_INFINITY;
        private int viajesCandidatosPorPedido = VIAJES_CANDIDATOS_POR_DEFECTO;
        private int unidadesCandidatasPorPedido = UNIDADES_CANDIDATAS_POR_DEFECTO;
        private int pasadasBusquedaLocal = PASADAS_BUSQUEDA_LOCAL_POR_DEFECTO;
        private int viajesCandidatosBusquedaLocal = VIAJES_BUSQUEDA_LOCAL_POR_DEFECTO;

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

        /** Cuanto cuesta que un producto espere una hora mas de lo necesario. */
        public Constructor penalidadEspera(double valor) {
            if (valor < 0) {
                throw new IllegalArgumentException("penalidadEspera no puede ser negativa.");
            }
            this.penalidadEspera = valor;
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

        public Constructor viajesCandidatosPorPedido(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("viajesCandidatosPorPedido debe ser mayor que cero.");
            }
            this.viajesCandidatosPorPedido = valor;
            return this;
        }

        public Constructor unidadesCandidatasPorPedido(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException("unidadesCandidatasPorPedido debe ser mayor que cero.");
            }
            this.unidadesCandidatasPorPedido = valor;
            return this;
        }

        public Constructor pasadasBusquedaLocal(int valor) {
            if (valor < 0) {
                throw new IllegalArgumentException("pasadasBusquedaLocal no puede ser negativa.");
            }
            this.pasadasBusquedaLocal = valor;
            return this;
        }

        public Constructor viajesCandidatosBusquedaLocal(int valor) {
            if (valor <= 0) {
                throw new IllegalArgumentException(
                        "viajesCandidatosBusquedaLocal debe ser mayor que cero.");
            }
            this.viajesCandidatosBusquedaLocal = valor;
            return this;
        }

        public Parametros construir() {
            return new Parametros(this);
        }
    }
}
