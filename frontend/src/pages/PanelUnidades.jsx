import React, { useEffect, useRef, useState } from 'react';
import { numero, tipoVehiculo, estadoVehiculo, fecha, hora } from '../formato.js';
import { filasDeUnidades, ordenarUnidades, filtrarUnidades, ORDENES } from '../unidades.js';
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
  reloj, trayectos = null }) {
  const [orden, setOrden] = useState('ocupacion-desc');
  const [abierta, setAbierta] = useState(null);
  const [alDestino, setAlDestino] = useState('');
  const [desdeSalida, setDesdeSalida] = useState('');
  const filaRef = useRef(new Map());

  const todas = filasDeUnidades(vehiculos, rutas, trayectos, reloj);
  const limite = valor => (valor === '' ? null : Number(valor));
  const filas = ordenarUnidades(filtrarUnidades(todas, {
    minutosAlDestino: limite(alDestino), minutosDesdeSalida: limite(desdeSalida)
  }), orden);
  const hayBusqueda = alDestino !== '' || desdeSalida !== '';

  // Al llegar la selección desde el mapa, traer la fila a la vista sin mover el resto del riel.
  useEffect(() => {
    if (!seleccionada) return;
    filaRef.current.get(seleccionada)?.scrollIntoView({ block: 'nearest' });
  }, [seleccionada]);

  if (!todas.length) {
    return <p className="vacio">No hay unidades publicadas para este instante.</p>;
  }

  return <>
    <div className="unidades-orden">
      <label htmlFor="u-orden">Ordenar por</label>
      <select id="u-orden" className="input" value={orden} onChange={e => setOrden(e.target.value)}>
        {ORDENES.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
      </select>
    </div>

    {/* Búsqueda por minutos (E06 y E07): el caso pide distinguir la llegada de la salida. */}
    <div className="unidades-busqueda">
      <div className="field"><label htmlFor="u-destino">Al destino ≤ (min)</label>
        <input id="u-destino" className="input" type="number" min="0" step="5" value={alDestino}
          placeholder="—" onChange={e => setAlDestino(e.target.value)} /></div>
      <div className="field"><label htmlFor="u-origen">Desde la salida ≤ (min)</label>
        <input id="u-origen" className="input" type="number" min="0" step="5" value={desdeSalida}
          placeholder="—" onChange={e => setDesdeSalida(e.target.value)} /></div>
      {hayBusqueda && <button type="button" className="unidades-limpiar"
        onClick={() => { setAlDestino(''); setDesdeSalida(''); }}>Limpiar búsqueda</button>}
    </div>

    {!filas.length && <p className="vacio">Ninguna unidad cumple la búsqueda en este instante.</p>}

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
          {(fila.salida || fila.llegada) && <span className="l2 ut-horarios">
            <span className="sub">Salida {hora(fila.salida)}{Number.isFinite(fila.minutosDesdeSalida)
              ? ` · hace ${numero(fila.minutosDesdeSalida)} min` : ''}</span>
            <span className="sub">Llegada {hora(fila.llegada)}{Number.isFinite(fila.minutosAlDestino)
              ? ` · en ${numero(fila.minutosAlDestino)} min` : ''}</span>
          </span>}
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
    <p className="nota">{numero(filas.length)}{hayBusqueda ? ` de ${numero(todas.length)}` : ''} unidad(es) del último corte publicado{reloj ? `, a las ${fecha(reloj)}` : ''}. Los horarios salen del trayecto publicado para la ventana.</p>
  </>;
}
