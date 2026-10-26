import test from 'node:test';
import assert from 'node:assert/strict';
import { estadoDeStock, inventarioEn } from './stock.js';

test('el stock de 08:33 no anticipa el corte publicado de 08:45', () => {
  const inicial = { reloj: '2026-09-01T08:30:00', almacenes: [{ id: 'I', disponible: 1000 }] };
  const posterior = { reloj: '2026-09-01T08:45:00', almacenes: [{ id: 'I', disponible: 900 }] };
  const fotogramas = [{ inventario: inicial }, { inventario: posterior }];
  assert.equal(inventarioEn(fotogramas, posterior, '2026-09-01T08:33:00'), inicial);
  assert.equal(inventarioEn(fotogramas, posterior, '2026-09-01T08:45:00'), posterior);
  assert.equal(inventarioEn(fotogramas, posterior, '2026-09-01T08:20:00'), null);
  assert.equal(inventarioEn([], posterior, '2026-09-01T08:33:00'), null);
});

test('el indicador usa los cortes ya presentes en la barra de inventario', () => {
  assert.equal(estadoDeStock({ disponible: 19 }, 100), 'critico');
  assert.equal(estadoDeStock({ disponible: 20 }, 100), 'advertencia');
  assert.equal(estadoDeStock({ disponible: 59 }, 100), 'advertencia');
  assert.equal(estadoDeStock({ disponible: 60 }, 100), 'suficiente');
});

test('vacío, ilimitado y stock desconocido no se confunden', () => {
  assert.equal(estadoDeStock({ disponible: 0 }, 100), 'vacio');
  assert.equal(estadoDeStock({ ilimitado: true, disponible: null }, 0), 'suficiente');
  assert.equal(estadoDeStock(null, 100), 'sin-datos');
  assert.equal(estadoDeStock({ disponible: 10 }, 0), 'sin-datos');
});
