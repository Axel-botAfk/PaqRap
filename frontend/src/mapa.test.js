import test from 'node:test';
import assert from 'node:assert/strict';
import { caminoEnGrilla, nodosBloqueados, posicionVisual, tramosDeRuta, planVigenteEn,
  estadosDePedido, xEnMapa, yEnMapa, vehiculosObservadosEn } from './mapa.js';
import { aMs } from './reloj.js';

test('las coordenadas publicadas por la API conservan el nodo al proyectarse en SVG', () => {
  assert.deepEqual([xEnMapa(27), yEnMapa(14)], [270, 360]);
  assert.deepEqual([xEnMapa(12), yEnMapa(38)], [120, 120]);
  assert.deepEqual([xEnMapa(70), yEnMapa(50)], [700, 0]);
  assert.deepEqual([xEnMapa(0), yEnMapa(0)], [0, 500]);
});

test('pausar, avanzar y retroceder muestran exactamente el fotograma elegido', () => {
  const anterior = [{ id: 'TA01', ubicacion: { x: 12, y: 38 }, cargaAbordo: 4 }];
  const posterior = [{ id: 'TA01', ubicacion: { x: 27, y: 14 }, cargaAbordo: 0 }];
  const fotogramas = [
    { reloj: '2026-09-01T08:30:00', vehiculos: anterior },
    { reloj: '2026-09-01T08:45:00', vehiculos: posterior }
  ];
  const ejecucion = { reloj: '2026-09-01T08:45:00', vehiculos: posterior };
  assert.equal(vehiculosObservadosEn(fotogramas, 0, ejecucion, false,
    '2026-09-01T08:30:00', aMs), anterior);
  assert.equal(vehiculosObservadosEn(fotogramas, 1, ejecucion, false,
    '2026-09-01T08:45:00', aMs), posterior);
  assert.equal(vehiculosObservadosEn(fotogramas, 0, ejecucion, true,
    '2026-09-01T08:33:00', aMs), anterior);
});

test('cada paso de la ruta es horizontal o vertical y de una arista', () => {
  const camino = caminoEnGrilla({ x: 1, y: 1 }, { x: 4, y: 3 });
  assert.deepEqual(camino[0], { x: 1, y: 1 });
  assert.deepEqual(camino.at(-1), { x: 4, y: 3 });
  assert.equal(camino.length, 6);
  for (let i = 1; i < camino.length; i++) {
    assert.equal(Math.abs(camino[i].x - camino[i - 1].x)
      + Math.abs(camino[i].y - camino[i - 1].y), 1);
  }
});

test('rodea un bloqueo activo sin atravesarlo', () => {
  const bloqueados = nodosBloqueados([{ nodos: [{ x: 2, y: 1 }] }]);
  const camino = caminoEnGrilla({ x: 1, y: 1 }, { x: 3, y: 1 }, bloqueados);
  assert.equal(camino.length, 5);
  assert.equal(camino.some(p => p.x === 2 && p.y === 1), false);
});

test('el camino visual sigue el desempate y la llegada a nodos cerrados del enrutador', () => {
  assert.deepEqual(caminoEnGrilla({ x: 2, y: 1 }, { x: 1, y: 2 }),
    [{ x: 2, y: 1 }, { x: 1, y: 1 }, { x: 1, y: 2 }]);
  const cerrado = nodosBloqueados([{ nodos: [{ x: 1, y: 0 }] }]);
  assert.deepEqual(caminoEnGrilla({ x: 0, y: 0 }, { x: 1, y: 0 }, cerrado),
    [{ x: 0, y: 0 }, { x: 1, y: 0 }]);
});

test('no dibuja un tramo cuando no hay acceso al destino', () => {
  const bloqueados = nodosBloqueados([{ nodos: [{ x: 0, y: 1 }, { x: 1, y: 0 }] }]);
  assert.deepEqual(caminoEnGrilla({ x: 0, y: 0 }, { x: 1, y: 1 }, bloqueados), []);
  assert.deepEqual(tramosDeRuta({ origen: { x: 0, y: 0 }, paradas: [{ destino: { x: 1, y: 1 } }] }, bloqueados), []);
});

test('rechaza puntos fuera del modelo', () => {
  assert.deepEqual(caminoEnGrilla({ x: -1, y: 0 }, { x: 2, y: 2 }), []);
  assert.deepEqual(caminoEnGrilla({ x: 0, y: 0 }, { x: 71, y: 2 }), []);
});

test('la transición visual avanza por nodos cardinales y llega al destino', () => {
  const origen = { x: 1, y: 1 };
  const destino = { x: 4, y: 3 };
  let anterior = origen;
  for (let i = 1; i <= 20; i++) {
    const actual = posicionVisual(origen, destino, i / 20);
    assert.ok(Math.abs(actual.x - anterior.x) + Math.abs(actual.y - anterior.y) <= 1);
    anterior = actual;
  }
  assert.deepEqual(anterior, destino);
});

test('cada instante muestra su propio plan, incluso rutas simultáneas y varias paradas', () => {
  const planes = [
    { indice: 0, reloj: '2029-01-15T08:00:00', rutas: [
      { id: 'r1', paradas: [{ id: 'p1' }, { id: 'p2' }] },
      { id: 'r2', paradas: [{ id: 'p3' }] }
    ] },
    { indice: 1, reloj: '2029-01-15T08:30:00', rutas: [
      { id: 'r3', paradas: [{ id: 'p4' }] }
    ] }
  ];
  assert.equal(planVigenteEn(planes, '2029-01-15T07:59:00'), null);
  assert.equal(planVigenteEn(planes, '2029-01-15T08:15:00').rutas.length, 2);
  assert.equal(planVigenteEn(planes, '2029-01-15T08:15:00').rutas[0].paradas.length, 2);
  assert.equal(planVigenteEn(planes, '2029-01-15T08:45:00').rutas[0].id, 'r3');
});

test('una ruta de dos pedidos traza origen a pedido 1 y luego pedido 1 a pedido 2', () => {
  const ruta = { origen: { x: 1, y: 1 }, paradas: [
    { id: 'p1', destino: { x: 3, y: 1 } },
    { id: 'p2', destino: { x: 3, y: 3 } }
  ] };
  const tramos = tramosDeRuta(ruta, new Set());
  assert.equal(tramos.length, 2);
  assert.deepEqual(tramos[0][0], ruta.origen);
  assert.deepEqual(tramos[0].at(-1), ruta.paradas[0].destino);
  assert.deepEqual(tramos[1][0], ruta.paradas[0].destino);
  assert.deepEqual(tramos[1].at(-1), ruta.paradas[1].destino);
});

// La API no publica «en ruta» ni «entregado»; se deducen de los hitos de los trayectos.
const PLANES_CON_HITOS = [
  { indice: 0, reloj: '2029-01-15T08:00:00', trayectos: [
    { vehiculoId: 'AUTO-1', pasos: [
      { hito: 'SALIDA', instante: '2029-01-15T08:00:00', pedidoId: null },
      { hito: 'ENTREGA', instante: '2029-01-15T08:20:00', pedidoId: 'p1' },
      { hito: 'ENTREGA', instante: '2029-01-15T08:40:00', pedidoId: 'p2' }
    ] },
    { vehiculoId: 'MOTO-2', pasos: [
      { hito: 'SALIDA', instante: '2029-01-15T08:30:00', pedidoId: null },
      { hito: 'ENTREGA', instante: '2029-01-15T08:50:00', pedidoId: 'p3' }
    ] }
  ] }
];

test('un pedido queda entregado cuando su hito de entrega ya pasó', () => {
  const estados = estadosDePedido(PLANES_CON_HITOS, aMs('2029-01-15T08:25:00'), aMs);
  assert.equal(estados.get('p1'), 'ENTREGADO');
  assert.equal(estados.get('p2'), 'EN_RUTA');
});

test('un pedido está en ruta solo después de que su unidad salió', () => {
  const antes = estadosDePedido(PLANES_CON_HITOS, aMs('2029-01-15T08:10:00'), aMs);
  assert.equal(antes.get('p3'), undefined, 'MOTO-2 aún no sale: p3 sigue planificado');
  const despues = estadosDePedido(PLANES_CON_HITOS, aMs('2029-01-15T08:35:00'), aMs);
  assert.equal(despues.get('p3'), 'EN_RUTA');
});

test('una entrega de un plan anterior no vuelve a «en ruta» en uno posterior', () => {
  const planes = [...PLANES_CON_HITOS, { indice: 1, reloj: '2029-01-15T08:30:00', trayectos: [
    { vehiculoId: 'AUTO-1', pasos: [
      { hito: 'SALIDA', instante: '2029-01-15T08:30:00', pedidoId: null },
      { hito: 'ENTREGA', instante: '2029-01-15T09:10:00', pedidoId: 'p1' }
    ] }
  ] }];
  assert.equal(estadosDePedido(planes, aMs('2029-01-15T08:45:00'), aMs).get('p1'), 'ENTREGADO');
});

test('sin instante válido no inventa estados', () => {
  assert.equal(estadosDePedido(PLANES_CON_HITOS, NaN, aMs).size, 0);
  assert.equal(estadosDePedido(null, aMs('2029-01-15T08:25:00'), aMs).size, 0);
});

// Caso real de la ventana 1: la unidad sale, carga en el almacén y el corte llega antes de
// su primera entrega, así que el trayecto no informa ningún hito ENTREGA.
test('las paradas de una unidad que ya salió cuentan como en ruta aunque el corte llegue antes', () => {
  const planes = [{ indice: 0, reloj: '2026-09-01T08:00:00',
    rutas: [{ id: 'C-TA02', vehiculoId: 'TA02', paradas: [{ id: 'P-00002' }, { id: 'P-00008' }] }],
    trayectos: [{ vehiculoId: 'TA02', pasos: [
      { hito: 'SALIDA', instante: '2026-09-01T08:00:00', pedidoId: null },
      { hito: 'ALMACEN', instante: '2026-09-01T08:00:00', pedidoId: null },
      { hito: 'CORTE', instante: '2026-09-01T08:15:00', pedidoId: null }
    ] }] }];
  const estados = estadosDePedido(planes, aMs('2026-09-01T08:09:00'), aMs);
  assert.equal(estados.get('P-00002'), 'EN_RUTA');
  assert.equal(estados.get('P-00008'), 'EN_RUTA');
});

test('antes de que la unidad salga sus paradas siguen planificadas', () => {
  const planes = [{ indice: 0, reloj: '2026-09-01T08:00:00',
    rutas: [{ id: 'C-TA02', vehiculoId: 'TA02', paradas: [{ id: 'P-00002' }] }],
    trayectos: [{ vehiculoId: 'TA02', pasos: [
      { hito: 'SALIDA', instante: '2026-09-01T08:30:00', pedidoId: null }] }] }];
  assert.equal(estadosDePedido(planes, aMs('2026-09-01T08:09:00'), aMs).get('P-00002'), undefined);
});

test('una entrega ya ocurrida gana sobre las paradas en ruta de la misma ruta', () => {
  const planes = [{ indice: 0, reloj: '2026-09-01T08:00:00',
    rutas: [{ id: 'C-TA03', vehiculoId: 'TA03', paradas: [{ id: 'P-00031' }, { id: 'P-00040' }] }],
    trayectos: [{ vehiculoId: 'TA03', pasos: [
      { hito: 'SALIDA', instante: '2026-09-01T08:15:00', pedidoId: null },
      { hito: 'ENTREGA', instante: '2026-09-01T08:30:00', pedidoId: 'P-00031' },
      { hito: 'FIN_ENTREGA', instante: '2026-09-01T09:30:00', pedidoId: 'P-00031' }
    ] }] }];
  const estados = estadosDePedido(planes, aMs('2026-09-01T08:45:00'), aMs);
  assert.equal(estados.get('P-00031'), 'ENTREGADO');
  assert.equal(estados.get('P-00040'), 'EN_RUTA');
});
