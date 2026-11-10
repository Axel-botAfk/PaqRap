import test from 'node:test';
import assert from 'node:assert/strict';
import { corridaRecuperable, unicaCorridaActiva } from './conexionCorridas.js';

const diaria = { id: 'diaria', escenario: 'OPERACION_DIARIA', estado: 'EN_CURSO' };
const semanal = { id: 'semanal', escenario: 'PERIODO_5_DIAS', estado: 'PAUSADA' };

test('una sola corrida activa del escenario se puede recuperar automáticamente', () => {
  assert.equal(unicaCorridaActiva([diaria], 'OPERACION_DIARIA'), diaria);
  assert.equal(unicaCorridaActiva([semanal], 'PERIODO_5_DIAS'), semanal);
  assert.equal(corridaRecuperable({ ...diaria, estado: 'PENDIENTE' }, 'OPERACION_DIARIA'), true);
});

test('ninguna, varias, finalizadas u otro escenario requieren decisión o dejan la vista inicial', () => {
  assert.equal(unicaCorridaActiva([], 'OPERACION_DIARIA'), null);
  assert.equal(unicaCorridaActiva([diaria, { ...diaria, id: 'otra' }], 'OPERACION_DIARIA'), null);
  assert.equal(unicaCorridaActiva([{ ...diaria, estado: 'TERMINADA' }], 'OPERACION_DIARIA'), null);
  assert.equal(unicaCorridaActiva([semanal], 'OPERACION_DIARIA'), null);
  assert.equal(corridaRecuperable({ ...semanal, estado: 'CANCELADA' }, 'PERIODO_5_DIAS'), false);
});
