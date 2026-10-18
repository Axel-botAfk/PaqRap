import test from 'node:test';
import assert from 'node:assert/strict';
import { caminoEnGrilla, nodosBloqueados, posicionVisual, tramosDeRuta } from './mapa.js';

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
