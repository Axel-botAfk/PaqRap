import test from 'node:test';
import assert from 'node:assert/strict';
import { enviosEn, ventanaDeEnvios, agruparOriginales, rutasDeEnvioEnMapa } from './envios.js';
const hora = h => `2026-09-01T${h}:00:00`;
const pedido = (id, cantidad) => ({ id, idOriginal: 'P', cantidad,
  fechaRegistro: hora('08'), destino: { x: 2, y: 3 } });
const plan = (id, unidad, pedido, llegada) => ({ reloj: hora('08'), indice: 0,
  rutas: [{ id, vehiculoId: unidad, almacenId: 'A', paradas: [pedido] }],
  trayectos: [{ vehiculoId: unidad, pasos: [
    { hito: 'SALIDA', instante: hora('09') },
    { hito: 'ENTREGA', pedidoId: pedido.id, instante: hora(llegada) }
  ] }] });

test('original repartido entre dos UT conserva ambas fracciones sin sumar reasignaciones', () => {
  const p = plan('R1', 'V1', pedido('P-a', 4), '10');
  const q = plan('R2', 'V2', pedido('P-b', 6), '11');
  const copia = { ...p, reloj: '2026-09-01T08:30:00', indice: 1 };
  const filas = enviosEn([p, q, copia], hora('09'));
  const grupos = agruparOriginales(filas);
  assert.equal(grupos.length, 1);
  assert.equal(grupos[0].productos, 10);
  assert.equal(grupos[0].fracciones.length, 2);
  assert.equal(ventanaDeEnvios(filas, hora('09'), 'transito').length, 2);
  assert.equal(ventanaDeEnvios(filas, hora('09'), 'entregados').length, 0);
});

test('ventanas no anticipan entregas y excluyen exactamente el límite de cuatro horas', () => {
  const p = plan('R', 'V', pedido('P', 2), '10');
  let filas = enviosEn([p], hora('10'));
  assert.equal(ventanaDeEnvios(filas, hora('10'), 'entregados').length, 1);
  assert.equal(ventanaDeEnvios(filas, hora('10'), 'transito').length, 0);
  filas = enviosEn([p], hora('14'));
  assert.equal(ventanaDeEnvios(filas, hora('14'), 'entregados').length, 0);
});

test('planes futuros no se incorporan y sin horario no se inventa una salida', () => {
  const p = plan('R', 'V', pedido('P', 2), '10');
  assert.deepEqual(enviosEn([p], hora('07')), []);
  const filas = enviosEn([{ ...p, trayectos: [] }], hora('09'));
  assert.equal(ventanaDeEnvios(filas, hora('09'), 'salidas').length, 0);
});

test('la ventana de próximas salidas incluye inicio y excluye su extremo final', () => {
  const filas = [{ salida: hora('09') }, { salida: hora('13') }];
  assert.equal(ventanaDeEnvios(filas, hora('09'), 'salidas').length, 1);
});

test('los horarios del plan permiten consultar próximas salidas sin fingir entregas realizadas', () => {
  const p = plan('R', 'V', pedido('P', 2), '10');
  p.trayectos = [];
  p.rutas[0].salidaPrevista = hora('09');
  p.rutas[0].llegadasPrevistas = { P: hora('10') };
  const filas = enviosEn([p], hora('08'));
  assert.equal(ventanaDeEnvios(filas, hora('08'), 'salidas').length, 1);
  assert.equal(ventanaDeEnvios(filas, hora('08'), 'proximos').length, 1);
  assert.equal(enviosEn([p], hora('11'))[0].entregaReal, false);
});

test('seleccionar una fracción resalta solo su ruta y cambiarla reemplaza el resaltado', () => {
  const primera = pedido('P-a', 4), segunda = pedido('P-b', 6);
  const filas = enviosEn([plan('R1', 'V1', primera, '10'),
    plan('R2', 'V2', segunda, '11')], hora('09'));
  const rutas = [
    { id: 'R1', vehiculoId: 'V1', almacenId: 'A', paradas: [primera] },
    { id: 'R2', vehiculoId: 'V2', almacenId: 'A', paradas: [segunda] }
  ];
  assert.deepEqual(rutasDeEnvioEnMapa(filas, { tipo: 'fraccion', id: 'P-a' }, rutas).rutas, ['R1']);
  assert.deepEqual(rutasDeEnvioEnMapa(filas, { tipo: 'fraccion', id: 'P-b' }, rutas).rutas, ['R2']);
  assert.deepEqual(rutasDeEnvioEnMapa(filas, null, rutas).rutas, []);
});

test('el original reúne rutas de todas sus fracciones y no duplica rutas compartidas', () => {
  const a = pedido('P-a', 4), b = pedido('P-b', 3), c = pedido('P-c', 3);
  const filas = [
    { ...a, original: 'P', ruta: 'R1', unidad: 'V1', almacen: 'A' },
    { ...b, original: 'P', ruta: 'R2', unidad: 'V2', almacen: 'B' },
    { ...c, original: 'P', ruta: 'R2', unidad: 'V2', almacen: 'B' }
  ];
  const rutas = [
    { id: 'R1', vehiculoId: 'V1', almacenId: 'A', paradas: [a] },
    { id: 'R2', vehiculoId: 'V2', almacenId: 'B', paradas: [b, c] }
  ];
  const resultado = rutasDeEnvioEnMapa(filas, { tipo: 'original', id: 'P' }, rutas);
  assert.equal(resultado.fracciones.length, 3);
  assert.deepEqual(resultado.rutas, ['R1', 'R2']);
  assert.deepEqual(resultado.sinRutaVisible, []);
});

test('un plan histórico o una fracción sin asignación no inventan trazos en el instante', () => {
  const fraccion = pedido('P-a', 4);
  const fila = { ...fraccion, original: 'P', ruta: 'R1', unidad: 'V1', almacen: 'A' };
  const otroPlan = [{ id: 'R2', vehiculoId: 'V1', almacenId: 'A', paradas: [fraccion] }];
  const resultado = rutasDeEnvioEnMapa([fila], { tipo: 'fraccion', id: 'P-a' }, otroPlan);
  assert.deepEqual(resultado.rutas, []);
  assert.deepEqual(resultado.sinRutaVisible, [fila]);
  assert.deepEqual(rutasDeEnvioEnMapa([], { tipo: 'original', id: 'P' }, otroPlan).rutas, []);
  assert.deepEqual(rutasDeEnvioEnMapa([{ ...fila, ruta: null }],
    { tipo: 'fraccion', id: 'P-a' }, otroPlan).rutas, []);
});

test('al avanzar y retroceder el historial se resalta solo el plan vigente', () => {
  const fraccion = pedido('P-a', 4);
  const primero = plan('R1', 'V1', fraccion, '12');
  const segundo = { ...plan('R2', 'V2', fraccion, '13'), reloj: hora('10'), indice: 1 };
  const seleccion = { tipo: 'fraccion', id: 'P-a' };
  const ruta1 = primero.rutas, ruta2 = segundo.rutas;
  const enNueve = enviosEn([primero, segundo], hora('09'));
  assert.deepEqual(rutasDeEnvioEnMapa(enNueve, seleccion, ruta1).rutas, ['R1']);
  assert.deepEqual(rutasDeEnvioEnMapa(enNueve, seleccion, ruta2).rutas, []);
  const enOnce = enviosEn([primero, segundo], hora('11'));
  assert.deepEqual(rutasDeEnvioEnMapa(enOnce, seleccion, ruta2).rutas, ['R2']);
  assert.deepEqual(rutasDeEnvioEnMapa(enviosEn([primero, segundo], hora('09')),
    seleccion, ruta1).rutas, ['R1']);
});
