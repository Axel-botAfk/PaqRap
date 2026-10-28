package com.paqrap.simulacion;

import com.paqrap.escenarios.Escenario;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.grasp.Grasp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pedidos que entran a una corrida ya empezada.
 *
 * Es lo que necesita la operación día a día en tiempo real: la jornada arranca sin conocer su
 * demanda, así que los pedidos se registran mientras el reloj corre. Sin esto la operación solo
 * puede mostrar lo que ya estaba en la fuente al crear la corrida.
 */
class SimuladorEnVivoTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 8, 8, 0);

    private static Simulador simulador(Escenario escenario) {
        return new Simulador(new Grasp(new Evaluador(escenario.distancias())),
                escenario.distancias(), escenario.almacenes(),
                Parametros.constructor(30, 0.30, 7L).construir());
    }

    @Test
    void unPedidoRegistradoAntesDeCorrerEntraComoRecibido() {
        Escenario escenario = Escenario.pequeno(INICIO).conPedidos(List.of());
        Simulador simulador = simulador(escenario);

        simulador.registrarPedidoEnVivo(
                Escenario.pedido("EV-1", 20, 18, 4, INICIO.plusMinutes(30), 12));

        ResumenSimulacion resumen = simulador.correr(
                INICIO, Duration.ofDays(1), List.of(), escenario.vehiculos());

        assertEquals(1, resumen.pedidosRecibidos(),
                "el pedido registrado en vivo no entró a la cola");
    }

    @Test
    void unPedidoEnVivoSeSumaALosDeLaFuenteSinDesplazarlos() {
        Escenario escenario = Escenario.pequeno(INICIO).conPedidos(List.of());
        Simulador simulador = simulador(escenario);
        Pedido deLaFuente = Escenario.pedido("F-1", 22, 19, 3, INICIO.plusMinutes(10), 12);

        simulador.registrarPedidoEnVivo(
                Escenario.pedido("EV-1", 20, 18, 4, INICIO.plusMinutes(40), 12));

        ResumenSimulacion resumen = simulador.correr(
                INICIO, Duration.ofDays(1), List.of(deLaFuente), escenario.vehiculos());

        assertEquals(2, resumen.pedidosRecibidos());
    }

    /**
     * Un pedido cuya fecha de registro cae fuera del horizonte no pertenece a la jornada: la
     * corrida lo ignora igual que a los de la fuente, sin fallar.
     */
    @Test
    void unPedidoEnVivoPosteriorAlHorizonteNoEntra() {
        Escenario escenario = Escenario.pequeno(INICIO).conPedidos(List.of());
        Simulador simulador = simulador(escenario);

        simulador.registrarPedidoEnVivo(
                Escenario.pedido("EV-1", 20, 18, 4, INICIO.plusDays(3), 12));

        ResumenSimulacion resumen = simulador.correr(
                INICIO, Duration.ofDays(1), List.of(), escenario.vehiculos());

        assertEquals(0, resumen.pedidosRecibidos());
    }

    /** Registrarlo dos veces no lo duplica: la cola se vacía al incorporarse. */
    @Test
    void losPedidosEnVivoSeIncorporanUnaSolaVez() {
        Escenario escenario = Escenario.pequeno(INICIO).conPedidos(List.of());
        Simulador simulador = simulador(escenario);

        simulador.registrarPedidoEnVivo(
                Escenario.pedido("EV-1", 20, 18, 4, INICIO.plusMinutes(20), 12));
        simulador.registrarPedidoEnVivo(
                Escenario.pedido("EV-2", 21, 18, 2, INICIO.plusMinutes(25), 12));

        ResumenSimulacion resumen = simulador.correr(
                INICIO, Duration.ofDays(1), List.of(), escenario.vehiculos());

        assertEquals(2, resumen.pedidosRecibidos());
        assertTrue(resumen.pedidosOriginalesConAlgunaEntrega() <= 2);
    }
}
