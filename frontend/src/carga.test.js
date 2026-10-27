import test from 'node:test';
import assert from 'node:assert/strict';
import { estadoDeCarga, estadoDeGrupo } from './carga.js';

test('el indicador respeta los límites de 40 y 70 % de la capacidad', () => {
  assert.equal(estadoDeCarga({ cargaAbordo: 0, capacidad: 24 }), 'vacio');
  assert.equal(estadoDeCarga({ cargaAbordo: 24, capacidad: 24 }), 'alta');
  assert.equal(estadoDeCarga({ cargaAbordo: 39, capacidad: 100 }), 'baja');
  assert.equal(estadoDeCarga({ cargaAbordo: 40, capacidad: 100 }), 'intermedia');
  assert.equal(estadoDeCarga({ cargaAbordo: 41, capacidad: 100 }), 'intermedia');
  assert.equal(estadoDeCarga({ cargaAbordo: 69, capacidad: 100 }), 'intermedia');
  assert.equal(estadoDeCarga({ cargaAbordo: 70, capacidad: 100 }), 'alta');
  assert.equal(estadoDeCarga({ cargaAbordo: 71, capacidad: 100 }), 'alta');
  assert.equal(estadoDeCarga({ cargaAbordo: 1, capacidad: 4 }), 'baja');
  assert.equal(estadoDeCarga({ cargaAbordo: 2, capacidad: 4 }), 'intermedia');
  assert.equal(estadoDeCarga({ cargaAbordo: 3, capacidad: 4 }), 'alta');
  assert.equal(estadoDeCarga({ cargaAbordo: 25, capacidad: 24 }), 'sin-datos');
  assert.equal(estadoDeCarga({ cargaAbordo: null, capacidad: 24 }), 'sin-datos');
});

test('un grupo con cargas diferentes no se pinta como una sola unidad', () => {
  assert.equal(estadoDeGrupo([
    { cargaAbordo: 0, capacidad: 24 }, { cargaAbordo: 24, capacidad: 24 }
  ]), 'mixto');
  assert.equal(estadoDeGrupo([
    { cargaAbordo: 0, capacidad: 24 }, { cargaAbordo: 0, capacidad: 8 }
  ]), 'vacio');
});
