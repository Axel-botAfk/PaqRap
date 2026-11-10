import test from 'node:test';
import assert from 'node:assert/strict';
import { ultimoPlanEstable, reporteDePlan, reporteCsv } from './reporte.js';

const pedido = (id, cantidad, limite, original = null) => ({ id, idOriginal: original,
  destino: { x: 10, y: 20 }, cantidad, fechaLimite: limite });

const planes = [
  { indice: 0, reloj: '2029-01-15T08:00:00', hasta: '2029-01-15T08:30:00', rutas: [], trayectos: [] },
  { indice: 1, reloj: '2029-01-15T09:00:00', hasta: '2029-01-15T09:30:00',
    rutas: [{ id: 'R1', vehiculoId: 'TA01', tipoVehiculo: 'AUTO', almacenId: 'ALM-CENTRAL',
      carga: 12, distanciaKm: 18.5, costo: 148, salidaPrevista: '2029-01-15T09:05:00',
      llegadasPrevistas: { 'P1': '2029-01-15T09:20:00', 'P2#2': '2029-01-15T10:40:00' },
      paradas: [pedido('P1', 8, '2029-01-15T12:00:00'), pedido('P2#2', 4, '2029-01-15T10:00:00', 'P2')] }],
    trayectos: [{ vehiculoId: 'TA01', pasos: [
      { hito: 'ENTREGA', pedidoId: 'P1', instante: '2029-01-15T09:21:00' },
      { hito: 'ENTREGA', pedidoId: 'P2#2', instante: '2029-01-15T09:50:00' }] }],
    noAsignados: [{ ...pedido('P9', 3, '2029-01-15T11:00:00'), motivo: 'SIN_RUTA_FACTIBLE' }] },
  { indice: 2, reloj: '2029-01-15T11:00:00', hasta: null, rutas: [], trayectos: [] }
];

test('el plan estable es el último publicado, o el último hasta el colapso', () => {
  assert.equal(ultimoPlanEstable(planes).indice, 2);
  assert.equal(ultimoPlanEstable(planes, '2029-01-15T10:30:00').indice, 1);
  assert.equal(ultimoPlanEstable(planes, '2029-01-15T09:00:00').indice, 1);
  assert.equal(ultimoPlanEstable(planes, '2029-01-15T07:00:00'), null);
  assert.equal(ultimoPlanEstable([], null), null);
  assert.equal(ultimoPlanEstable(null), null);
});

test('el reporte distingue entregas observadas de llegadas previstas y totaliza', () => {
  const reporte = reporteDePlan(planes[1]);
  const [p1, p2] = reporte.rutas[0].paradas;
  assert.equal(p1.entregado, true);
  assert.equal(p1.llegada, '2029-01-15T09:21:00');
  assert.equal(p1.enPlazo, true);
  // La entrega de P2#2 ocurre después del corte del plan: sigue siendo una previsión.
  assert.equal(p2.entregado, false);
  assert.equal(p2.llegada, '2029-01-15T10:40:00');
  assert.equal(p2.enPlazo, false);
  assert.equal(p2.original, 'P2');
  assert.deepEqual(reporte.totales, { rutas: 1, unidades: 1, pedidos: 2, productos: 12,
    entregados: 1, fueraDePlazo: 1, km: 18.5, costo: 148 });
  assert.equal(reporte.noAsignados[0].motivo, 'SIN_RUTA_FACTIBLE');
  assert.equal(reporteDePlan(null), null);
});

test('el CSV lleva una fila por parada y por pedido sin asignar', () => {
  const lineas = reporteCsv(reporteDePlan(planes[1])).replace('﻿', '').split('\r\n');
  assert.equal(lineas.length, 4);
  assert.ok(lineas[0].startsWith('Ruta;Unidad;Tipo'));
  assert.ok(lineas[1].startsWith('R1;TA01;AUTO;ALM-CENTRAL;2029-01-15T09:05:00;18.5;148;1;P1;P1;10;20;8;'));
  assert.ok(lineas[1].endsWith('Entregado'));
  assert.ok(lineas[3].endsWith('Sin asignar'));
  assert.equal(reporteCsv(null), '');
});
