import test from 'node:test';
import assert from 'node:assert/strict';
import { pedidosEnInstante } from './pedidosEnVivo.js';
import { estadosDePedido } from './mapa.js';
import { aMs } from './reloj.js';

const hora = m => `2029-01-15T08:${String(m).padStart(2, '0')}:00`;
const p = (id, minuto = 0) => ({ id, fechaRegistro: hora(minuto), destino: { x: 5, y: 5 } });
const ruta = (id, vehiculoId, paradas) => ({ id, vehiculoId, tipoVehiculo: 'AUTO', paradas });

test('conserva tres pedidos simultáneos y dos destinos coincidentes al cambiar el plan', () => {
  const planes = [
    { indice: 0, reloj: hora(0), rutas: [ruta('r1', 'TA01', [p('p1'), p('p2')]), ruta('r2', 'TA02', [p('p3')])] },
    { indice: 1, reloj: hora(10), rutas: [ruta('r3', 'TA03', [p('p2')])] }
  ];
  const visibles = pedidosEnInstante(planes, aMs(hora(12)), aMs);
  assert.deepEqual(visibles.map(p => p.id).sort(), ['p1', 'p2', 'p3']);
  assert.equal(visibles.find(p => p.id === 'p2').vehiculo, 'TA03');
  assert.deepEqual(visibles[0].destino, visibles[2].destino);
});

test('no incorpora planes futuros ni pedidos cuya recepción todavía no ocurrió', () => {
  const planes = [
    { reloj: hora(0), rutas: [ruta('r1', 'TA01', [p('p1'), p('futuro', 20)])] },
    { reloj: hora(15), rutas: [ruta('r2', 'TA02', [p('p2')])] }
  ];
  assert.deepEqual(pedidosEnInstante(planes, aMs(hora(10)), aMs).map(p => p.id), ['p1']);
});

test('reconoce entrega pasada y conserva fracciones separadas del mismo original', () => {
  const planes = [{ reloj: hora(0), rutas: [ruta('r1', 'TA01', [
    { ...p('p1a'), idOriginal: 'p1' }, { ...p('p1b'), idOriginal: 'p1' }
  ])] }];
  const visibles = pedidosEnInstante(planes, aMs(hora(10)), aMs, new Map([['p1a', 'ENTREGADO']]));
  assert.equal(visibles.length, 2);
  assert.equal(visibles.find(p => p.id === 'p1a').situacion, 'ENTREGADO');
  assert.equal(visibles.find(p => p.id === 'p1b').situacion, 'PLANIFICADO');
});

test('no deja en ruta una asignación que volvió a no asignado', () => {
  const planes = [{ reloj: hora(0), rutas: [ruta('r1', 'TA01', [p('p1')])] },
    { reloj: hora(10), rutas: [], noAsignados: [p('p1')] }];
  assert.equal(pedidosEnInstante(planes, aMs(hora(12)), aMs,
    new Map([['p1', 'EN_RUTA']]))[0].situacion, 'NO_ASIGNADO');
});

test('los hitos de un plan futuro no anticipan una entrega', () => {
  const planes = [{ reloj: hora(20), trayectos: [{ vehiculoId: 'TA01', pasos: [
    { instante: hora(5), hito: 'ENTREGA', pedidoId: 'p1' }
  ] }] }];
  assert.equal(estadosDePedido(planes, aMs(hora(10)), aMs).has('p1'), false);
});
