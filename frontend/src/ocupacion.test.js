import test from 'node:test';
import assert from 'node:assert/strict';
import { ocupacionDeAlmacen, COLORES_OCUPACION, ETIQUETAS_OCUPACION, NIVELES_OCUPACION } from './ocupacion.js';

const umbrales = { critico: 20, advertencia: 60 };
const nivel = (disponible, capacidad = 100) => ocupacionDeAlmacen({ disponible, capacidad }, undefined, umbrales).nivel;

test('vacío, bajo, intermedio y alto siguen el porcentaje de stock sobre la capacidad', () => {
  assert.equal(nivel(0), 'vacio');
  assert.equal(nivel(1), 'bajo');
  assert.equal(nivel(19), 'bajo');
  assert.equal(nivel(20), 'intermedio');
  assert.equal(nivel(59), 'intermedio');
  assert.equal(nivel(60), 'alto');
  assert.equal(nivel(100), 'alto');
});

test('devuelve el porcentaje acotado a 0–100', () => {
  assert.equal(ocupacionDeAlmacen({ disponible: 45, capacidad: 100 }, undefined, umbrales).porcentaje, 45);
  assert.equal(ocupacionDeAlmacen({ disponible: 150, capacidad: 100 }, undefined, umbrales).porcentaje, 100);
  assert.equal(ocupacionDeAlmacen({ disponible: 0, capacidad: 100 }, undefined, umbrales).porcentaje, 0);
});

test('sin capacidad publicada usa el stock inicial como referencia', () => {
  assert.equal(ocupacionDeAlmacen({ disponible: 30 }, 100, umbrales).nivel, 'intermedio');
  assert.equal(ocupacionDeAlmacen({ disponible: 30 }, undefined, umbrales).nivel, 'sin-datos');
  assert.equal(ocupacionDeAlmacen({ disponible: 30 }, 0, umbrales).nivel, 'sin-datos');
});

test('el almacén ilimitado y el que no tiene corte no reciben nivel de color', () => {
  assert.deepEqual(ocupacionDeAlmacen({ ilimitado: true }, 0, umbrales), { nivel: 'ilimitado', porcentaje: null });
  for (const stock of [null, undefined, {}, { disponible: -1 }, { disponible: NaN }]) {
    assert.deepEqual(ocupacionDeAlmacen(stock, 100, umbrales), { nivel: 'sin-datos', porcentaje: null });
  }
});

test('respeta los umbrales configurados', () => {
  const estrictos = { critico: 40, advertencia: 80 };
  assert.equal(ocupacionDeAlmacen({ disponible: 30, capacidad: 100 }, undefined, estrictos).nivel, 'bajo');
  assert.equal(ocupacionDeAlmacen({ disponible: 70, capacidad: 100 }, undefined, estrictos).nivel, 'intermedio');
  assert.equal(ocupacionDeAlmacen({ disponible: 85, capacidad: 100 }, undefined, estrictos).nivel, 'alto');
});

test('cada nivel pintable tiene colores y etiqueta, y el vacío es blanco', () => {
  for (const n of NIVELES_OCUPACION) {
    assert.ok(COLORES_OCUPACION[n].relleno && COLORES_OCUPACION[n].contorno && COLORES_OCUPACION[n].detalle, n);
    assert.ok(ETIQUETAS_OCUPACION[n], n);
  }
  assert.equal(COLORES_OCUPACION.vacio.relleno, '#FFFFFF');
  assert.equal(new Set(NIVELES_OCUPACION.map(n => COLORES_OCUPACION[n].relleno)).size, 4);
});
