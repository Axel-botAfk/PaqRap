import { aMs } from './reloj.js';

/** Asignaciones por fracción. Un hito solo es real cuando el reloj visible lo alcanza. */
export function enviosEn(planes, instante) {
  const t = aMs(instante);
  if (!Number.isFinite(t)) return [];
  const filas = new Map();
  const entregados = new Map();
  for (const plan of [...(planes || [])].filter(p => aMs(p.reloj) <= t)
    .sort((a, b) => aMs(a.reloj) - aMs(b.reloj) || a.indice - b.indice)) {
    for (const ruta of plan.rutas || []) {
      const pasos = (plan.trayectos || []).filter(v => v.vehiculoId === ruta.vehiculoId)
        .flatMap(v => v.pasos || []).sort((a, b) => aMs(a.instante) - aMs(b.instante));
      for (const pedido of ruta.paradas || []) {
        if (pedido.fechaRegistro && aMs(pedido.fechaRegistro) > t) continue;
        const entrega = pasos.find(p => p.pedidoId === pedido.id && p.hito === 'ENTREGA');
        const llegada = entrega?.instante || ruta.llegadasPrevistas?.[pedido.id] || null;
        const salida = pasos.filter(p => (p.hito === 'CARGA' || p.hito === 'SALIDA')
          && (!llegada || aMs(p.instante) <= aMs(llegada))).at(-1)?.instante || null;
        const carga = pasos.filter(p => p.hito === 'CARGA'
          && (!llegada || aMs(p.instante) <= aMs(llegada))).at(-1)?.instante || null;
        const anterior = filas.get(pedido.id);
        const mismoViaje = anterior?.unidad === ruta.vehiculoId && anterior?.almacen === ruta.almacenId;
        const fila = { ...pedido, original: pedido.idOriginal || pedido.id,
          unidad: ruta.vehiculoId, ruta: ruta.id, almacen: ruta.almacenId,
          salida: carga || (mismoViaje ? anterior.salida : null) || salida || ruta.salidaPrevista || null, llegada,
          entregaReal: !!entrega && aMs(entrega.instante) <= t,
          productos: pedido.cantidad };
        filas.set(pedido.id, fila);
        if (fila.entregaReal && !entregados.has(pedido.id)) entregados.set(pedido.id, fila);
      }
    }
  }
  for (const [id, fila] of entregados) filas.set(id, fila);
  return [...filas.values()];
}

export function ventanaDeEnvios(filas, instante, modo, horas = 4) {
  const t = aMs(instante), ancho = horas * 3600000;
  if (!Number.isFinite(t) || !Number.isFinite(ancho) || ancho <= 0) return [];
  return (filas || []).filter(f => {
    const s = aMs(f.salida), l = aMs(f.llegada);
    if (modo === 'transito') return Number.isFinite(s) && s <= t && !f.entregaReal;
    if (modo === 'salidas') return Number.isFinite(s) && s >= t && s < t + ancho;
    if (modo === 'entregados') return f.entregaReal && l > t - ancho && l <= t;
    if (modo === 'proximos') return !f.entregaReal && Number.isFinite(l) && l >= t && l < t + ancho;
    return true;
  });
}

export function agruparOriginales(filas) {
  const grupos = new Map();
  for (const fila of filas || []) {
    const id = fila.original || fila.idOriginal || fila.id;
    if (!grupos.has(id)) grupos.set(id, { id, fracciones: [], productos: 0 });
    const grupo = grupos.get(id);
    if (grupo.fracciones.some(f => f.id === fila.id)) continue;
    grupo.fracciones.push(fila);
    grupo.productos += fila.cantidad;
  }
  return [...grupos.values()];
}
