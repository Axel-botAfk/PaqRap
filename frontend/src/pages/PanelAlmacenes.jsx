import React, { useEffect, useRef, useState } from 'react';
import { numero, fecha } from '../formato.js';
import { filasDeAlmacenes } from '../almacenes.js';
import { ETIQUETAS_STOCK } from '../stock.js';
import { UMBRALES_POR_OMISION } from '../umbrales.js';

/**
 * Lista de almacenes del riel (E18, E19 y E20), enlazada con el mapa en los dos sentidos (F05).
 *
 * Presenta el stock en número y en porcentaje, el semáforo con estado vacío y texto además del
 * color, y los envíos que salieron del almacén y siguen en camino. El porcentaje se mide contra
 * el stock inicial del caso porque la API no publica capacidad, y el pie lo dice.
 */
export default function PanelAlmacenes({ inventario, mapaDatos, rutas, estadosPedido,
  seleccionado, alSeleccionar, umbrales = UMBRALES_POR_OMISION, ejecucion }) {
  const [abierto, setAbierto] = useState(null);
  const filaRef = useRef(new Map());

  const filas = filasDeAlmacenes(inventario, mapaDatos?.almacenes, rutas, estadosPedido,
    umbrales.stock);

  useEffect(() => {
    if (!seleccionado) return;
    filaRef.current.get(seleccionado)?.scrollIntoView({ block: 'nearest' });
  }, [seleccionado]);

  if (!filas.length) {
    return <p className="vacio">Todavía no se cargaron los almacenes del período.</p>;
  }

  return <>
    <div className="almacenes">{filas.map(fila => {
      const activo = seleccionado === fila.id;
      const enTransito = fila.enTransito;
      return <div key={fila.id} className={`alm ${activo ? 'alm-activo' : ''}`}
        ref={nodo => { nodo ? filaRef.current.set(fila.id, nodo) : filaRef.current.delete(fila.id); }}>
        <button type="button" className="alm-cabecera" aria-pressed={activo}
          onClick={() => alSeleccionar(activo ? null : fila.id)}>
          <div className="almacen-linea">
            <span><i className={`unidad-luz mapa-stock-${fila.estado}`} title={ETIQUETAS_STOCK[fila.estado]} />
              {fila.id} <em>({fila.ubicacion.x}, {fila.ubicacion.y})</em></span>
            <b>{fila.ilimitado ? '∞'
              : fila.disponible === null ? '—'
                : `${numero(fila.disponible)}${fila.inicial ? ` / ${numero(fila.inicial)}` : ''}`}</b>
          </div>
          <div className="bar"><i className={fila.estado === 'critico' ? 'bajo'
            : fila.estado === 'advertencia' ? 'medio' : ''}
          style={{ width: `${fila.ilimitado ? 100 : fila.porcentaje ?? 0}%` }} /></div>
          <div className="almacen-pie">
            <span className="sub">{ETIQUETAS_STOCK[fila.estado]}{fila.porcentaje === null ? ''
              : ` · ${fila.porcentaje} %`}</span>
            <span className="sub">{enTransito.pedidos.length
              ? `${numero(enTransito.pedidos.length)} envío(s) en tránsito · ${numero(enTransito.productos)} producto(s)`
              : 'Sin envíos en tránsito'}</span>
          </div>
        </button>
        {enTransito.pedidos.length > 0 && <>
          <button type="button" className="ut-envios" aria-expanded={abierto === fila.id}
            onClick={() => setAbierto(abierto === fila.id ? null : fila.id)}>
            {abierto === fila.id ? 'Ocultar envíos en tránsito' : 'Ver envíos en tránsito'} · {numero(enTransito.unidades)} unidad(es)
          </button>
          {abierto === fila.id && <ul className="ut-lista-envios">
            {enTransito.pedidos.map(pedido => <li key={`${pedido.ruta}-${pedido.id}`}>
              <span>{pedido.id} · {pedido.unidad}</span><b>{numero(pedido.cantidad)} producto(s)</b>
            </li>)}
          </ul>}
        </>}
      </div>;
    })}</div>
    <p className="nota">{inventario
      ? `Inventario del último corte disponible, a las ${fecha(inventario.reloj)}.`
      : ejecucion ? 'Sin corte de inventario para este instante; se muestran los almacenes del período.'
        : 'Sin una corrida seleccionada, se muestra el stock inicial del caso.'} El porcentaje se
      mide contra el stock inicial: la API todavía no publica la capacidad de cada almacén.</p>
  </>;
}
