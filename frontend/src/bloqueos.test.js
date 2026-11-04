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
