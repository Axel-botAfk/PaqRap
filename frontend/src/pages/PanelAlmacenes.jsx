import React, { useEffect, useRef, useState } from 'react';
import { numero, fecha } from '../formato.js';
import { filasDeAlmacenes } from '../almacenes.js';
import { ETIQUETAS_STOCK } from '../stock.js';
import { UMBRALES_POR_OMISION } from '../umbrales.js';
import { enviosEn, ventanaDeEnvios, agruparOriginales } from '../envios.js';
import IconoUI from '../components/IconoUI.jsx';

const ESTADOS = { PLANIFICADO: 'Planificado', EN_RUTA: 'En ruta', ENTREGADO: 'Entregado',
  NO_ASIGNADO: 'Sin asignar' };

/**
 * Lista de almacenes del riel (E18, E19 y E20), enlazada con el mapa en los dos sentidos (F05).
 *
 * Presenta el stock en número y en porcentaje, el semáforo con estado vacío y texto además del
 * color, y consultas de envíos por ventana temporal. El porcentaje usa la capacidad publicada;
 * el stock inicial solo es referencia de compatibilidad para servidores anteriores.
 */
export default function PanelAlmacenes({ inventario, mapaDatos, rutas, estadosPedido,
  seleccionado, alSeleccionar, umbrales = UMBRALES_POR_OMISION, ejecucion,
  planes = [], instante = null, seleccionEnvio = null, rutasDelEnvio = null,
  alSeleccionarEnvio = () => {} }) {
  const [abierto, setAbierto] = useState(null);
  const [pedidoAbierto, setPedidoAbierto] = useState(null);
  const [consulta, setConsulta] = useState('transito');
  const filaRef = useRef(new Map());

  const filas = filasDeAlmacenes(inventario, mapaDatos?.almacenes, rutas, estadosPedido,
    umbrales.stock);
  const envios = ventanaDeEnvios(enviosEn(planes, instante), instante, consulta);
  const seleccionarEnvio = (tipo, id) => alSeleccionarEnvio(
    seleccionEnvio?.tipo === tipo && seleccionEnvio.id === id ? null : { tipo, id });

  useEffect(() => {
    if (!seleccionado) return;
    filaRef.current.get(seleccionado)?.scrollIntoView({ block: 'nearest' });
  }, [seleccionado]);

  if (!filas.length) {
    return <p className="vacio">Todavía no se cargaron los almacenes del período.</p>;
  }

  return <div className="panel-inventario">
    <label htmlFor="alm-consulta">Consultar envíos por almacén</label>
    <div className="alm-consulta-control"><IconoUI nombre="camion" size={15} />
    <select id="alm-consulta" className="input" value={consulta}
      onChange={e => { setConsulta(e.target.value); setAbierto(null); setPedidoAbierto(null);
        alSeleccionarEnvio(null); }}>
      <option value="transito">En tránsito</option>
      <option value="salidas">Salidas previstas · próximas 4 horas</option>
      <option value="entregados">Entregados · últimas 4 horas</option>
    </select></div>
    {seleccionEnvio && <div className="alm-seleccion" role="status">
      <span><b>{seleccionEnvio.tipo === 'original' ? 'Pedido' : 'Fracción'} {seleccionEnvio.id}</b>
        {rutasDelEnvio?.fracciones?.length ? seleccionEnvio.tipo === 'fraccion'
          ? ` · ${rutasDelEnvio.fracciones[0].unidad || 'sin unidad'} · ${rutasDelEnvio.fracciones[0].ruta || 'sin ruta asignada'}`
          : ` · ${rutasDelEnvio.fracciones.length} fracción(es)` : ''}</span>
      <span>{rutasDelEnvio?.rutas?.length
        ? `${rutasDelEnvio.rutas.length} ruta(s) resaltada(s): ${rutasDelEnvio.rutas.join(', ')}`
        : 'Sin ruta visible para esta selección en el instante actual.'}
        {!!rutasDelEnvio?.sinRutaVisible?.length &&
          ` ${rutasDelEnvio.sinRutaVisible.length} fracción(es) sin ruta visible.`}</span>
      <button type="button" onClick={() => alSeleccionarEnvio(null)}>Limpiar selección</button>
    </div>}
    <div className="almacenes">{filas.map(fila => {
      const activo = seleccionado === fila.id;
      const pedidos = envios.filter(p => p.almacen === fila.id);
      const originales = agruparOriginales(pedidos);
      const enTransito = { pedidos, productos: pedidos.reduce((n, p) => n + p.cantidad, 0),
        unidades: new Set(pedidos.map(p => p.unidad)).size };
      return <div key={fila.id} className={`alm ${activo ? 'alm-activo' : ''}`}
        ref={nodo => { nodo ? filaRef.current.set(fila.id, nodo) : filaRef.current.delete(fila.id); }}>
        <button type="button" className="alm-cabecera" aria-pressed={activo}
          onClick={() => alSeleccionar(activo ? null : fila.id)}>
          <div className="almacen-linea">
            <span><i className={`unidad-luz mapa-stock-${fila.estado}`} title={ETIQUETAS_STOCK[fila.estado]} />
              <strong>{fila.id}</strong> <em><IconoUI nombre="ubicacion" size={12} />
                ({fila.ubicacion.x}, {fila.ubicacion.y})</em></span>
            <b>{fila.ilimitado ? '∞'
              : fila.disponible === null ? '—'
                : `${numero(fila.disponible)}${fila.capacidad ? ` / ${numero(fila.capacidad)}` : ''}`}</b>
          </div>
          {!fila.ilimitado && <div className="bar"><i className={fila.estado === 'critico' ? 'bajo'
            : fila.estado === 'advertencia' ? 'medio' : ''}
          style={{ width: `${fila.porcentaje ?? 0}%` }} /></div>}
          <div className="almacen-pie">
            <span className="sub alm-estado">{fila.ilimitado ? 'Abastecimiento ilimitado' : ETIQUETAS_STOCK[fila.estado]}
              {fila.porcentaje !== null && <b className={`alm-porcentaje mapa-stock-${fila.estado}`}>{fila.porcentaje} %</b>}</span>
            <span className="sub alm-envios-resumen"><IconoUI nombre="info" size={12} />{enTransito.pedidos.length
              ? `${numero(enTransito.pedidos.length)} fracción(es) · ${numero(enTransito.productos)} producto(s)`
              : 'Sin envíos observados para esta consulta'}</span>
          </div>
        </button>
        {enTransito.pedidos.length > 0 && <>
          <button type="button" className="ut-envios" aria-expanded={abierto === fila.id}
            onClick={() => setAbierto(abierto === fila.id ? null : fila.id)}>
            {abierto === fila.id ? 'Ocultar envíos' : 'Ver envíos'} · {numero(enTransito.unidades)} unidad(es)
          </button>
          {abierto === fila.id && <ul className="ut-lista-envios alm-pedidos">
            {originales.map(grupo => {
              const clave = `${fila.id}:${grupo.id}`;
              const expandido = pedidoAbierto === clave;
              const marcado = seleccionEnvio?.tipo === 'original' && seleccionEnvio.id === grupo.id;
              const estados = [...new Set(grupo.fracciones.map(fraccion =>
                estadosPedido?.get(fraccion.id) || fraccion.estado).filter(Boolean))]
                .map(estado => ESTADOS[estado] || estado).join(' / ');
              return <li key={clave} className="alm-pedido">
                <div className={`alm-pedido-cabecera ${marcado ? 'alm-envio-marcado' : ''}`}>
                  <button type="button" className="alm-pedido-seleccionar" aria-pressed={marcado}
                    onClick={() => { seleccionarEnvio('original', grupo.id); setPedidoAbierto(clave); }}>
                    <strong>Pedido {grupo.id}</strong>
                    <span>{numero(grupo.productos)} unidades de P · {numero(grupo.fracciones.length)} fracción(es)</span>
                    {estados && <small>{estados}</small>}
                  </button>
                  <button type="button" className="alm-pedido-expandir" aria-expanded={expandido}
                    aria-label={`${expandido ? 'Ocultar' : 'Ver'} fracciones de ${grupo.id}`}
                    onClick={() => setPedidoAbierto(expandido ? null : clave)}>{expandido ? '⌃' : '⌄'}</button>
                </div>
                {expandido && <ul className="alm-fracciones">{grupo.fracciones.map(fraccion => {
                  const marcada = seleccionEnvio?.tipo === 'fraccion' && seleccionEnvio.id === fraccion.id;
                  return <li key={`${fraccion.ruta}-${fraccion.id}`}>
                    <button type="button" className={`alm-fraccion ${marcada ? 'alm-envio-marcado' : ''}`}
                      aria-pressed={marcada} onClick={() => seleccionarEnvio('fraccion', fraccion.id)}>
                      <strong>Fracción {fraccion.id}</strong>
                      <span>{numero(fraccion.cantidad)} unidades de P · {fraccion.unidad || 'Sin unidad'} · {fraccion.ruta || 'Sin ruta asignada'}</span>
                      <small>Salida {fecha(fraccion.salida)} · llegada {fecha(fraccion.llegada)}</small>
                    </button>
                  </li>;
                })}</ul>}
              </li>;
            })}
          </ul>}
        </>}
      </div>;
    })}</div>
    <p className="nota alm-nota"><IconoUI nombre="info" size={15} />{inventario
      ? `Inventario del último corte disponible, a las ${fecha(inventario.reloj)}.`
      : ejecucion ? 'Sin corte de inventario para este instante; se muestran los almacenes del período.'
        : 'Sin una corrida seleccionada, se muestra el stock inicial del caso.'} </p>
  </div>;
}
