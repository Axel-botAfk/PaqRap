import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizarUmbrales, UMBRALES_POR_OMISION } from './umbrales.js';

test('los umbrales de omisión son los cortes que ya usaba el código', () => {
  assert.deepEqual(UMBRALES_POR_OMISION.carga, { baja: 40, intermedia: 70 });
  assert.deepEqual(UMBRALES_POR_OMISION.stock, { critico: 20, advertencia: 60 });
});

test('acepta un par válido', () => {
  const valores = normalizarUmbrales({ carga: { baja: 25, intermedia: 80 }, stock: { critico: 10, advertencia: 50 } });
  assert.deepEqual(valores.carga, { baja: 25, intermedia: 80 });
  assert.deepEqual(valores.stock, { critico: 10, advertencia: 50 });
});

test('un corte invertido vuelve al de omisión en vez de romper el semáforo', () => {
  const valores = normalizarUmbrales({ carga: { baja: 80, intermedia: 20 } });
  assert.deepEqual(valores.carga, UMBRALES_POR_OMISION.carga);
});

test('cortes iguales tampoco valen: no dejarían banda intermedia', () => {
  assert.deepEqual(normalizarUmbrales({ carga: { baja: 50, intermedia: 50 } }).carga,
    UMBRALES_POR_OMISION.carga);
});

test('fuera de 1 a 99 vuelve al de omisión', () => {
  assert.deepEqual(normalizarUmbrales({ stock: { critico: 0, advertencia: 60 } }).stock,
    UMBRALES_POR_OMISION.stock);
  assert.deepEqual(normalizarUmbrales({ stock: { critico: 20, advertencia: 100 } }).stock,
    UMBRALES_POR_OMISION.stock);
});

test('valores no enteros o ausentes vuelven al de omisión', () => {
  assert.deepEqual(normalizarUmbrales({ carga: { baja: 40.5, intermedia: 70 } }).carga,
    UMBRALES_POR_OMISION.carga);
  assert.deepEqual(normalizarUmbrales({ carga: { baja: 'ocho', intermedia: 70 } }).carga,
    UMBRALES_POR_OMISION.carga);
  assert.deepEqual(normalizarUmbrales(null), { ...UMBRALES_POR_OMISION });
});

test('un par válido no se contamina con el otro inválido', () => {
  const valores = normalizarUmbrales({ carga: { baja: 30, intermedia: 60 }, stock: { critico: 90, advertencia: 10 } });
  assert.deepEqual(valores.carga, { baja: 30, intermedia: 60 });
  assert.deepEqual(valores.stock, UMBRALES_POR_OMISION.stock);
});
