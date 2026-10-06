package com.paqrap.simulacion;

/**
 * Cómo va la operación en el instante en que se planifica.
 *
 * <h2>Para qué existe</h2>
 *
 * El plan que recibe un {@link Observador} dice qué se pretende hacer a partir de ahora, pero no
 * dice nada de lo que ya pasó. Sin eso no se puede seguir una corrida: no se sabe si la cola de
 * cincuenta pedidos viene de un mal día o de una semana acumulando, ni si alguien ya se quedó sin
 * plazo.
 *
 * Son cifras acumuladas desde el arranque, no del último salto.
 *
 * <h2>Dónde está cada pedido</h2>
 *
 * En todo momento un pedido está en uno de cuatro sitios, y entre los cuatro suman lo recibido:
 *
 * <ul>
 *   <li><b>entregado</b>: la unidad llegó a su destino. Es definitivo.</li>
 *   <li><b>vencido</b>: se le pasó el plazo sin que llegara nadie. También es definitivo, y el
 *       primero de ellos es lo que declara el colapso logístico.</li>
 *   <li><b>en cola</b>: sigue esperando, lo vea o no el plan de esta vuelta.</li>
 *   <li><b>en ruta</b>: su producto ya viaja encima de una unidad. Esto no se cuenta en pedidos
 *       sino en producto ({@code productosABordo}), porque el producto es fungible y lo que una
 *       unidad lleva no tiene nombre: sirve para cualquier cliente, y eso es justamente lo que
 *       permite redirigirla a mitad de camino.</li>
 * </ul>
 *
 * @param pedidosEntregados   pedidos puestos en manos del cliente hasta ahora.
 * @param productosEntregados los productos de esas entregas.
 * @param pedidosVencidos     pedidos que perdieron su plazo sin ser entregados.
 * @param productosVencidos   los productos de esos pedidos.
 * @param productosABordo     producto que en este instante viaja encima de alguna unidad.
 */
public record AvanceDeLaOperacion(
        int pedidosEntregados,
        int productosEntregados,
        int pedidosVencidos,
        int productosVencidos,
        int productosABordo
) {
}
