import test from 'node:test';
import assert from 'node:assert/strict';
import { comprobaciones, hayErrores, aRegistro, parsearVentas, periodoDelNombre,
  diasDelMes, seguimientoDePedido } from './pedidos.js';

const aMs = valor => new Date(valor).getTime();

const valido = {
  cliente: 'c4910', x: '56', y: '30', cantidad: '2', plazo: '36',
  recepcion: '2026-01-01T01:30'
};

test('un borrador correcto no deja errores', () => {
  const lista = comprobaciones(valido);
  assert.equal(hayErrores(lista), false);
  assert.equal(lista.filter(c => c.nivel === 'ok').length, 5);
});

test('el nodo fuera de la retícula bloquea el registro', () => {
  assert.equal(hayErrores(comprobaciones({ ...valido, x: '71' })), true);
  assert.equal(hayErrores(comprobaciones({ ...valido, y: '51' })), true);
  assert.equal(hayErrores(comprobaciones({ ...valido, x: '70', y: '50' })), false);
});

test('la cantidad debe ser un entero mayor que cero', () => {
  assert.equal(hayErrores(comprobaciones({ ...valido, cantidad: '0' })), true);
  assert.equal(hayErrores(comprobaciones({ ...valido, cantidad: '-3' })), true);
  assert.equal(hayErrores(comprobaciones({ ...valido, cantidad: '2.5' })), true);
});

test('pasar de 24 unidades avisa del fraccionamiento, pero no bloquea', () => {
  const lista = comprobaciones({ ...valido, cantidad: '30' });
  assert.equal(hayErrores(lista), false);
  assert.equal(lista.find(c => c.clave === 'cantidad').nivel, 'aviso');
});

test('solo se admiten los plazos del caso', () => {
  for (const plazo of ['4', '8', '12', '18', '36']) {
    assert.equal(hayErrores(comprobaciones({ ...valido, plazo })), false);
  }
  assert.equal(hayErrores(comprobaciones({ ...valido, plazo: '24' })), true);
});

test('un cliente que no sigue la forma cNNNN avisa sin bloquear', () => {
  const lista = comprobaciones({ ...valido, cliente: 'Panadería Luz' });
  assert.equal(hayErrores(lista), false);
  assert.equal(lista.find(c => c.clave === 'cliente').nivel, 'aviso');
});

test('el borrador se escribe en el formato del archivo mensual', () => {
  assert.equal(aRegistro(valido), '01d01h30m:56,30,c4910,2,36');
});

test('diasDelMes respeta los meses cortos y los bisiestos', () => {
  assert.equal(diasDelMes('202602'), 28);
  assert.equal(diasDelMes('202402'), 29);
  assert.equal(diasDelMes('202601'), 31);
  assert.equal(diasDelMes('202604'), 30);
});

test('el periodo sale del nombre del archivo', () => {
  assert.equal(periodoDelNombre('ventas.202601.txt'), '202601');
  assert.equal(periodoDelNombre('ventas202610'), '202610');
  assert.equal(periodoDelNombre('cualquiera.txt'), '');
});

test('lee las líneas del archivo de ventas del curso', () => {
  const { pedidos, errores, leidas } = parsearVentas([
    '01d01h30m:56,30,c4910,02,36',
    '01d04h55m:21,29,c9599,06,18'
  ].join('\n'), '202601');
  assert.equal(leidas, 2);
  assert.deepEqual(errores, []);
  assert.equal(pedidos.length, 2);
  assert.equal(pedidos[0].id, 'P-00001');
  assert.deepEqual(pedidos[0].destino, { x: 56, y: 30 });
  assert.equal(pedidos[0].recepcion, '2026-01-01T01:30');
  assert.equal(pedidos[1].id, 'P-00002');
});

test('salta vacías y comentarios sin gastar correlativo', () => {
  const { pedidos, leidas } = parsearVentas([
    '# cabecera', '', '01d01h30m:56,30,c4910,02,36'
  ].join('\n'), '202601');
  assert.equal(leidas, 1);
  assert.equal(pedidos[0].id, 'P-00001');
});

test('reúne todos los errores en vez de cortar en el primero', () => {
  const { pedidos, errores } = parsearVentas([
    'esto no es un registro',
    '32d01h30m:56,30,c4910,02,36',
    '01d25h30m:56,30,c4910,02,36',
    '01d01h30m:71,30,c4910,02,36',
    '01d01h30m:56,30,c4910,0,36',
    '01d01h30m:56,30,c4910,02,36'
  ].join('\n'), '202601');
  assert.equal(errores.length, 5);
  assert.deepEqual(errores.map(e => e.numero), [1, 2, 3, 4, 5]);
  assert.equal(pedidos.length, 1);
  // El correlativo cuenta también las líneas malas, como haría el backend.
  assert.equal(pedidos[0].id, 'P-00006');
});

test('un plazo fuera del caso pasa, pero queda marcado', () => {
  const { pedidos, errores } = parsearVentas('01d01h30m:56,30,c4910,02,24', '202601');
  assert.deepEqual(errores, []);
  assert.match(pedidos[0].inusual, /24 h/);
});

test('el seguimiento junta asignaciones e hitos en orden', () => {
  const planes = [{
    reloj: '2026-01-01T08:00',
    rutas: [
      { id: 'C-TA10', vehiculoId: 'TA10', tipoVehiculo: 'AUTO', almacenId: 'ALM-CENTRAL',
        origen: { x: 27, y: 14 }, distanciaKm: 31, costo: 248,
        paradas: [{ id: 'P-00003', cantidad: 9 }] },
      { id: 'C-TB06', vehiculoId: 'TB06', paradas: [{ id: 'P-00001' }] }
    ],
    trayectos: [
      { vehiculoId: 'TA10', pasos: [
        { hito: 'SALIDA', instante: '2026-01-01T08:00', ubicacion: { x: 27, y: 14 }, pedidoId: null },
        { hito: 'ENTREGA', instante: '2026-01-01T09:30', ubicacion: { x: 61, y: 21 }, pedidoId: 'P-00003' }
      ] },
      { vehiculoId: 'TB06', pasos: [
        { hito: 'SALIDA', instante: '2026-01-01T08:00', ubicacion: { x: 27, y: 14 }, pedidoId: null }
      ] }
    ]
  }];

  const { asignaciones, hitos } = seguimientoDePedido(planes, 'P-00003', aMs);
  assert.equal(asignaciones.length, 1);
  assert.equal(asignaciones[0].vehiculoId, 'TA10');
  assert.equal(asignaciones[0].costo, 248);
  // La salida de su unidad entra; la de la otra unidad, no.
  assert.deepEqual(hitos.map(h => h.hito), ['SALIDA', 'ENTREGA']);
  assert.equal(hitos.every(h => h.vehiculoId === 'TA10'), true);
  assert.equal(hitos[0].delPedido, false);
  assert.equal(hitos[1].delPedido, true);
});

test('un pedido sin plan conocido devuelve un seguimiento vacío', () => {
  assert.deepEqual(seguimientoDePedido([], 'P-00009', aMs), { asignaciones: [], hitos: [] });
  assert.deepEqual(seguimientoDePedido(null, 'P-00009', aMs), { asignaciones: [], hitos: [] });
});

test('los campos vacíos no se leen como el nodo (0, 0)', () => {
  const lista = comprobaciones({ cliente: 'c1', x: '', y: '', cantidad: '', plazo: '36', recepcion: '' });
  assert.equal(lista.find(c => c.clave === 'nodo').nivel, 'error');
  assert.match(lista.find(c => c.clave === 'nodo').texto, /Falta el nodo/);
  assert.match(lista.find(c => c.clave === 'cantidad').texto, /Falta la cantidad/);
});

test('el nodo (0, 0) sigue siendo válido si se escribe a propósito', () => {
  const lista = comprobaciones({ ...valido, x: '0', y: '0' });
  assert.equal(lista.find(c => c.clave === 'nodo').nivel, 'ok');
});

test('solo la primera salida es del almacén; las siguientes retoman la ruta', () => {
  const plan = reloj => ({
    reloj,
    rutas: [{ id: 'C-TA10', vehiculoId: 'TA10', paradas: [{ id: 'P-00003' }] }],
    trayectos: [{ vehiculoId: 'TA10', pasos: [
      { hito: 'SALIDA', instante: reloj, ubicacion: { x: 27, y: 14 }, pedidoId: null }
    ] }]
  });
  const { hitos } = seguimientoDePedido(
    [plan('2026-01-01T09:00'), plan('2026-01-01T09:15'), plan('2026-01-01T09:30')], 'P-00003', aMs);
  assert.equal(hitos.length, 3);
  assert.deepEqual(hitos.map(h => h.retoma), [false, true, true]);
});
