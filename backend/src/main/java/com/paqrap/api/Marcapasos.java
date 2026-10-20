package com.paqrap.api;

/**
 * Marca el pulso de la simulación: una ventana publicada cada {@code Sa} reales.
 *
 * Cada ventana se publica en cuanto está calculada y luego se espera su turno antes de calcular
 * la siguiente. Mientras tanto el visualizador la reproduce y quien registre una avería la ve
 * entrar en el cálculo inmediato, no dos ventanas después.
 *
 * La espera descuenta lo que tardó el último cálculo, de modo que N ventanas duren N × Sa sin
 * acumular atraso. Si un cálculo rebasa el salto la ventana sale tarde y la cuenta sigue desde
 * ahí: el reloj del visualizador nunca salta hacia adelante para recuperar tiempo. La pausa
 * congela la cuenta y su duración no se descuenta del salto.
 */
final class Marcapasos {
    /** Reloj de pared; en las pruebas se reemplaza para no esperar de verdad. */
    interface Reloj {
        long ahoraMs();
        void dormir(long ms) throws InterruptedException;
    }

    static final Reloj SISTEMA = new Reloj() {
        @Override public long ahoraMs() { return System.currentTimeMillis(); }
        @Override public void dormir(long ms) throws InterruptedException { Thread.sleep(ms); }
    };

    private static final long PASO_ESPERA_MS = 200;
    private final Reloj reloj;
    private final long saMs;
    private long publicadoMs = -1;
    private long finDeEsperaMs = -1;
    private long ultimoCalculoMs;
    private long pausadaDesdeMs = -1;
    private int rebasadas;
    private volatile boolean pausada;

    Marcapasos(Reloj reloj, long saMs) {
        if (saMs <= 0) throw new IllegalArgumentException("Sa debe ser positivo.");
        this.reloj = reloj;
        this.saMs = saMs;
    }

    /** La ventana recién calculada sale ahora; devuelve el ancla del reloj del visualizador. */
    synchronized long publicar() {
        long ahora = reloj.ahoraMs();
        if (finDeEsperaMs >= 0) {
            ultimoCalculoMs = Math.max(0, ahora - finDeEsperaMs);
            if (ultimoCalculoMs > saMs) rebasadas++;
        }
        publicadoMs = ahora;
        return publicadoMs;
    }

    /**
     * Espera hasta que toque calcular la ventana siguiente: el salto menos lo que tardó el último
     * cálculo, para que la próxima salga justo cuando termina de verse esta. El descuento no pasa
     * de medio salto, para que tras un cálculo lento la ventana publicada alcance a verse.
     */
    void esperarTurno() throws InterruptedException {
        esperarHasta(() -> publicadoMs + saMs - Math.min(ultimoCalculoMs, saMs / 2));
        synchronized (this) { finDeEsperaMs = reloj.ahoraMs(); }
    }

    /** Deja que la última ventana se vea entera antes de declarar el fin de la corrida. */
    void esperarCierre() throws InterruptedException {
        esperarHasta(() -> publicadoMs + saMs);
    }

    synchronized boolean pausar() {
        if (pausada) return false;
        pausada = true;
        pausadaDesdeMs = reloj.ahoraMs();
        return true;
    }

    /** Corre la cuenta lo que duró la pausa: la ventana visible retoma donde quedó. */
    synchronized boolean reanudar() {
        if (!pausada) return false;
        long ahora = reloj.ahoraMs();
        if (publicadoMs >= 0) publicadoMs += ahora - Math.max(pausadaDesdeMs, publicadoMs);
        pausada = false;
        pausadaDesdeMs = -1;
        return true;
    }

    boolean pausada() { return pausada; }

    synchronized long publicadoMs() { return publicadoMs; }

    /** Cuánto de la ventana visible se había reproducido al pausar; nulo si no está en pausa. */
    synchronized Long transcurridoAlPausarMs() {
        if (!pausada) return null;
        return Math.max(0, Math.min(saMs, pausadaDesdeMs - publicadoMs));
    }

    synchronized int rebasadas() { return rebasadas; }

    long saMs() { return saMs; }

    long ahoraMs() { return reloj.ahoraMs(); }

    private interface Objetivo { long ms(); }

    private void esperarHasta(Objetivo objetivo) throws InterruptedException {
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long restante;
            synchronized (this) {
                restante = pausada ? PASO_ESPERA_MS : objetivo.ms() - reloj.ahoraMs();
            }
            if (restante <= 0) return;
            reloj.dormir(Math.min(restante, PASO_ESPERA_MS));
        }
    }
}
