import test from 'node:test';
import assert from 'node:assert/strict';
import { filasDeUnidades, ordenarUnidades, filtrarUnidades } from './unidades.js';

const flota = [
  { id: 'TA02', tipo: 'MOTO', estado: 'EN_RUTA', capacidad: 8, cargaAbordo: 4, ubicacion: { x: 1, y: 1 } },
  { id: 'TA01', tipo: 'AUTO', estado: 'EN_RUTA', capacidad: 10, cargaAbordo: 9, ubicacion: { x: 2, y: 2 } },
  { id: 'TA10', tipo: 'AUTO', estado: 'DISPONIBLE', capacidad: 10, cargaAbordo: 0, ubicacion: { x: 3, y: 3 } }
];
const rutas = [
  { id: 'R-1', vehiculoId: 'TA01', paradas: [{ id: 'P-1', cantidad: 5 }, { id: 'P-2', cantidad: 4 }] },
  { id: 'R-2', vehiculoId: 'TA02', paradas: [{ id: 'P-3', cantidad: 4 }] }
];

test('una unidad con dos viajes conserva envíos de ambos y horarios del plan', () => {
  const r = [...rutas, { id: 'R-3', vehiculoId: 'TA01',
    salidaPrevista: '2026-09-01T11:00:00', llegadasPrevistas: { 'P-9': '2026-09-01T12:00:00' },
    paradas: [{ id: 'P-9', cantidad: 2 }] }];
  const fila = filasDeUnidades(flota, r, [], '2026-09-01T10:00:00').find(v => v.id === 'TA01');
  assert.equal(fila.pedidos.length, 3);
  assert.equal(fila.productos, 11);
  assert.equal(fila.minutosAlDestino, 120);
  assert.equal(fila.minutosDesdeSalida, null);
});

test('cada fila trae ocupación en número y en porcentaje', () => {
  const filas = filasDeUnidades(flota, rutas);
  const ta01 = filas.find(f => f.id === 'TA01');
  assert.equal(ta01.cargaAbordo, 9);
  assert.equal(ta01.capacidad, 10);
  assert.equal(ta01.porcentaje, 90);
  assert.equal(ta01.nivel, 'alta');
});

test('la fila enlaza los pedidos y suma sus productos', () => {
  const ta01 = filasDeUnidades(flota, rutas).find(f => f.id === 'TA01');
  assert.equal(ta01.ruta, 'R-1');
  assert.deepEqual(ta01.pedidos.map(p => p.id), ['P-1', 'P-2']);
  assert.equal(ta01.productos, 9);
});

test('una unidad sin ruta asignada no inventa pedidos', () => {
  const ta10 = filasDeUnidades(flota, rutas).find(f => f.id === 'TA10');
  assert.equal(ta10.ruta, null);
  assert.deepEqual(ta10.pedidos, []);
  assert.equal(ta10.productos, 0);
  assert.equal(ta10.nivel, 'vacio');
});

test('sin capacidad publicada la ocupación queda en nulo', () => {
  const filas = filasDeUnidades([{ id: 'TA99', tipo: 'AUTO', capacidad: 0, cargaAbordo: 0 }], []);
  assert.equal(filas[0].porcentaje, null);
  assert.equal(filas[0].cargaAbordo, null);
});

test('ordena por ocupación descendente y ascendente', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'ocupacion-desc').map(f => f.id), ['TA01', 'TA02', 'TA10']);
  assert.deepEqual(ordenarUnidades(filas, 'ocupacion-asc').map(f => f.id), ['TA10', 'TA02', 'TA01']);
});

test('ordena por unidad con criterio numérico', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'id').map(f => f.id), ['TA01', 'TA02', 'TA10']);
});

test('ordena por cantidad de pedidos', () => {
  const filas = filasDeUnidades(flota, rutas);
  assert.deepEqual(ordenarUnidades(filas, 'pedidos-desc').map(f => f.id), ['TA01', 'TA02', 'TA10']);
});

test('las unidades sin ocupación medible quedan al final', () => {
  const filas = filasDeUnidades([...flota, { id: 'TA99', tipo: 'AUTO', capacidad: 0, cargaAbordo: 0 }], rutas);
  assert.equal(ordenarUnidades(filas, 'ocupacion-asc').at(-1).id, 'TA99');
  assert.equal(ordenarUnidades(filas, 'ocupacion-desc').at(-1).id, 'TA99');
});

test('ordenar no muta la lista original', () => {
  const filas = filasDeUnidades(flota, rutas);
  const antes = filas.map(f => f.id);
  ordenarUnidades(filas, 'ocupacion-desc');
  assert.deepEqual(filas.map(f => f.id), antes);
});

const trayectos = [
  { vehiculoId: 'TA01', pasos: [
    { instante: '2029-01-15T10:00:00', hito: 'SALIDA' },
    { instante: '2029-01-15T10:30:00', hito: 'ENTREGA', pedidoId: 'P-1' },
    { instante: '2029-01-15T11:15:00', hito: 'ENTREGA', pedidoId: 'P-2' }
  ] },
  { vehiculoId: 'TA02', pasos: [
    { instante: '2029-01-15T09:40:00', hito: 'SALIDA' },
    { instante: '2029-01-15T10:50:00', hito: 'ENTREGA', pedidoId: 'P-3' }
  ] }
];
const INSTANTE = '2029-01-15T10:40:00';

test('la salida es el hito SALIDA y la llegada, la próxima entrega', () => {
  const ta01 = filasDeUnidades(flota, rutas, trayectos, INSTANTE).find(f => f.id === 'TA01');
  assert.equal(ta01.salida, '2029-01-15T10:00:00');
  assert.equal(ta01.llegada, '2029-01-15T11:15:00');
});

test('los minutos al destino miran hacia adelante y los de salida hacia atrás', () => {
  const ta01 = filasDeUnidades(flota, rutas, trayectos, INSTANTE).find(f => f.id === 'TA01');
  assert.equal(ta01.minutosAlDestino, 35);
  assert.equal(ta01.minutosDesdeSalida, 40);
});

test('sin entregas por delante no se inventan minutos para llegar', () => {
  const tarde = filasDeUnidades(flota, rutas, trayectos, '2029-01-15T12:00:00').find(f => f.id === 'TA01');
  assert.equal(tarde.minutosAlDestino, null);
  assert.equal(tarde.llegada, '2029-01-15T11:15:00');
});

test('una unidad sin trayecto publicado no declara horarios', () => {
  const ta10 = filasDeUnidades(flota, rutas, trayectos, INSTANTE).find(f => f.id === 'TA10');
  assert.equal(ta10.salida, null);
  assert.equal(ta10.llegada, null);
  assert.equal(ta10.minutosAlDestino, null);
});

test('ordena por hora de salida y por hora de llegada', () => {
  const filas = filasDeUnidades(flota, rutas, trayectos, INSTANTE);
  assert.deepEqual(ordenarUnidades(filas, 'salida-asc').slice(0, 2).map(f => f.id), ['TA02', 'TA01']);
  assert.deepEqual(ordenarUnidades(filas, 'salida-desc').slice(0, 2).map(f => f.id), ['TA01', 'TA02']);
  assert.deepEqual(ordenarUnidades(filas, 'llegada-asc').slice(0, 2).map(f => f.id), ['TA02', 'TA01']);
});

test('las unidades sin horario quedan al final en los dos sentidos', () => {
  const filas = filasDeUnidades(flota, rutas, trayectos, INSTANTE);
  assert.equal(ordenarUnidades(filas, 'salida-asc').at(-1).id, 'TA10');
  assert.equal(ordenarUnidades(filas, 'salida-desc').at(-1).id, 'TA10');
});

test('busca por minutos al destino inmediato', () => {
  const filas = filasDeUnidades(flota, rutas, trayectos, INSTANTE);
  assert.deepEqual(filtrarUnidades(filas, { minutosAlDestino: 15 }).map(f => f.id), ['TA02']);
  assert.deepEqual(filtrarUnidades(filas, { minutosAlDestino: 40 }).map(f => f.id).sort(), ['TA01', 'TA02']);
});

test('busca por minutos desde la salida, sin colar unidades sin dato', () => {
  const filas = filasDeUnidades(flota, rutas, trayectos, INSTANTE);
  // TA02 salió 60 min antes del instante, así que el límite de 45 min la deja fuera.
  assert.deepEqual(filtrarUnidades(filas, { minutosDesdeSalida: 45 }).map(f => f.id), ['TA01']);
  assert.deepEqual(filtrarUnidades(filas, { minutosDesdeSalida: 60 }).map(f => f.id), ['TA02', 'TA01']);
  assert.ok(!filtrarUnidades(filas, { minutosDesdeSalida: 1000 }).some(f => f.id === 'TA10'));
});

test('sin criterios de búsqueda la lista no se recorta', () => {
  const filas = filasDeUnidades(flota, rutas, trayectos, INSTANTE);
  assert.equal(filtrarUnidades(filas, {}).length, filas.length);
  assert.equal(filtrarUnidades(filas).length, filas.length);
});

test('la búsqueda al origen utiliza los minutos publicados y no el tiempo desde salida', () => {
  const filas = filasDeUnidades([{ ...flota[0], origenId: 'A', minutosAlOrigen: 12 }, flota[1]], []);
  assert.deepEqual(filtrarUnidades(filas, { minutosAlOrigen: 12 }).map(v => v.id), ['TA02']);
  assert.equal(filtrarUnidades(filas, { minutosAlOrigen: 11 }).length, 0);
});

test('distancias en km: al destino inmediato, al origen y recorridas (E08, E09, E11, E16, E17)', () => {
  const vehiculos = [
    { id: 'TA01', tipo: 'AUTO', estado: 'EN_RUTA', ubicacion: { x: 10, y: 10 }, capacidad: 24,
      cargaAbordo: 12, kmAlOrigen: 21.5, kmRecorridos: 30 },
    { id: 'TA02', tipo: 'AUTO', estado: 'EN_RUTA', ubicacion: { x: 0, y: 0 }, capacidad: 24,
      cargaAbordo: 6, kmAlOrigen: 4, kmRecorridos: 2 },
    { id: 'TA03', tipo: 'AUTO', estado: 'DISPONIBLE', ubicacion: { x: 5, y: 5 }, capacidad: 24, cargaAbordo: 0 }
  ];
  const ruta = (id, vehiculoId, x, y) => ({ id, vehiculoId, salidaPrevista: '2029-01-15T08:00:00',
    llegadasPrevistas: { [`P-${id}`]: '2029-01-15T10:00:00' },
    paradas: [{ id: `P-${id}`, cantidad: 6, destino: { x, y } }] });
  const filas = filasDeUnidades(vehiculos, [ruta('R1', 'TA01', 14, 13), ruta('R2', 'TA02', 20, 5)],
    null, '2029-01-15T09:00:00');
  const porId = Object.fromEntries(filas.map(f => [f.id, f]));
  assert.equal(porId.TA01.kmAlDestino, 7);
  assert.equal(porId.TA02.kmAlDestino, 25);
  assert.equal(porId.TA03.kmAlDestino, null);
  assert.equal(porId.TA01.kmAlOrigen, 21.5);
  assert.equal(porId.TA03.kmRecorridos, null);

  assert.deepEqual(filtrarUnidades(filas, { kmAlDestino: 10 }).map(f => f.id), ['TA01']);
  assert.deepEqual(filtrarUnidades(filas, { kmAlOrigen: 5 }).map(f => f.id), ['TA02']);
  assert.deepEqual(filtrarUnidades(filas, { rangoKm: '20-40' }).map(f => f.id), ['TA02']);
  assert.deepEqual(filtrarUnidades(filas, { rangoKm: '0-10' }).map(f => f.id), ['TA01']);
  // Sin dato de distancia la unidad queda al final en los dos sentidos.
  assert.deepEqual(ordenarUnidades(filas, 'km-destino-asc').map(f => f.id), ['TA01', 'TA02', 'TA03']);
  assert.deepEqual(ordenarUnidades(filas, 'km-destino-desc').map(f => f.id), ['TA02', 'TA01', 'TA03']);
  assert.deepEqual(ordenarUnidades(filas, 'km-recorridos-desc').map(f => f.id), ['TA01', 'TA02', 'TA03']);
});
