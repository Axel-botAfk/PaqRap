import test from 'node:test';
import assert from 'node:assert/strict';
import { SECCIONES, ESCENARIO_DE_SECCION, seccionActiva, TITULOS_SECCION } from './secciones.js';

test('el menú muestra las dos simulaciones justo debajo de la operación diaria', () => {
  assert.deepEqual(SECCIONES.slice(0, 3).map(([clave]) => clave), ['operacion', 'simulacion5d', 'colapso']);
  assert.equal(TITULOS_SECCION.simulacion5d, 'Simulación 5D');
  assert.equal(TITULOS_SECCION.colapso, 'Colapso logístico');
  assert.ok(!SECCIONES.some(([clave]) => clave === 'simulacion'), 'ya no existe la entrada genérica «Simulación»');
});

test('cada entrada de simulación abre su escenario', () => {
  assert.equal(ESCENARIO_DE_SECCION.simulacion5d, 'PERIODO_5_DIAS');
  assert.equal(ESCENARIO_DE_SECCION.colapso, 'COLAPSO');
  assert.equal(ESCENARIO_DE_SECCION.operacion, undefined);
});

test('la entrada resaltada sale de la vista y del escenario', () => {
  assert.equal(seccionActiva('operacion', 'OPERACION_DIARIA'), 'operacion');
  assert.equal(seccionActiva('simulacion', 'PERIODO_5_DIAS'), 'simulacion5d');
  assert.equal(seccionActiva('simulacion', 'COLAPSO'), 'colapso');
  assert.equal(seccionActiva('pedidos', 'PERIODO_5_DIAS'), 'pedidos');
});
