import test from 'node:test';
import assert from 'node:assert/strict';
import { corridaRecuperable, unicaCorridaActiva, decidirCorrida } from './conexionCorridas.js';

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

test('otro dispositivo encuentra la corrida aunque sea de otro escenario', () => {
  const colapso = { id: 'c', escenario: 'COLAPSO', estado: 'EN_CURSO' };
  const diaria = { id: 'd', escenario: 'OPERACION_DIARIA', estado: 'PAUSADA' };
  const terminada = { id: 't', escenario: 'OPERACION_DIARIA', estado: 'TERMINADA' };
  // Recién abierta la página en Operación diaria y la única corrida es un colapso: se adopta.
  assert.equal(decidirCorrida([colapso], 'OPERACION_DIARIA', true).adoptar, colapso);
  // Si la persona ya eligió una vista, no se la cambia: la corrida se ofrece.
  const ofrecida = decidirCorrida([colapso], 'PERIODO_5_DIAS', false);
  assert.equal(ofrecida.adoptar, null);
  assert.deepEqual(ofrecida.ofrecer, [colapso]);
  // La del escenario seleccionado gana aunque haya otras activas.
  assert.equal(decidirCorrida([colapso, diaria], 'OPERACION_DIARIA', false).adoptar, diaria);
  // Varias y ninguna del escenario: se listan todas, sin adoptar.
  assert.deepEqual(decidirCorrida([colapso, diaria], 'PERIODO_5_DIAS', true),
    { adoptar: null, ofrecer: [colapso, diaria] });
  // Las finalizadas no cuentan.
  assert.deepEqual(decidirCorrida([terminada], 'OPERACION_DIARIA', true), { adoptar: null, ofrecer: [] });
  assert.deepEqual(decidirCorrida(null, 'COLAPSO', true), { adoptar: null, ofrecer: [] });
});
