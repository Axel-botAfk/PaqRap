package com.paqrap.planificador.tabu;

import java.util.HashMap;
import java.util.Map;

/**
 * Memoria de corto plazo. Cada atributo registrado permanece prohibido hasta la iteración
 * de vencimiento; las entradas vencidas se descartan al consultarse, de modo que no hace
 * falta recorrer la estructura completa en cada iteración.
 */
public final class ListaTabu {
    private final Map<String, Integer> vencimientos = new HashMap<>();
    private final int tenencia;

    public ListaTabu(int tenencia) {
        if (tenencia <= 0) {
            throw new IllegalArgumentException("La tenencia debe ser mayor que cero.");
        }
        this.tenencia = tenencia;
    }

    public void registrar(String atributo, int iteracionActual) {
        vencimientos.put(atributo, iteracionActual + tenencia);
    }

    public boolean esTabu(String atributo, int iteracionActual) {
        Integer vencimiento = vencimientos.get(atributo);
        if (vencimiento == null) {
            return false;
        }
        if (vencimiento <= iteracionActual) {
            vencimientos.remove(atributo);
            return false;
        }
        return true;
    }

    public int getTamano() {
        return vencimientos.size();
    }

    public int getTenencia() {
        return tenencia;
    }
}
