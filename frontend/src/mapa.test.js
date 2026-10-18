import test from 'node:test';
import assert from 'node:assert/strict';
import { caminoEnGrilla, nodosBloqueados, posicionVisual, tramosDeRuta, planVigenteEn } from './mapa.js';

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
