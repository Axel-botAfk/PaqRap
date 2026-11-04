import test from 'node:test';
import assert from 'node:assert/strict';
import { filasDeUnidades, ordenarUnidades } from './unidades.js';

const flota = [
  { id: 'TA02', tipo: 'MOTO', estado: 'EN_RUTA', capacidad: 8, cargaAbordo: 4, ubicacion: { x: 1, y: 1 } },
  { id: 'TA01', tipo: 'AUTO', estado: 'EN_RUTA', capacidad: 10, cargaAbordo: 9, ubicacion: { x: 2, y: 2 } },
  { id: 'TA10', tipo: 'AUTO', estado: 'DISPONIBLE', capacidad: 10, cargaAbordo: 0, ubicacion: { x: 3, y: 3 } }
];
const rutas = [
  { id: 'R-1', vehiculoId: 'TA01', paradas: [{ id: 'P-1', cantidad: 5 }, { id: 'P-2', cantidad: 4 }] },
  { id: 'R-2', vehiculoId: 'TA02', paradas: [{ id: 'P-3', cantidad: 4 }] }
];

test('cada fila trae ocupación en número y en porcentaje', () => {
  const filas = filasDeUnidades(flota, rutas);
  const ta01 = filas.find(f => f.id === 'TA01');
  assert.equal(ta01.cargaAbordo, 9);
  assert.equal(ta01.capacidad, 10);
  assert.equal(ta01.porcentaje, 90);
  assert.equal(ta01.nivel, 'alta');
});

test('la fila enlaza los pedidos y suma sus productos', () => {
  const ta01 = filasDeUnidades(flota, rutas).find(f => f.id === 'TA01');
  assert.equal(ta01.ruta, 'R-1');
  assert.deepEqual(ta01.pedidos.map(p => p.id), ['P-1', 'P-2']);
  assert.equal(ta01.productos, 9);
});

test('una unidad sin ruta asignada no inventa pedidos', () => {
  const ta10 = filasDeUnidades(flota, rutas).find(f => f.id === 'TA10');
  assert.equal(ta10.ruta, null);
  assert.deepEqual(ta10.pedidos, []);
  assert.equal(ta10.productos, 0);
  assert.equal(ta10.nivel, 'vacio');
});

test('sin capacidad publicada la ocupación queda en nulo', () => {
  const filas = filasDeUnidades([{ id: 'TA99', tipo: 'AUTO', capacidad: 0, cargaAbordo: 0 }], []);
  assert.equal(filas[0].porcentaje, null);
  assert.equal(filas[0].cargaAbordo, null);
});

test('ordena por ocupación descendente y ascendente', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'ocupacion-desc').map(f => f.id), ['TA01', 'TA02', 'TA10']);
  assert.deepEqual(ordenarUnidades(filas, 'ocupacion-asc').map(f => f.id), ['TA10', 'TA02', 'TA01']);
});

test('ordena por unidad con criterio numérico', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'id').map(f => f.id), ['TA01', 'TA02', 'TA10']);
});

test('ordena por cantidad de pedidos', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'pedidos-desc').map(f => f.id), ['TA01', 'TA02', 'TA10']);
});

test('las unidades sin ocupación medible quedan al final', () => {
  const filas = filasDeUnidades([...flota, { id: 'TA99', tipo: 'AUTO', capacidad: 0, cargaAbordo: 0 }], rutas);
  assert.equal(ordenarUnidades(filas, 'ocupacion-asc').at(-1).id, 'TA99');
  assert.equal(ordenarUnidades(filas, 'ocupacion-desc').at(-1).id, 'TA99');
});

test('ordenar no muta la lista original', () => {
  const filas = filasDeUnidades(flota, rutas);
  const antes = filas.map(f => f.id);
  ordenarUnidades(filas, 'ocupacion-desc');
  assert.deepEqual(filas.map(f => f.id), antes);
});
