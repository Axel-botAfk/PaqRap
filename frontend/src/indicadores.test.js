import test from 'node:test';
import assert from 'node:assert/strict';
import { ocupacionDeFlota, ocupacionDeAlmacenes } from './indicadores.js';
import { estadoDeCarga } from './carga.js';
import { estadoDeStock } from './stock.js';

test('la flota suma cargas contra capacidades', () => {
  const resultado = ocupacionDeFlota([
    { cargaAbordo: 10, capacidad: 10 }, { cargaAbordo: 0, capacidad: 10 }
  ]);
  assert.equal(resultado.carga, 10);
  assert.equal(resultado.capacidad, 20);
  assert.equal(resultado.porcentaje, 50);
  assert.equal(resultado.unidades, 2);
});

test('el semáforo global usa los mismos cortes que el de cada unidad', () => {
  assert.equal(ocupacionDeFlota([{ cargaAbordo: 7, capacidad: 10 }]).estado,
    estadoDeCarga({ cargaAbordo: 7, capacidad: 10 }));
  assert.equal(ocupacionDeFlota([{ cargaAbordo: 3, capacidad: 10 }]).estado, 'baja');
  assert.equal(ocupacionDeFlota([{ cargaAbordo: 5, capacidad: 10 }]).estado, 'intermedia');
  assert.equal(ocupacionDeFlota([{ cargaAbordo: 0, capacidad: 10 }]).estado, 'vacio');
});

test('una flota sin datos medibles no declara porcentaje', () => {
  const resultado = ocupacionDeFlota([{ cargaAbordo: null, capacidad: 10 }]);
  assert.equal(resultado.porcentaje, null);
  assert.equal(resultado.estado, 'sin-datos');
  assert.equal(ocupacionDeFlota([]).estado, 'sin-datos');
});

test('una carga mayor que la capacidad no se promedia: se descarta la unidad', () => {
  assert.equal(ocupacionDeFlota([{ cargaAbordo: 20, capacidad: 10 }]).estado, 'sin-datos');
});

test('los almacenes se miden contra su stock inicial', () => {
  const inicial = new Map([['ALM-NOR', 1000], ['ALM-SUR', 1000]]);
  const resultado = ocupacionDeAlmacenes([
    { id: 'ALM-NOR', disponible: 900 }, { id: 'ALM-SUR', disponible: 700 }
  ], inicial);
  assert.equal(resultado.porcentaje, 80);
  assert.equal(resultado.estado, estadoDeStock({ disponible: 1600 }, 2000));
  assert.equal(resultado.unidades, 2);
});

test('el central ilimitado no entra en el porcentaje pero se informa', () => {
  const inicial = new Map([['ALM-NOR', 1000]]);
  const resultado = ocupacionDeAlmacenes([
    { id: 'ALM-CENTRAL', ilimitado: true }, { id: 'ALM-NOR', disponible: 100 }
  ], inicial);
  assert.equal(resultado.capacidad, 1000);
  assert.equal(resultado.porcentaje, 10);
  assert.equal(resultado.estado, 'critico');
  assert.equal(resultado.ilimitados, 1);
});

test('sin stock inicial conocido no se afirma ocupación de almacenes', () => {
  const resultado = ocupacionDeAlmacenes([{ id: 'ALM-NOR', disponible: 100 }], new Map());
  assert.equal(resultado.porcentaje, null);
  assert.equal(resultado.estado, 'sin-datos');
});

test('almacenes vacíos en conjunto se marcan vacíos', () => {
  const inicial = new Map([['ALM-NOR', 1000]]);
  assert.equal(ocupacionDeAlmacenes([{ id: 'ALM-NOR', disponible: 0 }], inicial).estado, 'vacio');
});
