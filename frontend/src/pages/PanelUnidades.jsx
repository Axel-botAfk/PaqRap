import React, { useEffect, useRef, useState } from 'react';
import { numero, tipoVehiculo, estadoVehiculo, fecha } from '../formato.js';
import { filasDeUnidades, ordenarUnidades, ORDENES } from '../unidades.js';
import { ETIQUETAS_CARGA } from '../carga.js';
import { ETIQUETAS_AVERIA } from '../averias.js';

/**
 * Lista de unidades de transporte del riel (E02 a E05 y E13), enlazada con el mapa en los dos
 * sentidos (F01): al elegir una fila el mapa la resalta, y al elegirla en el mapa esta lista la
 * marca y la trae a la vista.
 *
 * La carga es la del último corte publicado, el mismo que informa la ficha del mapa. Entre un
 * corte y el siguiente el mapa interpola la posición para animar el recorrido; la lista no
 * inventa esa interpolación y declara de cuándo es el dato.
 */
export default function PanelUnidades({ vehiculos, rutas, averias, seleccionada, alSeleccionar,
  reloj }) {
  const [orden, setOrden] = useState('ocupacion-desc');
  const [abierta, setAbierta] = useState(null);
  const filaRef = useRef(new Map());

  const filas = ordenarUnidades(filasDeUnidades(vehiculos, rutas), orden);

  // Al llegar la selección desde el mapa, traer la fila a la vista sin mover el resto del riel.
  useEffect(() => {
    if (!seleccionada) return;
    filaRef.current.get(seleccionada)?.scrollIntoView({ block: 'nearest' });
  }, [seleccionada]);

  if (!filas.length) {
    return <p className="vacio">No hay unidades publicadas para este instante.</p>;
  }

  return <>
    <div className="unidades-orden">
      <label htmlFor="u-orden">Ordenar por</label>
      <select id="u-orden" className="input" value={orden} onChange={e => setOrden(e.target.value)}>
        {ORDENES.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
      </select>
    </div>

    <div className="lista-unidades">{filas.map(fila => {
      const averia = averias?.get(fila.id);
      const activa = seleccionada === fila.id;
      return <div key={fila.id} className={`ut ${activa ? 'ut-activa' : ''}`}
        ref={nodo => { nodo ? filaRef.current.set(fila.id, nodo) : filaRef.current.delete(fila.id); }}>
        <button type="button" className="ut-cabecera" aria-pressed={activa}
          onClick={() => alSeleccionar(activa ? null : fila.id)}>
          <span className="l1">
            <i className={`unidad-luz mapa-carga-${fila.nivel}`} title={ETIQUETAS_CARGA[fila.nivel]} />
            <b>{fila.id}</b>
            <span className="sub">{tipoVehiculo(fila.tipo)}</span>
            <span className="num">{fila.porcentaje === null ? 'Sin dato'
              : `${numero(fila.cargaAbordo)} / ${numero(fila.capacidad)} · ${fila.porcentaje} %`}</span>
          </span>
          <span className="l2">
            <span className="sub">{estadoVehiculo(fila.estado)}{fila.ubicacion ? ` · (${fila.ubicacion.x}, ${fila.ubicacion.y})` : ''}</span>
            <span className="sub">{fila.pedidos.length
              ? `${numero(fila.pedidos.length)} pedido(s) · ${numero(fila.productos)} producto(s)`
              : 'Sin pedidos asignados'}</span>
          </span>
          {averia && <span className={`ut-averia mapa-averia-${averia.tipo.toLowerCase()}`}>
            {ETIQUETAS_AVERIA[averia.tipo]}
          </span>}
        </button>
        {fila.pedidos.length > 0 && <>
          <button type="button" className="ut-envios" aria-expanded={abierta === fila.id}
            onClick={() => setAbierta(abierta === fila.id ? null : fila.id)}>
            {abierta === fila.id ? 'Ocultar envíos' : 'Ver envíos'} · {fila.ruta}
          </button>
          {abierta === fila.id && <ul className="ut-lista-envios">
            {fila.pedidos.map(pedido => <li key={pedido.id}>
              <span>{pedido.id}</span><b>{numero(pedido.cantidad)} producto(s)</b>
            </li>)}
          </ul>}
        </>}
      </div>;
    })}</div>
    <p className="nota">{numero(filas.length)} unidad(es) del último corte publicado{reloj ? `, a las ${fecha(reloj)}` : ''}.</p>
  </>;
}
