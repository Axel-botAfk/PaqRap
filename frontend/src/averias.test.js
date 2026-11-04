import test from 'node:test';
import assert from 'node:assert/strict';
import { averiasEn, averiaDeGrupo, PERMANENCIA_HORAS } from './averias.js';

const averia = (unidadId, tipo, instante) => ({ unidadId, tipo, instante, estado: 'APLICADA' });

test('una avería rechazada o aún registrada no genera marcador de incidencia aplicada', () => {
  const a = averia('TA01', 'TIPO_2', '2026-09-01T08:00:00');
  assert.equal(averiasEn([{ ...a, estado: 'NO_APLICADA' }], '2026-09-01T09:00:00').size, 0);
  assert.equal(averiasEn([{ ...a, estado: 'REGISTRADA' }], '2026-09-01T09:00:00').size, 0);
});

test('el tipo 1 permanece dos horas en el lugar y los tipos 2 y 3 cuatro', () => {
  assert.equal(PERMANENCIA_HORAS.TIPO_1, 2);
  assert.equal(PERMANENCIA_HORAS.TIPO_2, 4);
  assert.equal(PERMANENCIA_HORAS.TIPO_3, 4);
});

test('una avería no se muestra antes de su instante', () => {
  const vigentes = averiasEn([averia('TA06', 'TIPO_1', '2029-01-15T10:00:00')], '2029-01-15T09:59:00');
  assert.equal(vigentes.size, 0);
});

test('dentro de la permanencia la avería está en sitio', () => {
  const vigentes = averiasEn([averia('TA06', 'TIPO_1', '2029-01-15T10:00:00')], '2029-01-15T11:59:00');
  assert.equal(vigentes.get('TA06').fase, 'en-sitio');
  assert.equal(vigentes.get('TA06').tipo, 'TIPO_1');
});

test('al cumplirse la permanencia la unidad pasa a fuera de servicio', () => {
  const vigentes = averiasEn([averia('TA06', 'TIPO_1', '2029-01-15T10:00:00')], '2029-01-15T12:00:00');
  assert.equal(vigentes.get('TA06').fase, 'fuera');
});

test('el tipo 2 sigue en el lugar a las tres horas, cuando el tipo 1 ya salió', () => {
  const instante = '2029-01-15T13:00:00';
  const vigentes = averiasEn([
    averia('TA06', 'TIPO_1', '2029-01-15T10:00:00'),
    averia('TA10', 'TIPO_2', '2029-01-15T10:00:00')
  ], instante);
  assert.equal(vigentes.get('TA06').fase, 'fuera');
  assert.equal(vigentes.get('TA10').fase, 'en-sitio');
});

test('de dos averías de la misma unidad manda la más reciente', () => {
  const vigentes = averiasEn([
    averia('TA06', 'TIPO_1', '2029-01-15T08:00:00'),
    averia('TA06', 'TIPO_3', '2029-01-15T10:00:00')
  ], '2029-01-15T11:00:00');
  assert.equal(vigentes.get('TA06').tipo, 'TIPO_3');
});

test('sin instante válido no se afirma ninguna avería', () => {
  assert.equal(averiasEn([averia('TA06', 'TIPO_1', '2029-01-15T10:00:00')], null).size, 0);
  assert.equal(averiasEn(null, '2029-01-15T10:00:00').size, 0);
});

test('un tipo desconocido se ignora en vez de dibujarse sin regla', () => {
  const vigentes = averiasEn([averia('TA06', 'TIPO_9', '2029-01-15T10:00:00')], '2029-01-15T11:00:00');
  assert.equal(vigentes.size, 0);
});

test('el grupo toma la avería en sitio de cualquiera de sus unidades', () => {
  const vigentes = averiasEn([averia('TA10', 'TIPO_2', '2029-01-15T10:00:00')], '2029-01-15T11:00:00');
  assert.equal(averiaDeGrupo([{ id: 'TA06' }, { id: 'TA10' }], vigentes).tipo, 'TIPO_2');
  assert.equal(averiaDeGrupo([{ id: 'TA06' }], vigentes), null);
});
