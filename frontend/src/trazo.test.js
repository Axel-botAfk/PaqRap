import test from 'node:test';
import assert from 'node:assert/strict';
import { posicionEnTrayecto, prepararTrayectos, tramosDeRuta, trazosConRecorrido } from './mapa.js';
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
