import test from 'node:test';
import assert from 'node:assert/strict';
import { TOPE_CUADRO_MS, aIso, aMs, crearRelojAnclado, desfaseConServidor, instanteSimulado,
  transcurridoObjetivo } from './reloj.js';
import { posicionEnTrayecto, prepararTrayectos } from './mapa.js';

// Ventana de 1 h simulada que se reproduce en 20 s reales: K = 180.
const ritmo = (cambios = {}) => ({
  saMs: 20_000, k: 180, ventana: 3, publicadoMs: 1_000_000, servidorAhoraMs: 1_000_000,
  relojDesde: '2026-09-01T02:00:00', relojHasta: '2026-09-01T03:00:00',
  transcurridoAlPausarMs: null, ...cambios
});

test('el instante depende del tiempo real transcurrido, no de cuántos cuadros se dibujaron', () => {
  const pocos = crearRelojAnclado();
  const muchos = crearRelojAnclado();
  pocos(ritmo(), 1_000_000);
  for (let t = 1_000_000; t <= 1_010_000; t += 16) muchos(ritmo(), t);
  for (let t = 1_000_000; t <= 1_010_000; t += 200) pocos(ritmo(), t);
  const esperado = aMs('2026-09-01T02:30:00');
  assert.equal(muchos(ritmo(), 1_010_000), esperado);
  assert.equal(pocos(ritmo(), 1_010_000), esperado);
});

test('no pasa del corte de la ventana aunque pase más de Sa', () => {
  const reloj = crearRelojAnclado();
  reloj(ritmo(), 1_000_000);
  let instante;
  for (let t = 1_000_000; t <= 1_060_000; t += 100) instante = reloj(ritmo(), t);
  assert.equal(aIso(instante), '2026-09-01T03:00:00');
});

test('al despertar la pestaña avanza a lo sumo el tope por cuadro', () => {
  const reloj = crearRelojAnclado();
  const inicio = reloj(ritmo(), 1_000_000);
  const tras = reloj(ritmo(), 1_015_000); // 15 s sin cuadros
  assert.equal(tras - inicio, TOPE_CUADRO_MS * 180);
  let instante = tras;
  for (let i = 0; i < 100; i++) instante = reloj(ritmo(), 1_015_000);
  assert.equal(instante, aMs('2026-09-01T02:45:00'));
});

test('al recargar se ubica directamente donde va la corrida', () => {
  const reloj = crearRelojAnclado();
  assert.equal(reloj(ritmo(), 1_010_000), aMs('2026-09-01T02:30:00'));
});

test('una ventana nueva arranca desde su inicio', () => {
  const reloj = crearRelojAnclado();
  reloj(ritmo(), 1_019_000);
  const nueva = ritmo({ ventana: 4, publicadoMs: 1_020_000, relojDesde: '2026-09-01T03:00:00',
    relojHasta: '2026-09-01T04:00:00' });
  assert.equal(reloj(nueva, 1_020_000), aMs('2026-09-01T03:00:00'));
});

test('en pausa el reloj se congela en lo reproducido', () => {
  const reloj = crearRelojAnclado();
  const pausada = ritmo({ transcurridoAlPausarMs: 5_000 });
  assert.equal(reloj(pausada, 1_050_000), aMs('2026-09-01T02:15:00'));
  assert.equal(reloj(pausada, 1_090_000), aMs('2026-09-01T02:15:00'));
});

test('corrige el desfase entre el reloj del navegador y el del servidor', () => {
  const r = ritmo({ servidorAhoraMs: 1_004_000 });
  const desfase = desfaseConServidor(r, 5_004_000); // el navegador va 4 000 000 ms adelante
  assert.equal(transcurridoObjetivo(r, 5_004_000, desfase), 4_000);
  assert.equal(instanteSimulado(r, 4_000), aMs('2026-09-01T02:12:00'));
});

test('la unidad avanza por los hitos de su trayecto y espera en la entrega', () => {
  const preparado = prepararTrayectos([{ vehiculoId: 'A1', pasos: [
    { ubicacion: { x: 0, y: 0 }, instante: '2026-09-01T02:00:00', hito: 'SALIDA' },
    { ubicacion: { x: 4, y: 0 }, instante: '2026-09-01T02:04:00', hito: 'ENTREGA' },
    { ubicacion: { x: 4, y: 0 }, instante: '2026-09-01T03:04:00', hito: 'FIN_ENTREGA' },
    { ubicacion: { x: 4, y: 2 }, instante: '2026-09-01T03:06:00', hito: 'CORTE' }
  ] }], aMs).get('A1');
  assert.deepEqual(posicionEnTrayecto(preparado, aMs('2026-09-01T01:00:00')), { x: 0, y: 0 });
  assert.deepEqual(posicionEnTrayecto(preparado, aMs('2026-09-01T02:02:00')), { x: 2, y: 0 });
  assert.deepEqual(posicionEnTrayecto(preparado, aMs('2026-09-01T02:30:00')), { x: 4, y: 0 });
  assert.deepEqual(posicionEnTrayecto(preparado, aMs('2026-09-01T03:05:00')), { x: 4, y: 1 });
  assert.deepEqual(posicionEnTrayecto(preparado, aMs('2026-09-01T05:00:00')), { x: 4, y: 2 });
  assert.equal(posicionEnTrayecto(undefined, 0), null);
});
