import { aMs } from './reloj.js';

/**
 * Reporte de la última planificación estable (G08, G09 y G10).
 *
 * «Estable» es el último plan que el planificador publicó y que ya no fue reemplazado por una
 * replanificación: al terminar la corrida es el último del historial; en un colapso, el último
 * vigente hasta el instante del primer incumplimiento. No se recalcula nada: se ordena lo que
 * el servidor publicó —rutas, horarios previstos y entregas observadas— para leerlo de corrido.
 */
export function ultimoPlanEstable(planes, limite = null) {
  const tope = aMs(limite);
  const candidatos = (planes || []).filter(plan => Number.isFinite(aMs(plan?.reloj))
    && (!Number.isFinite(tope) || aMs(plan.reloj) <= tope));
  if (!candidatos.length) return null;
  return candidatos.reduce((ultimo, plan) => (aMs(plan.reloj) > aMs(ultimo.reloj)
    || (aMs(plan.reloj) === aMs(ultimo.reloj) && plan.indice > ultimo.indice) ? plan : ultimo));
}

export function reporteDePlan(plan) {
  if (!plan) return null;
  const corte = aMs(plan.hasta);
  const rutas = (plan.rutas || []).map(ruta => {
    const pasos = (plan.trayectos || []).filter(t => t.vehiculoId === ruta.vehiculoId)
      .flatMap(t => t.pasos || []);
    const paradas = (ruta.paradas || []).map((pedido, orden) => {
      const entrega = pasos.find(p => p.hito === 'ENTREGA' && p.pedidoId === pedido.id
        && (!Number.isFinite(corte) || aMs(p.instante) <= corte));
      const llegada = entrega?.instante || ruta.llegadasPrevistas?.[pedido.id] || null;
      return {
        orden: orden + 1, id: pedido.id, original: pedido.idOriginal || pedido.id,
        destino: pedido.destino, cantidad: pedido.cantidad, limite: pedido.fechaLimite,
        llegada, entregado: !!entrega,
        enPlazo: llegada && pedido.fechaLimite ? aMs(llegada) <= aMs(pedido.fechaLimite) : null
      };
    });
    return {
      id: ruta.id, unidad: ruta.vehiculoId, tipo: ruta.tipoVehiculo, almacen: ruta.almacenId,
      carga: ruta.carga, km: ruta.distanciaKm, costo: ruta.costo,
      salida: ruta.salidaPrevista || null, paradas
    };
  });
  const paradas = rutas.flatMap(ruta => ruta.paradas);
  const suma = (lista, campo) => lista.reduce((total, x) =>
    total + (Number.isFinite(x[campo]) ? x[campo] : 0), 0);
  return {
    indice: plan.indice, reloj: plan.reloj, hasta: plan.hasta || null, rutas,
    noAsignados: (plan.noAsignados || []).map(p => ({ id: p.id, original: p.idOriginal || p.id,
      destino: p.destino, cantidad: p.cantidad, limite: p.fechaLimite, motivo: p.motivo || null })),
    totales: {
      rutas: rutas.length, unidades: new Set(rutas.map(r => r.unidad)).size,
      pedidos: paradas.length, productos: suma(paradas, 'cantidad'),
      entregados: paradas.filter(p => p.entregado).length,
      fueraDePlazo: paradas.filter(p => p.enPlazo === false).length,
      km: suma(rutas, 'km'), costo: suma(rutas, 'costo')
    }
  };
}

const celda = valor => {
  const texto = valor === null || valor === undefined ? '' : String(valor);
  return /[",;\n]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto;
};

/** Una fila por parada; separador «;» y BOM para que Excel en español lo abra sin asistente. */
export function reporteCsv(reporte) {
  if (!reporte) return '';
  const filas = [['Ruta', 'Unidad', 'Tipo', 'Almacén', 'Salida prevista', 'Km de la ruta',
    'Costo de la ruta', 'Orden', 'Fracción', 'Pedido original', 'Destino X', 'Destino Y',
    'Productos', 'Llegada', 'Plazo', 'Situación']];
  for (const ruta of reporte.rutas) {
    for (const p of ruta.paradas) {
      filas.push([ruta.id, ruta.unidad, ruta.tipo, ruta.almacen, ruta.salida, ruta.km, ruta.costo,
        p.orden, p.id, p.original, p.destino?.x, p.destino?.y, p.cantidad, p.llegada, p.limite,
        p.entregado ? 'Entregado' : 'Previsto']);
    }
  }
  for (const p of reporte.noAsignados) {
    filas.push(['', '', '', '', '', '', '', '', p.id, p.original, p.destino?.x, p.destino?.y,
      p.cantidad, '', p.limite, 'Sin asignar']);
  }
  return `﻿${filas.map(fila => fila.map(celda).join(';')).join('\r\n')}`;
}
