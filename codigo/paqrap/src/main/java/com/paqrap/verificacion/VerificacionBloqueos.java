package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.LectorBloqueos;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.EspecificacionVehiculo;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.planificador.ruteo.DistanciaManhattan;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.Parametros;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import com.paqrap.escenarios.Escenario;

/**
 * Verificación ejecutable sin JUnit del enrutador que esquiva tramos cerrados.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.VerificacionBloqueos
 */
public final class VerificacionBloqueos {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);

    private static final double VELOCIDAD_BICICLETA = velocidad(TipoVehiculo.BICICLETA);
    private static final double VELOCIDAD_AUTO = velocidad(TipoVehiculo.AUTO);

    private VerificacionBloqueos() {
    }

    public static void main(String[] args) {
        pruebaSinBloqueosCoincideConManhattan();
        pruebaBloqueoObligaARodear();
        pruebaBloqueoSoloRigeEnSuVentana();
        pruebaDestinoEncerradoEsInalcanzable();
        pruebaCaminoNoPisaNodosCerrados();
        pruebaBloqueoQueEmpiezaDuranteElTramo();
        pruebaTramoQueTerminaAntesDelBloqueo();
        pruebaFormatoDelArchivo();
        pruebaPlanificadorDescartaPedidoInalcanzable();
        pruebaClienteSobreElTramoCerradoRecibeSuPedido();
        System.out.println("OK - 10 verificaciones de bloqueos superadas.");
    }

    /** Sin tramos cerrados, el recorrido más corto es exactamente la distancia Manhattan. */
    private static void pruebaSinBloqueosCoincideConManhattan() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(MapaBloqueos.vacio());
        DistanciaManhattan referencia = new DistanciaManhattan();

        List<Ubicacion> nodos = List.of(
                DatosCaso.UBICACION_CENTRAL,
                DatosCaso.UBICACION_NOR_OESTE,
                DatosCaso.UBICACION_ESTE,
                new Ubicacion(0, 0),
                new Ubicacion(70, 50),
                new Ubicacion(45, 43)
        );

        for (Ubicacion origen : nodos) {
            for (Ubicacion destino : nodos) {
                afirmar(
                        enrutador.calcularKm(origen, destino, AHORA, VELOCIDAD_BICICLETA)
                                == referencia.calcularKm(origen, destino, AHORA, VELOCIDAD_BICICLETA),
                        "Sin bloqueos, " + origen + " -> " + destino
                                + " debería medir lo mismo que la distancia Manhattan."
                );
            }
        }
    }

    /**
     * Un muro entre el origen y el destino obliga a rodearlo. El desvío es determinista:
     * la pared cierra x de 25 a 29 sobre y=17, así que hay que cruzar por x=24 o por x=30.
     */
    private static void pruebaBloqueoObligaARodear() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muroAlNorteDelCentral());

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion destino = new Ubicacion(27, 20);

        double directa = origen.distanciaManhattanKm(destino);
        double conDesvio = enrutador.calcularKm(origen, destino, AHORA, VELOCIDAD_BICICLETA);

        afirmar(directa == 6.0, "La distancia directa de referencia cambió.");
        afirmar(conDesvio > directa, "El bloqueo no obligó a alargar el recorrido.");
        afirmar(
                conDesvio == 12.0,
                "El desvío por el extremo del muro debería medir 12 km y midió " + conDesvio + "."
        );
    }

    /** Antes de que empiece y después de que termine, el tramo vuelve a estar habilitado. */
    private static void pruebaBloqueoSoloRigeEnSuVentana() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muroAlNorteDelCentral());

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion destino = new Ubicacion(27, 20);

        LocalDateTime antes = PERIODO.atDay(8).atTime(4, 0);
        LocalDateTime durante = PERIODO.atDay(8).atTime(6, 0);
        LocalDateTime despues = PERIODO.atDay(8).atTime(15, 0);

        afirmar(
                enrutador.calcularKm(origen, destino, antes, VELOCIDAD_BICICLETA) == 6.0,
                "El bloqueo afectó un viaje que sale antes de su inicio."
        );
        afirmar(
                enrutador.calcularKm(origen, destino, durante, VELOCIDAD_BICICLETA) == 12.0,
                "El bloqueo no rige en su instante de inicio."
        );
        afirmar(
                enrutador.calcularKm(origen, destino, despues, VELOCIDAD_BICICLETA) == 6.0,
                "El bloqueo siguió rigiendo en su instante de fin."
        );
    }

    /** Una poligonal abierta puede aislar una esquina de la ciudad. */
    private static void pruebaDestinoEncerradoEsInalcanzable() {
        Bloqueo esquinaCerrada = Bloqueo.dePoligonal(
                PERIODO.atDay(8).atStartOfDay(),
                PERIODO.atDay(9).atStartOfDay(),
                List.of(new Ubicacion(1, 0), new Ubicacion(1, 1), new Ubicacion(0, 1))
        );
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(new MapaBloqueos(List.of(esquinaCerrada)));

        double distancia = enrutador.calcularKm(
                DatosCaso.UBICACION_CENTRAL, new Ubicacion(0, 0), AHORA, VELOCIDAD_AUTO);

        afirmar(
                Double.isInfinite(distancia),
                "Un destino aislado debería reportarse como inalcanzable y midió " + distancia + "."
        );
        afirmar(
                enrutador.camino(DatosCaso.UBICACION_CENTRAL, new Ubicacion(0, 0), AHORA, VELOCIDAD_AUTO)
                        .isEmpty(),
                "No debería existir camino hacia un destino aislado."
        );
    }

    /** El camino devuelto para el mapa debe ser contiguo y esquivar los nodos cerrados. */
    private static void pruebaCaminoNoPisaNodosCerrados() {
        MapaBloqueos mapa = muroAlNorteDelCentral();
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion destino = new Ubicacion(27, 20);
        List<Ubicacion> camino = enrutador.camino(origen, destino, AHORA, VELOCIDAD_BICICLETA);

        afirmar(camino.get(0).equals(origen), "El camino no empieza en el origen.");
        afirmar(camino.get(camino.size() - 1).equals(destino), "El camino no termina en el destino.");
        afirmar(
                camino.size() - 1 == (int) enrutador.calcularKm(origen, destino, AHORA, VELOCIDAD_BICICLETA),
                "El camino y la distancia informada no coinciden."
        );

        for (int i = 1; i < camino.size(); i++) {
            afirmar(
                    camino.get(i - 1).distanciaManhattanKm(camino.get(i)) == 1.0,
                    "El camino da un salto entre " + camino.get(i - 1) + " y " + camino.get(i) + "."
            );
        }
        for (Ubicacion nodo : camino) {
            afirmar(
                    !mapa.estaBloqueado(nodo, AHORA),
                    "El camino pasa por el nodo cerrado " + nodo + "."
            );
        }
    }

    /**
     * El corazón del asunto: la unidad sale con la calle abierta pero llegaría a esa esquina
     * después de que la cierren. El recorrido tiene que rodearla igual, porque de lo contrario
     * el plan prometería una entrega por un camino que no va a existir.
     */
    private static void pruebaBloqueoQueEmpiezaDuranteElTramo() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muroAlNorteDelCentral());

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion destino = new Ubicacion(27, 20);

        // Sale 10 minutos antes del cierre; a 12 km/h llegaría al muro recién a las 06:05.
        LocalDateTime salida = PERIODO.atDay(8).atTime(5, 50);
        double recorrido = enrutador.calcularKm(origen, destino, salida, VELOCIDAD_BICICLETA);

        afirmar(
                recorrido == 12.0,
                "La unidad atravesaría un tramo que estará cerrado cuando pase por él: "
                        + "se esperaba el desvío de 12 km y se obtuvo " + recorrido + "."
        );

        for (Ubicacion nodo : enrutador.camino(origen, destino, salida, VELOCIDAD_BICICLETA)) {
            afirmar(
                    nodo.y() != 17 || nodo.x() < 25 || nodo.x() > 29,
                    "El camino pasa por el muro en " + nodo + "."
            );
        }
    }

    /** Si la unidad completa el tramo antes del cierre, no tiene por qué desviarse. */
    private static void pruebaTramoQueTerminaAntesDelBloqueo() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muroAlNorteDelCentral());

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion destino = new Ubicacion(27, 20);

        // Mismo instante de salida, pero en auto: 6 km a 40 km/h se recorren en 9 minutos.
        LocalDateTime salida = PERIODO.atDay(8).atTime(5, 50);
        double recorrido = enrutador.calcularKm(origen, destino, salida, VELOCIDAD_AUTO);

        afirmar(
                recorrido == 6.0,
                "Una unidad que llega antes del cierre no debería desviarse; midió " + recorrido + "."
        );
    }

    /** Formato del caso: ##d##h##m-##d##h##m:x1,y1,x2,y2,... */
    private static void pruebaFormatoDelArchivo() {
        List<Bloqueo> bloqueos = LectorBloqueos.leerLineas(
                List.of(
                        "# comentario",
                        "",
                        "01d06h00m-01d15h00m:31,21,34,21",
                        "15d00h00m-16d23h59m:12,36,12,40,16,40"
                ),
                PERIODO
        );

        afirmar(bloqueos.size() == 2, "El lector no devolvió los dos registros del ejemplo.");

        Bloqueo primero = bloqueos.get(0);
        afirmar(
                primero.inicio().equals(PERIODO.atDay(1).atTime(6, 0)),
                "El instante de inicio no se interpretó como día 1 a las 06:00."
        );
        afirmar(
                primero.fin().equals(PERIODO.atDay(1).atTime(15, 0)),
                "El instante de fin no se interpretó como día 1 a las 15:00."
        );
        afirmar(
                primero.nodos().size() == 4,
                "El tramo (31,21)-(34,21) debería cerrar 4 nodos y cerró " + primero.nodos().size() + "."
        );
        afirmar(
                primero.nodos().contains(new Ubicacion(32, 21)),
                "El tramo no incluyó los nodos intermedios."
        );

        // Poligonal de dos tramos: 5 nodos verticales + 4 horizontales, con el vértice compartido.
        afirmar(
                bloqueos.get(1).nodos().size() == 9,
                "La poligonal de dos tramos debería cerrar 9 nodos y cerró "
                        + bloqueos.get(1).nodos().size() + "."
        );

        afirmar(
                LectorBloqueos.periodoDelNombre("202609.bloqueadas").equals(PERIODO),
                "El periodo no se dedujo del nombre del archivo."
        );
    }

    /** Si el cliente queda aislado, el planificador debe dejar su pedido sin asignar. */
    private static void pruebaPlanificadorDescartaPedidoInalcanzable() {
        Bloqueo esquinaCerrada = Bloqueo.dePoligonal(
                PERIODO.atDay(8).atStartOfDay(),
                PERIODO.atDay(9).atStartOfDay(),
                List.of(new Ubicacion(1, 0), new Ubicacion(1, 1), new Ubicacion(0, 1))
        );

        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-AISLADO", 0, 0, 2, AHORA, 36)))
                .conDistancias(new EnrutadorBloqueos(new MapaBloqueos(List.of(esquinaCerrada))));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.30, 8L).construir());

        afirmar(
                solucion.getCantidadPedidosNoAsignados() == 1,
                "Se asignó un pedido cuyo destino está aislado por un bloqueo."
        );
    }

    /**
     * Un cliente ubicado sobre el tramo cerrado tiene que poder recibir su pedido.
     *
     * El caso dice que por un nodo bloqueado no se pasa ni se gira, pero que la unidad que llega
     * hasta él vuelve por donde vino; y garantiza que en una poligonal abierta se llega a todos
     * sus puntos. Es decir: el nodo cerrado no se atraviesa, pero se le entrega entrando y
     * saliendo en media vuelta.
     *
     * Antes el enrutador prohibía entrar, no solo pasar, y ese cliente quedaba fuera del mapa
     * durante todo el bloqueo. Sobre los datos reales del caso así se perdía P-00689: el tramo
     * (8,23)-(20,23) —que esta prueba replica— dejaba inentregable al cliente de (15,23) durante
     * siete de sus ocho horas de plazo.
     */
    private static void pruebaClienteSobreElTramoCerradoRecibeSuPedido() {
        Bloqueo tramo = Bloqueo.dePoligonal(
                PERIODO.atDay(8).atTime(6, 0),
                PERIODO.atDay(8).atTime(15, 0),
                List.of(new Ubicacion(8, 23), new Ubicacion(20, 23))
        );
        MapaBloqueos mapa = new MapaBloqueos(List.of(tramo));
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);

        Ubicacion origen = DatosCaso.UBICACION_CENTRAL;
        Ubicacion cliente = new Ubicacion(15, 23);

        afirmar(
                mapa.estaBloqueado(cliente, AHORA),
                "La prueba pierde sentido si el cliente no está sobre el tramo cerrado."
        );
        afirmar(
                !mapa.estaAislado(cliente, AHORA),
                "Un nodo con vecinos abiertos no debería considerarse incomunicado."
        );

        double recorrido = enrutador.calcularKm(origen, cliente, AHORA, VELOCIDAD_AUTO);
        afirmar(
                recorrido == origen.distanciaManhattanKm(cliente),
                "Se debería llegar al cliente por la transversal sin alargar el recorrido: "
                        + "se esperaba " + origen.distanciaManhattanKm(cliente)
                        + " km y se obtuvo " + recorrido + "."
        );

        // Se entra por un vecino abierto; el tramo cerrado no se recorre en ningún momento.
        List<Ubicacion> camino = enrutador.camino(origen, cliente, AHORA, VELOCIDAD_AUTO);
        afirmar(
                camino.get(camino.size() - 1).equals(cliente),
                "El camino no termina en el cliente."
        );
        for (int i = 0; i < camino.size() - 1; i++) {
            afirmar(
                    !mapa.estaBloqueado(camino.get(i), AHORA),
                    "El camino atraviesa el nodo cerrado " + camino.get(i) + "."
            );
        }

        // Y sigue sin poder usarse de paso: cruzar de un lado al otro obliga a rodear el tramo.
        double deLadoALado = enrutador.calcularKm(
                new Ubicacion(15, 22), new Ubicacion(15, 24), AHORA, VELOCIDAD_AUTO);
        afirmar(
                deLadoALado == 14.0,
                "Atravesar el tramo cerrado debería costar el rodeo de 14 km y costó "
                        + deLadoALado + "."
        );
    }

    private static MapaBloqueos muroAlNorteDelCentral() {
        Bloqueo muro = Bloqueo.dePoligonal(
                PERIODO.atDay(8).atTime(6, 0),
                PERIODO.atDay(8).atTime(15, 0),
                List.of(new Ubicacion(25, 17), new Ubicacion(29, 17))
        );
        return new MapaBloqueos(List.of(muro));
    }

    private static double velocidad(TipoVehiculo tipo) {
        EspecificacionVehiculo especificacion = tipo.getEspecificacionDelCaso();
        return especificacion.velocidadKmH();
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
