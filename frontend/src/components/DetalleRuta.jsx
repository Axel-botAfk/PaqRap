import React from 'react';
import { decimal, fecha, numero } from '../services/formato.js';

/* Detalle de la ruta elegida. Vive en el riel derecho, bajo la lista de rutas, no en una
   pantalla propia: así el mapa sigue visible mientras se inspecciona la secuencia. */

export default function DetalleRuta({ ejecucion, rutaId, alCerrar }) {
  const ruta = (ejecucion?.rutas || []).find(r => r.id === rutaId);
  if (!ruta) return null;

  return <section className="float detalle">
    <header>
      <h2>Detalle · {ruta.id}</h2>
      <button type="button" className="x" onClick={alCerrar} aria-label="Cerrar el detalle">✕</button>
    </header>
    <div className="body">
      <dl className="resumen">
        <div><dt>Unidad</dt><dd>{ruta.vehiculoId} · {ruta.tipoVehiculo}</dd></div>
        <div><dt>Almacén de salida</dt><dd>{ruta.almacenId}</dd></div>
        <div><dt>Origen</dt><dd className="mono">({ruta.origen?.x}, {ruta.origen?.y})</dd></div>
        <div><dt>Carga</dt><dd>{numero(ruta.carga)} u.</dd></div>
        <div><dt>Distancia</dt><dd>{decimal(ruta.distanciaKm, ' km')}</dd></div>
        <div><dt>Costo</dt><dd>S/ {decimal(ruta.costo)}</dd></div>
      </dl>

      <p className="sect" style={{ marginTop: 16 }}>Secuencia de paradas · {ruta.paradas.length}</p>
      {ruta.paradas.length === 0
        ? <p className="vacio">Esta ruta no tiene paradas.</p>
        : <div className="tabla-caja"><table>
          <thead><tr><th>#</th><th>Pedido</th><th>Nodo</th><th>Cant.</th><th>Plazo</th></tr></thead>
          <tbody>{ruta.paradas.map((parada, indice) => <tr key={`${parada.id}-${indice}`}>
            <td>{indice + 1}</td>
            <td className="mono">{parada.idOriginal || parada.id}</td>
            <td className="mono">{parada.destino?.x}, {parada.destino?.y}</td>
            <td>{numero(parada.cantidad)}</td>
            <td>{fecha(parada.fechaLimite)}</td>
          </tr>)}</tbody>
        </table></div>}

      <p className="nota">
        La secuencia es el plan vigente para esta unidad, no entregas confirmadas. Un pedido
        de más unidades que la capacidad se atiende en entregas parciales, que pueden caer en
        rutas distintas.
      </p>
    </div>
  </section>;
}
