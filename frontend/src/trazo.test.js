import test from 'node:test';
import assert from 'node:assert/strict';
import { posicionEnTrayecto, prepararTrayectos, cargaEnTrayecto, orientacionEnTrayecto, tramosDeRuta,
  trazosConRecorrido, segmentosEnInstante, rutaFinalizadaEn } from './mapa.js';
import { aMs } from './reloj.js';

// Ruta del plan desde el central (27,14) a dos clientes; el simulador informa que la unidad
// salió de otra esquina y que el corte la encontró en (40,20), fuera de la "L" del plan.
const ruta = {
  id: 'R1', vehiculoId: 'TA01', origen: { x: 27, y: 14 },
  paradas: [{ id: 'P-1', destino: { x: 45, y: 25 } }, { id: 'P-2', destino: { x: 30, y: 40 } }]
};
const trayectos = [{ vehiculoId: 'TA01', pasos: [
  { ubicacion: { x: 30, y: 14 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
  { ubicacion: { x: 40, y: 20 }, instante: '2026-09-01T02:20:00', hito: 'CORTE' },
  { ubicacion: { x: 40, y: 20 }, instante: '2026-09-01T02:20:00', hito: 'SALIDA' },
  { ubicacion: { x: 45, y: 25 }, instante: '2026-09-01T02:30:00', hito: 'ENTREGA', pedidoId: 'P-1' },
  { ubicacion: { x: 45, y: 25 }, instante: '2026-09-01T02:50:00', hito: 'FIN_ENTREGA', pedidoId: 'P-1' },
  { ubicacion: { x: 41, y: 25 }, instante: '2026-09-01T03:00:00', hito: 'CORTE' }
] }];

const nodos = trazo => new Set(trazo.tramos.flat().map(p => `${p.x},${p.y}`));

test('la carga sigue los hitos reales al avanzar, pausar y retroceder', () => {
  const preparado = prepararTrayectos([{ vehiculoId: 'TA01', pasos: [
    { ubicacion: { x: 27, y: 14 }, instante: '2026-09-01T08:30:00', hito: 'SALIDA', cargaAbordo: 0 },
    { ubicacion: { x: 27, y: 14 }, instante: '2026-09-01T08:35:00', hito: 'CARGA', cargaAbordo: 12 },
    { ubicacion: { x: 30, y: 15 }, instante: '2026-09-01T08:45:00', hito: 'ENTREGA', cargaAbordo: 4 }
  ] }], aMs).get('TA01');
  assert.equal(cargaEnTrayecto(preparado, aMs('2026-09-01T08:33:00'), 0), 0);
  assert.equal(cargaEnTrayecto(preparado, aMs('2026-09-01T08:40:00'), 0), 12);
  assert.equal(cargaEnTrayecto(preparado, aMs('2026-09-01T08:45:00'), 0), 4);
  assert.equal(cargaEnTrayecto(preparado, aMs('2026-09-01T08:34:00'), 0), 0);
});

test('el giro sigue cada tramo y conserva el último rumbo durante una espera', () => {
  const preparado = prepararTrayectos([{ vehiculoId: 'TA01', pasos: [
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00' },
    { ubicacion: { x: 2, y: 0 }, instante: '2026-09-01T02:02:00' },
    { ubicacion: { x: 2, y: 2 }, instante: '2026-09-01T02:04:00' },
    { ubicacion: { x: 2, y: 2 }, instante: '2026-09-01T02:10:00' },
    { ubicacion: { x: 1, y: 2 }, instante: '2026-09-01T02:11:00' }
  ] }], aMs).get('TA01');
  assert.equal(orientacionEnTrayecto(preparado, aMs('2026-09-01T01:59:00')), null);
  assert.equal(orientacionEnTrayecto(preparado, aMs('2026-09-01T02:01:00')), 0);
  assert.equal(orientacionEnTrayecto(preparado, aMs('2026-09-01T02:03:00')), -90);
  assert.equal(orientacionEnTrayecto(preparado, aMs('2026-09-01T02:06:00')), -90);
  assert.equal(orientacionEnTrayecto(preparado, aMs('2026-09-01T02:11:00')), 180);
});

test('en vivo la unidad va siempre encima de la línea de su ruta', () => {
  const preparados = prepararTrayectos(trayectos, aMs);
  const [trazo] = trazosConRecorrido([ruta], preparados);
  const linea = nodos(trazo);
  for (let minuto = 0; minuto <= 60; minuto++) {
    const instante = aMs('2026-09-01T02:00:00') + minuto * 60_000;
    const p = posicionEnTrayecto(preparados.get('TA01'), instante);
    assert.ok(linea.has(`${p.x},${p.y}`), `minuto ${minuto}: (${p.x},${p.y}) fuera de la línea`);
  }
  // Desde donde queda, la línea sigue hacia la parada que falta.
  assert.ok(linea.has('30,40'));
});

test('sin recorrido la ruta se traza desde su almacén, como en el historial', () => {
  const [trazo] = trazosConRecorrido([ruta], new Map());
  assert.deepEqual(trazo.tramos, tramosDeRuta(ruta, new Set()));
});

test('el recorrido se dibuja una sola vez aunque la unidad tenga varios viajes', () => {
  const segundoViaje = { ...ruta, id: 'R2', paradas: [{ id: 'P-3', destino: { x: 10, y: 10 } }] };
  const preparados = prepararTrayectos(trayectos, aMs);
  const [, segundo] = trazosConRecorrido([ruta, segundoViaje], preparados);
  // El segundo viaje no empezó: sale de su almacén y no repite el recorrido del primero.
  assert.deepEqual(segundo.tramos, tramosDeRuta(segundoViaje, new Set()));
});

test('las aristas recorridas siguen el hito, incluso al retroceder y durante una parada', () => {
  const preparado = prepararTrayectos([{ vehiculoId: 'TA01', pasos: [
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
    { ubicacion: { x: 2, y: 1 }, instante: '2026-09-01T02:04:00', hito: 'ENTREGA', pedidoId: 'P-1' },
    { ubicacion: { x: 2, y: 1 }, instante: '2026-09-01T02:08:00', hito: 'FIN_ENTREGA', pedidoId: 'P-1' },
    { ubicacion: { x: 1, y: 1 }, instante: '2026-09-01T02:10:00', hito: 'ENTREGA', pedidoId: 'P-2' }
  ] }], aMs);
  const [trazo] = trazosConRecorrido([{
    id: 'R1', vehiculoId: 'TA01', origen: { x: 0, y: 0 },
    paradas: [{ id: 'P-1', destino: { x: 2, y: 1 } }, { id: 'P-2', destino: { x: 1, y: 1 } }]
  }], preparado);
  const visibles = instante => trazo.detalles.flatMap(detalle => segmentosEnInstante(detalle, aMs(instante)));
  const contarAristas = (segmentos, recorrido) => segmentos
    .filter(segmento => segmento.recorrido === recorrido)
    .reduce((total, segmento) => total + segmento.camino.length - 1, 0);
  assert.equal(contarAristas(visibles('2026-09-01T02:02:00'), true), 1);
  assert.equal(contarAristas(visibles('2026-09-01T02:06:00'), true), 3);
  assert.equal(contarAristas(visibles('2026-09-01T02:06:00'), false), 1);
  assert.equal(contarAristas(visibles('2026-09-01T02:10:00'), true), 4);
  assert.equal(contarAristas(visibles('2026-09-01T02:01:00'), true), 0);
  assert.deepEqual(visibles('2026-09-01T02:02:00')[0].camino.at(-1),
    posicionEnTrayecto(preparado.get('TA01'), aMs('2026-09-01T02:02:00')));
});

test('dos viajes de la misma unidad conservan sus tramos y resaltado por ruta', () => {
  const segundaRuta = { id: 'R2', vehiculoId: 'TA01', origen: { x: 2, y: 1 },
    paradas: [{ id: 'P-2', destino: { x: 3, y: 1 } }] };
  const preparados = prepararTrayectos([{ vehiculoId: 'TA01', pasos: [
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
    { ubicacion: { x: 2, y: 1 }, instante: '2026-09-01T02:03:00', hito: 'ENTREGA', pedidoId: 'P-1' },
    { ubicacion: { x: 3, y: 1 }, instante: '2026-09-01T02:05:00', hito: 'ENTREGA', pedidoId: 'P-2' }
  ] }], aMs);
  const [primera, segunda] = trazosConRecorrido([
    { id: 'R1', vehiculoId: 'TA01', origen: { x: 0, y: 0 },
      paradas: [{ id: 'P-1', destino: { x: 2, y: 1 } }] }, segundaRuta
  ], preparados);
  assert.equal(primera.detalles.filter(detalle => detalle.origenMs !== undefined).length, 1);
  assert.equal(segunda.detalles.filter(detalle => detalle.origenMs !== undefined).length, 1);
  assert.equal(segmentosEnInstante(segunda.detalles[0], aMs('2026-09-01T02:04:00'))[0].recorrido, false);
  assert.equal(segmentosEnInstante(primera.detalles[0], aMs('2026-09-01T02:04:00'))[0].recorrido, true);
});

test('la ruta se atenúa después del fin de entrega, nunca durante el servicio', () => {
  const preparado = prepararTrayectos(trayectos, aMs).get('TA01');
  assert.equal(rutaFinalizadaEn(ruta, preparado, aMs('2026-09-01T02:30:00')), false);
  assert.equal(rutaFinalizadaEn(ruta, preparado, aMs('2026-09-01T02:50:00')), false);
  const primeraParada = { ...ruta, paradas: [ruta.paradas[0]] };
  assert.equal(rutaFinalizadaEn(primeraParada, preparado, aMs('2026-09-01T02:49:00')), false);
  assert.equal(rutaFinalizadaEn(primeraParada, preparado, aMs('2026-09-01T02:50:00')), true);
  assert.equal(rutaFinalizadaEn(primeraParada, preparado, aMs('2026-09-01T02:30:00')), false);
  const sinAcondicionamiento = { ...primeraParada, paradas: [{ id: 'P-2' }] };
  const soloEntrega = prepararTrayectos([{ vehiculoId: 'TA01', pasos: [
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
    { ubicacion: { x: 1, y: 0 }, instante: '2026-09-01T02:31:00', hito: 'ENTREGA', pedidoId: 'P-2' }
  ] }], aMs).get('TA01');
  assert.equal(rutaFinalizadaEn(sinAcondicionamiento, soloEntrega, aMs('2026-09-01T02:31:00')), true);
});
