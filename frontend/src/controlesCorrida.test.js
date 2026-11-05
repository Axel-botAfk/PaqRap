import test from 'node:test';
import assert from 'node:assert/strict';
import { controlesDeCorrida } from './controlesCorrida.js';
import { api } from './services/api.js';

test('la espera permite detener, ejecución permite pausar y pausa permite reanudar', () => {
  assert.deepEqual(controlesDeCorrida('PENDIENTE'), { activa: true, pausar: false, reanudar: false, cancelar: true });
  assert.deepEqual(controlesDeCorrida('EN_CURSO'), { activa: true, pausar: true, reanudar: false, cancelar: true });
  assert.deepEqual(controlesDeCorrida('PAUSADA'), { activa: true, pausar: false, reanudar: true, cancelar: true });
});

test('una corrida finalizada o ausente no ofrece acciones sobre el motor', () => {
  for (const estado of [undefined, 'TERMINADA', 'COLAPSADA', 'FALLIDA', 'CANCELADA']) {
    assert.deepEqual(controlesDeCorrida(estado), { activa: false, pausar: false, reanudar: false, cancelar: false });
  }
});

test('los tres controles usan POST sobre la misma corrida y propagan rechazos', async t => {
  const peticiones = [];
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    peticiones.push([url, opciones.method]);
    return { ok: true, json: async () => ({ id: 'corrida', estado: 'PAUSADA' }) };
  });
  await api.pausar('corrida'); await api.reanudar('corrida'); await api.cancelar('corrida');
  assert.deepEqual(peticiones, [
    ['/api/ejecuciones/corrida/pausar', 'POST'],
    ['/api/ejecuciones/corrida/reanudar', 'POST'],
    ['/api/ejecuciones/corrida/cancelar', 'POST']
  ]);
  t.mock.method(globalThis, 'fetch', async () => ({ ok: false, status: 409,
    json: async () => ({ mensaje: 'La corrida ya finalizó.' }) }));
  await assert.rejects(api.reanudar('corrida'), /La corrida ya finalizó/);
});
