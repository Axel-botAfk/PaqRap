import { estadoDeStock } from './stock.js';

/**
 * La lista de almacenes del panel (E18, E19 y E20).
 *
 * Reúne por almacén lo que ya publican la API y el plan vigente: el stock del corte que
 * corresponde al instante, su semáforo, y los envíos que salieron de ese almacén y todavía no
 * se entregan. «En tránsito» se calcula desde las rutas cuyo origen es el almacén, descontando
 * los pedidos ya entregados según los hitos del trayecto; no se inventa un estado que el
 * servidor no haya informado.
 *
 * La API no publica la capacidad de cada almacén, así que el llenado se mide contra el
 * `stockInicial` del caso y se dice de dónde sale.
 */
export function filasDeAlmacenes(inventario, almacenesDelMapa, rutas, estadosPedido = null,
  umbrales = undefined) {
  const stockPorId = new Map((inventario?.almacenes || []).map(a => [a.id, a]));
  const entregado = pedido => estadosPedido?.get(pedido.id) === 'ENTREGADO';

  return (almacenesDelMapa || []).map(almacen => {
    const stock = stockPorId.get(almacen.id) || null;
    const inicial = almacen.stockInicial > 0 ? almacen.stockInicial : null;
    const rutasDelAlmacen = (rutas || []).filter(ruta => ruta.almacenId === almacen.id);
    const pedidos = rutasDelAlmacen.flatMap(ruta => (ruta.paradas || [])
      .filter(pedido => !entregado(pedido))
      .map(pedido => ({ id: pedido.id, cantidad: pedido.cantidad,
        unidad: ruta.vehiculoId, ruta: ruta.id })));
    const medible = !stock?.ilimitado && Number.isFinite(stock?.disponible) && inicial;

    return {
      id: almacen.id,
      tipo: almacen.tipo,
      ubicacion: almacen.ubicacion,
      ilimitado: !!stock?.ilimitado,
      disponible: stock?.ilimitado ? null
        : Number.isFinite(stock?.disponible) ? stock.disponible : null,
      inicial,
      porcentaje: medible
        ? Math.max(0, Math.min(100, Math.round((stock.disponible / inicial) * 100))) : null,
      estado: estadoDeStock(stock, almacen.stockInicial, umbrales),
      enTransito: {
        pedidos,
        productos: pedidos.reduce((total, p) => total + (Number.isFinite(p.cantidad) ? p.cantidad : 0), 0),
        unidades: new Set(pedidos.map(p => p.unidad)).size
      }
    };
  });
}
