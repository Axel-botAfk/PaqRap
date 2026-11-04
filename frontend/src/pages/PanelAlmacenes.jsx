import React, { useEffect, useRef, useState } from 'react';
import { numero, fecha } from '../formato.js';
import { filasDeAlmacenes } from '../almacenes.js';
import { ETIQUETAS_STOCK } from '../stock.js';
import { UMBRALES_POR_OMISION } from '../umbrales.js';
import { enviosEn, ventanaDeEnvios } from '../envios.js';

/**
 * Lista de almacenes del riel (E18, E19 y E20), enlazada con el mapa en los dos sentidos (F05).
 *
 * Presenta el stock en número y en porcentaje, el semáforo con estado vacío y texto además del
 * color, y consultas de envíos por ventana temporal. El porcentaje usa la capacidad publicada;
 * el stock inicial solo es referencia de compatibilidad para servidores anteriores.
 */
export default function PanelAlmacenes({ inventario, mapaDatos, rutas, estadosPedido,
  seleccionado, alSeleccionar, umbrales = UMBRALES_POR_OMISION, ejecucion,
  planes = [], instante = null }) {
  const [abierto, setAbierto] = useState(null);
  const [consulta, setConsulta] = useState('transito');
  const filaRef = useRef(new Map());

  const filas = filasDeAlmacenes(inventario, mapaDatos?.almacenes, rutas, estadosPedido,
    umbrales.stock);
  const envios = ventanaDeEnvios(enviosEn(planes, instante), instante, consulta);

  useEffect(() => {
    if (!seleccionado) return;
    filaRef.current.get(seleccionado)?.scrollIntoView({ block: 'nearest' });
  }, [seleccionado]);

  if (!filas.length) {
    return <p className="vacio">Todavía no se cargaron los almacenes del período.</p>;
  }

  return <>
    <label htmlFor="alm-consulta">Consultar envíos por almacén</label>
    <select id="alm-consulta" className="input" value={consulta}
      onChange={e => setConsulta(e.target.value)}>
      <option value="transito">En tránsito</option>
      <option value="salidas">Salidas previstas · próximas 4 horas</option>
      <option value="entregados">Entregados · últimas 4 horas</option>
    </select>
    <div className="almacenes">{filas.map(fila => {
      const activo = seleccionado === fila.id;
      const pedidos = envios.filter(p => p.almacen === fila.id);
      const enTransito = { pedidos, productos: pedidos.reduce((n, p) => n + p.cantidad, 0),
        unidades: new Set(pedidos.map(p => p.unidad)).size };
      return <div key={fila.id} className={`alm ${activo ? 'alm-activo' : ''}`}
        ref={nodo => { nodo ? filaRef.current.set(fila.id, nodo) : filaRef.current.delete(fila.id); }}>
        <button type="button" className="alm-cabecera" aria-pressed={activo}
          onClick={() => alSeleccionar(activo ? null : fila.id)}>
          <div className="almacen-linea">
            <span><i className={`unidad-luz mapa-stock-${fila.estado}`} title={ETIQUETAS_STOCK[fila.estado]} />
              {fila.id} <em>({fila.ubicacion.x}, {fila.ubicacion.y})</em></span>
            <b>{fila.ilimitado ? '∞'
              : fila.disponible === null ? '—'
                : `${numero(fila.disponible)}${fila.capacidad ? ` / ${numero(fila.capacidad)}` : ''}`}</b>
          </div>
          {!fila.ilimitado && <div className="bar"><i className={fila.estado === 'critico' ? 'bajo'
            : fila.estado === 'advertencia' ? 'medio' : ''}
          style={{ width: `${fila.porcentaje ?? 0}%` }} /></div>}
          <div className="almacen-pie">
            <span className="sub">{fila.ilimitado ? 'Abastecimiento ilimitado' : ETIQUETAS_STOCK[fila.estado]}{fila.porcentaje === null ? ''
              : ` · ${fila.porcentaje} %`}</span>
            <span className="sub">{enTransito.pedidos.length
              ? `${numero(enTransito.pedidos.length)} fracción(es) · ${numero(enTransito.productos)} producto(s)`
              : 'Sin envíos observados para esta consulta'}</span>
          </div>
        </button>
        {enTransito.pedidos.length > 0 && <>
          <button type="button" className="ut-envios" aria-expanded={abierto === fila.id}
            onClick={() => setAbierto(abierto === fila.id ? null : fila.id)}>
            {abierto === fila.id ? 'Ocultar envíos' : 'Ver envíos'} · {numero(enTransito.unidades)} unidad(es)
          </button>
          {abierto === fila.id && <ul className="ut-lista-envios">
            {enTransito.pedidos.map(pedido => <li key={`${pedido.ruta}-${pedido.id}`}>
              <span>{pedido.id} · {pedido.unidad} · salida {fecha(pedido.salida)} · llegada {fecha(pedido.llegada)}</span><b>{numero(pedido.cantidad)} producto(s)</b>
            </li>)}
          </ul>}
        </>}
      </div>;
    })}</div>
    <p className="nota">{inventario
      ? `Inventario del último corte disponible, a las ${fecha(inventario.reloj)}.`
      : ejecucion ? 'Sin corte de inventario para este instante; se muestran los almacenes del período.'
        : 'Sin una corrida seleccionada, se muestra el stock inicial del caso.'} El porcentaje usa
      la capacidad publicada; con servidores anteriores solo hay referencia al stock inicial.
      Las consultas usan el reloj visible. Un horario ausente no se estima; las próximas salidas
      cubren únicamente planes publicados y pueden cambiar al replanificar.</p>
  </>;
}
