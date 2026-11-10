import test from 'node:test';
import assert from 'node:assert/strict';
import { bloqueosEn } from './bloqueos.js';

test('el bloqueo cruza medianoche con inicio inclusivo y fin exclusivo', () => {
  const b = [{ inicio: '2026-09-01T23:59:00', fin: '2026-09-02T00:01:00' }];
  assert.equal(bloqueosEn(b, '2026-09-01T23:58:59').length, 0);
  assert.equal(bloqueosEn(b, '2026-09-01T23:59').length, 1);
  assert.equal(bloqueosEn(b, '2026-09-02T00:00:59').length, 1);
  assert.equal(bloqueosEn(b, '2026-09-02T00:01:00').length, 0);
  assert.equal(bloqueosEn(b, null).length, 0);
});

test('bordes de vigencia: solo se dibujan los bloqueos activos en el instante (D04)', () => {
  const b = [
    { inicio: '2029-01-15T08:00:00', fin: '2029-01-15T10:00:00' },
    { inicio: '2029-01-15T10:00:00', fin: '2029-01-15T12:00:00' },
    { inicio: '2029-01-15T09:30:00', fin: '2029-01-15T09:30:00' }
  ];
  // En el relevo exacto termina uno y empieza el otro: nunca se muestran los dos.
  assert.deepEqual(bloqueosEn(b, '2029-01-15T10:00:00'), [b[1]]);
  assert.deepEqual(bloqueosEn(b, '2029-01-15T09:59:59'), [b[0]]);
  assert.deepEqual(bloqueosEn(b, '2029-01-15T07:59:59'), []);
  assert.deepEqual(bloqueosEn(b, '2029-01-15T12:00:00'), []);
  // Un bloqueo de duración cero nunca está vigente.
  assert.deepEqual(bloqueosEn(b, '2029-01-15T09:30:00'), [b[0]]);
  assert.deepEqual(bloqueosEn(null, '2029-01-15T09:30:00'), []);
  assert.deepEqual(bloqueosEn(b, 'no-es-fecha'), []);
});
