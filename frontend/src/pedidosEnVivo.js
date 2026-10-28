/** Pedidos conocidos hasta T, conservados por fracción y sin anticipar planes futuros. */
export function pedidosEnInstante(planes, instanteMs, aMs, estados = new Map()) {
  if (!Number.isFinite(instanteMs)) return [];
  const pedidos = new Map();
  const ordenados = (planes || []).filter(p => aMs(p.reloj) <= instanteMs)
    .slice().sort((a, b) => aMs(a.reloj) - aMs(b.reloj) || a.indice - b.indice);
  for (const plan of ordenados) {
    for (const ruta of plan.rutas || []) {
      for (const pedido of ruta.paradas || []) {
        if (pedido.fechaRegistro && aMs(pedido.fechaRegistro) > instanteMs) continue;
        pedidos.set(pedido.id, { ...pedido, ruta: ruta.id, vehiculo: ruta.vehiculoId,
          tipoVehiculo: ruta.tipoVehiculo, situacion: 'PLANIFICADO' });
      }
    }
    for (const pedido of plan.noAsignados || []) {
      if (pedido.fechaRegistro && aMs(pedido.fechaRegistro) > instanteMs) continue;
      pedidos.set(pedido.id, { ...pedido, ruta: null, vehiculo: null, situacion: 'NO_ASIGNADO' });
    }
  }
  return [...pedidos.values()].map(pedido => ({ ...pedido,
    situacion: estados.get(pedido.id) === 'ENTREGADO' ? 'ENTREGADO'
      : pedido.situacion === 'NO_ASIGNADO' ? 'NO_ASIGNADO'
        : estados.get(pedido.id) || pedido.situacion }));
}
