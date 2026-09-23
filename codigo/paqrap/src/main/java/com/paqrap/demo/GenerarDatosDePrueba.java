package com.paqrap.demo;

import com.paqrap.datos.GeneradorVentas;
import com.paqrap.modelo.Pedido;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Escribe los archivos de ventas que usan las simulaciones, en el formato del caso.
 *
 * Son datos sintéticos que se reemplazan por los del equipo docente cuando estén disponibles;
 * mientras tanto permiten correr los escenarios 5D y de colapso de forma reproducible, porque
 * el generador va con semilla fija.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.GenerarDatosDePrueba
 */
public final class GenerarDatosDePrueba {
    private static final Path CARPETA = Path.of("datos");

    private GenerarDatosDePrueba() {
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(CARPETA);

        escribir(
                CARPETA.resolve("ventas202609"),
                "Demanda sostenida para la simulacion de 5 dias: 120 pedidos por dia.",
                new GeneradorVentas(20260901L)
                        .demandaConstante(YearMonth.of(2026, 9), 1, 5, 120)
        );

        escribir(
                CARPETA.resolve("ventas202610"),
                "Demanda creciente para la simulacion hasta el colapso: 60 el primer dia, +8 por dia. "
                        + "La rampa esta calibrada al techo medido de la flota (~200 pedidos/dia), "
                        + "de modo que la operacion aguanta varias semanas antes de quebrarse.",
                new GeneradorVentas(20261001L)
                        .demandaCreciente(YearMonth.of(2026, 10), 1, 31, 60, 8)
        );
    }

    private static void escribir(Path archivo, String descripcion, List<Pedido> pedidos)
            throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("# " + archivo.getFileName() + " - datos sinteticos generados con semilla fija.");
        lineas.add("# " + descripcion);
        lineas.add("# Registro: ##d##h##m:posX,posY,cIdCliente,qq,hl");
        lineas.addAll(GeneradorVentas.aLineas(pedidos));

        Files.write(archivo, lineas, StandardCharsets.UTF_8);
        System.out.println("Escrito " + archivo + " con " + pedidos.size() + " pedidos.");
    }
}
