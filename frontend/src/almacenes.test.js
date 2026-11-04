import test from 'node:test';
import assert from 'node:assert/strict';
import { filasDeAlmacenes } from './almacenes.js';

const delMapa = [
  { id: 'ALM-CENTRAL', tipo: 'CENTRAL', ubicacion: { x: 35, y: 25 }, stockInicial: 0 },
  { id: 'ALM-NOR', tipo: 'INTERMEDIO', ubicacion: { x: 12, y: 8 }, stockInicial: 1000 },
  { id: 'ALM-SUR', tipo: 'INTERMEDIO', ubicacion: { x: 58, y: 42 }, stockInicial: 1000 }
];
const inventario = { reloj: '2029-01-15T10:40:00', almacenes: [
  { id: 'ALM-CENTRAL', ilimitado: true, disponible: null },
  { id: 'ALM-NOR', ilimitado: false, disponible: 800 },
  { id: 'ALM-SUR', ilimitado: false, disponible: 90 }
] };
const rutas = [
  { id: 'R-1', vehiculoId: 'TA01', almacenId: 'ALM-NOR',
    paradas: [{ id: 'P-1', cantidad: 5 }, { id: 'P-2', cantidad: 4 }] },
  { id: 'R-2', vehiculoId: 'TA02', almacenId: 'ALM-NOR', paradas: [{ id: 'P-3', cantidad: 3 }] },
  { id: 'R-3', vehiculoId: 'TA03', almacenId: 'ALM-SUR', paradas: [{ id: 'P-4', cantidad: 7 }] }
];

test('el stock se presenta en número y en porcentaje contra el inicial', () => {
  const nor = filasDeAlmacenes(inventario, delMapa, rutas).find(f => f.id === 'ALM-NOR');
  assert.equal(nor.disponible, 800);
  assert.equal(nor.inicial, 1000);
  assert.equal(nor.porcentaje, 80);
});

test('el semáforo usa los mismos cortes que el marcador del mapa', () => {
  const filas = filasDeAlmacenes(inventario, delMapa, rutas);
  assert.equal(filas.find(f => f.id === 'ALM-NOR').estado, 'suficiente');
  assert.equal(filas.find(f => f.id === 'ALM-SUR').estado, 'critico');
});

test('con otros umbrales configurados el semáforo del almacén cambia', () => {
  const filas = filasDeAlmacenes(inventario, delMapa, rutas, null, { critico: 85, advertencia: 95 });
  assert.equal(filas.find(f => f.id === 'ALM-NOR').estado, 'critico');
});

test('el central ilimitado no declara porcentaje', () => {
  const central = filasDeAlmacenes(inventario, delMapa, rutas).find(f => f.id === 'ALM-CENTRAL');
  assert.equal(central.ilimitado, true);
  assert.equal(central.porcentaje, null);
  assert.equal(central.estado, 'suficiente');
});

test('en tránsito reúne los envíos de las rutas que salen del almacén', () => {
  const nor = filasDeAlmacenes(inventario, delMapa, rutas).find(f => f.id === 'ALM-NOR');
  assert.equal(nor.enTransito.pedidos.length, 3);
  assert.equal(nor.enTransito.productos, 12);
  assert.equal(nor.enTransito.unidades, 2);
});

test('un pedido ya entregado deja de contar como en tránsito', () => {
  const estados = new Map([['P-1', 'ENTREGADO']]);
  const nor = filasDeAlmacenes(inventario, delMapa, rutas, estados).find(f => f.id === 'ALM-NOR');
  assert.deepEqual(nor.enTransito.pedidos.map(p => p.id), ['P-2', 'P-3']);
  assert.equal(nor.enTransito.productos, 7);
});

test('cada envío en tránsito dice con qué unidad y en qué ruta va', () => {
  const nor = filasDeAlmacenes(inventario, delMapa, rutas).find(f => f.id === 'ALM-NOR');
  assert.deepEqual(nor.enTransito.pedidos[0], { id: 'P-1', cantidad: 5, unidad: 'TA01', ruta: 'R-1' });
});

test('un almacén sin rutas no inventa envíos', () => {
  const central = filasDeAlmacenes(inventario, delMapa, rutas).find(f => f.id === 'ALM-CENTRAL');
  assert.equal(central.enTransito.pedidos.length, 0);
  assert.equal(central.enTransito.productos, 0);
});

test('sin corte de inventario el stock queda en nulo, no en cero', () => {
  const nor = filasDeAlmacenes(null, delMapa, rutas).find(f => f.id === 'ALM-NOR');
  assert.equal(nor.disponible, null);
  assert.equal(nor.porcentaje, null);
  assert.equal(nor.estado, 'sin-datos');
});
