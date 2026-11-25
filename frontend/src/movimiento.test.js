import test from 'node:test';
import assert from 'node:assert/strict';
import { aMs } from './reloj.js';
import { prepararTrayectos, posicionEnTrayecto, posicionContinuaEnTrayecto, posicionContinuaEnCamino,
  caminoEnGrilla } from './mapa.js';

const preparar = pasos => prepararTrayectos([{ vehiculoId: 'A1', pasos }], aMs).get('A1');
const en = (hora, tramo = '2026-09-01T') => aMs(`${tramo}${hora}`);

const recorrido = preparar([
  { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
  { ubicacion: { x: 4, y: 0 }, instante: '2026-09-01T02:04:00', hito: 'ENTREGA' },
  { ubicacion: { x: 4, y: 0 }, instante: '2026-09-01T03:04:00', hito: 'FIN_ENTREGA' },
  { ubicacion: { x: 4, y: 2 }, instante: '2026-09-01T03:06:00', hito: 'CORTE' }
]);

test('entre dos nodos la unidad se desliza en lugar de saltar', () => {
  assert.deepEqual(posicionContinuaEnTrayecto(recorrido, en('02:01:30')), { x: 1.5, y: 0 });
  assert.deepEqual(posicionContinuaEnTrayecto(recorrido, en('02:02:30')), { x: 2.5, y: 0 });
  // El nodo entero que usan los textos y los trazos no cambia.
  assert.deepEqual(posicionEnTrayecto(recorrido, en('02:01:30')), { x: 1, y: 0 });
});

test('en los nodos, en las esperas y fuera del recorrido coincide con la posición por nodo', () => {
  for (const hora of ['01:00:00', '02:00:00', '02:02:00', '02:04:00', '02:30:00', '03:04:00', '03:05:00', '05:00:00']) {
    assert.deepEqual(posicionContinuaEnTrayecto(recorrido, en(hora)), posicionEnTrayecto(recorrido, en(hora)), hora);
  }
  assert.equal(posicionContinuaEnTrayecto(undefined, 0), null);
});

test('muestreada cada segundo nunca avanza más de lo que corresponde a su velocidad', () => {
  // 4 aristas en 240 s: 1 arista cada 60 s, es decir 1/60 de nodo por segundo.
  let anterior = posicionContinuaEnTrayecto(recorrido, en('02:00:00'));
  for (let s = 1; s <= 240; s++) {
    const actual = posicionContinuaEnTrayecto(recorrido, aMs('2026-09-01T02:00:00') + s * 1000);
    const paso = Math.abs(actual.x - anterior.x) + Math.abs(actual.y - anterior.y);
    assert.ok(paso <= 1 / 60 + 1e-9, `segundo ${s}: avanzó ${paso}`);
    assert.ok(actual.x >= anterior.x, `segundo ${s}: retrocedió`);
    anterior = actual;
  }
  assert.deepEqual(anterior, { x: 4, y: 0 });
});

test('con giros sigue las aristas del camino: siempre queda sobre una calle', () => {
  const giro = preparar([
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
    { ubicacion: { x: 3, y: 2 }, instante: '2026-09-01T02:10:00', hito: 'ENTREGA' }
  ]);
  const camino = caminoEnGrilla({ x: 0, y: 0 }, { x: 3, y: 2 });
  for (let s = 0; s <= 600; s += 7) {
    const p = posicionContinuaEnTrayecto(giro, aMs('2026-09-01T02:00:00') + s * 1000);
    assert.ok(Number.isInteger(p.x) || Number.isInteger(p.y), `(${p.x}, ${p.y}) está fuera de las calles`);
    const sobreArista = camino.some((a, i) => {
      const b = camino[i + 1];
      return b && p.x >= Math.min(a.x, b.x) - 1e-9 && p.x <= Math.max(a.x, b.x) + 1e-9
        && p.y >= Math.min(a.y, b.y) - 1e-9 && p.y <= Math.max(a.y, b.y) + 1e-9;
    });
    assert.ok(sobreArista, `(${p.x}, ${p.y}) no está en el camino`);
  }
});

test('un camino sin aristas se resuelve igual que antes', () => {
  assert.deepEqual(posicionContinuaEnCamino([], { x: 1, y: 1 }, { x: 2, y: 2 }, 0.5), { x: 1, y: 1 });
  assert.deepEqual(posicionContinuaEnCamino([], { x: 1, y: 1 }, { x: 2, y: 2 }, 1), { x: 2, y: 2 });
  assert.deepEqual(posicionContinuaEnCamino([{ x: 5, y: 5 }], { x: 5, y: 5 }, { x: 5, y: 5 }, 0.3), { x: 5, y: 5 });
});
