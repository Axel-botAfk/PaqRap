import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TIEMPO_REAL_DIA_A_DIA, VELOCIDAD_DIA_A_DIA, velocidadDiaADia,
  ritmoConfigurado, DURACION_5_DIAS_MIN } from './formato.js';

// El interruptor de tiempo real decide qué velocidad se le pide al backend para el día a día.
// Las pruebas no fijan su valor —se cambia para hacer rollback— sino la coherencia entre el
// interruptor, la velocidad enviada y lo que la pantalla anuncia.

test('en tiempo real el día a día pide 1x; si no, la velocidad acelerada', () => {
  assert.equal(velocidadDiaADia(), TIEMPO_REAL_DIA_A_DIA ? 1 : VELOCIDAD_DIA_A_DIA);
});

test('la velocidad pedida es una de las que admite el backend', () => {
  assert.ok([1, 10, 60, 240].includes(velocidadDiaADia()),
    `RitmoEscenario.VELOCIDADES no admite ${velocidadDiaADia()}`);
});

test('el texto del ritmo del día a día corresponde al interruptor', () => {
  const texto = ritmoConfigurado('OPERACION_DIARIA');
  if (TIEMPO_REAL_DIA_A_DIA) {
    assert.match(texto, /tiempo real/);
    assert.doesNotMatch(texto, /se reproduce/,
      'en tiempo real no se reproduce una jornada: se sigue la de hoy');
  } else {
    assert.match(texto, /se reproduce/);
  }
});

test('los otros dos escenarios no dependen del interruptor', () => {
  assert.match(ritmoConfigurado('PERIODO_5_DIAS'), new RegExp(`${DURACION_5_DIAS_MIN} min`));
  assert.match(ritmoConfigurado('COLAPSO'), /pedido vencido/);
});
