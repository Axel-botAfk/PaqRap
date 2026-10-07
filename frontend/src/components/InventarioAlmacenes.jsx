import React from 'react';

/* Inventario de los almacenes.

   GET /api/datos/mapa/{aaaamm} ya trae el stock inicial de cada almacén, pero no el stock
   durante la corrida: el motor lo lleva en Inventario (el central con existencia ilimitada
   y los intermedios con tope de 1000 unidades, recargados cada 24 h a las 23:59:59) y no
   viaja en EjecucionVista. Hasta que exista el campo, el panel declara el estado en lugar
   de presentar el inicial como si fuera el actual.

   Contrato pedido al backend, en docs/api-pendiente.md:
     EjecucionVista.existencias: [{ almacenId, disponible, reservado, ilimitado }] */

const ALMACENES = [
  ['Central (27, 14)', 'Ilimitado'],
  ['Nor-Oeste (12, 38)', 'Tope 1000 u.'],
  ['Este (57, 27)', 'Tope 1000 u.']
];

export default function InventarioAlmacenes({ existencias }) {
  return <>
    <p className="sect" style={{ marginTop: 18 }}>Inventario de almacenes</p>

    {existencias
      ? <dl className="resumen">
        {existencias.map(fila => <div key={fila.almacenId}>
          <dt>{fila.almacenId}</dt>
          <dd>{fila.ilimitado ? 'Ilimitado' : `${fila.disponible} u.`}</dd>
        </div>)}
      </dl>
      : <>
        <dl className="resumen">
          {ALMACENES.map(([nombre, regla]) => <div key={nombre}>
            <dt>{nombre}</dt>
            <dd className="muted" style={{ fontWeight: 400 }}>{regla}</dd>
          </div>)}
        </dl>
        <div className="aviso aviso-ambar" style={{ marginTop: 12 }}>
          <b>PENDIENTE</b>
          <span>
            Las existencias por almacén aún no viajan en la respuesta de la API. Arriba están
            las reglas del caso, no el stock de esta corrida.
          </span>
        </div>
      </>}
  </>;
}
