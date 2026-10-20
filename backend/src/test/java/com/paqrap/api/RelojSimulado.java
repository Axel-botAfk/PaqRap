package com.paqrap.api;

import java.util.concurrent.atomic.AtomicLong;

/** Reloj de pared que avanza al dormir, sin esperar de verdad. */
final class RelojSimulado implements Marcapasos.Reloj {
    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    /** Se ejecuta en cada espera del hilo que duerme; sirve para intervenir en un punto exacto. */
    volatile Runnable alDormir = () -> { };

    @Override
    public long ahoraMs() { return ahora.get(); }

    @Override
    public void dormir(long ms) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        ahora.addAndGet(ms);
        alDormir.run();
        Thread.yield();
    }

    void avanzar(long ms) { ahora.addAndGet(ms); }
}
