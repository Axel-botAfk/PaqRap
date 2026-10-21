import React, { useMemo, useState } from 'react';
import { fecha, numero } from '../formato.js';
import PedidoNuevo from './PedidoNuevo.jsx';
import PedidoCargaMasiva from './PedidoCargaMasiva.jsx';
import PedidoSeguimiento from './PedidoSeguimiento.jsx';

const ETIQUETA = {
  PLANIFICADO: ['Planificado', 'n'], EN_RUTA: ['En ruta', 'a'],
  ENTREGADO: ['Entregado', 'v'], NO_ASIGNADO: ['Sin asignar', 'r']
};

const PESTANAS = [
  ['registrados', 'Pedidos registrados'],
  ['nuevo', 'Registrar pedido'],
  ['masiva', 'Carga masiva']
];

const LIMITE = 100;

/**
 * Hoja de pedidos: cubre el mapa en lugar de reemplazar la pantalla entera. Reúne las cuatro
 * pantallas de pedidos del prototipo —gestión, alta, carga masiva y seguimiento— en pestañas,
 * para no añadir entradas al menú lateral, que sigue teniendo las cuatro vistas del caso.
 *
 * La tabla muestra lo que publica el backend. El alta y la carga masiva no tienen endpoint:
 * cada una dice en su pie hasta dónde llega.
 */
export default function HojaPedidos({ ejecucion, estadosPedido, planes, periodo, mapaDatos, fuente }) {
  const [pestana, setPestana] = useState('registrados');
  const [filtro, setFiltro] = useState('');
  const [seguido, setSeguido] = useState(null);

  const filas = useMemo(() => [
    ...(ejecucion?.rutas || []).flatMap(ruta => ruta.paradas.map(pedido => ({
      ...pedido, ruta: ruta.id, vehiculo: ruta.vehiculoId,
      situacion: estadosPedido?.get(pedido.id) || 'PLANIFICADO'
    }))),
    ...(ejecucion?.noAsignados || []).map(pedido => ({
      ...pedido, ruta: '—', vehiculo: '—', situacion: 'NO_ASIGNADO'
    }))
  ], [ejecucion, estadosPedido]);

  const visibles = filtro ? filas.filter(f => f.situacion === filtro) : filas;
  const cuenta = situacion => filas.filter(f => f.situacion === situacion).length;
  const enSeguimiento = seguido && filas.find(f => f.id === seguido);

  return <section className="sheet-contenido">
    <div className="hoja-tabs" role="tablist" aria-label="Pantallas de pedidos">
      {PESTANAS.map(([clave, titulo]) => <button key={clave} type="button" role="tab"
        aria-selected={pestana === clave} className={pestana === clave ? 'activo' : ''}
        onClick={() => { setPestana(clave); setSeguido(null); }}>{titulo}</button>)}
    </div>

    {pestana === 'nuevo' && <PedidoNuevo almacenes={mapaDatos?.almacenes || []} />}
    {pestana === 'masiva' && <PedidoCargaMasiva periodo={periodo} />}

    {pestana === 'registrados' && (enSeguimiento
      ? <PedidoSeguimiento pedido={enSeguimiento} planes={planes}
        situacion={enSeguimiento.situacion} alVolver={() => setSeguido(null)} />
      : <>
        <div className="kpis">
          <div className="kpi" style={{ '--k': 'var(--ink)' }}><span>EN EL PLAN VIGENTE</span><b>{numero(filas.length)}</b></div>
          <div className="kpi" style={{ '--k': 'var(--verde)' }}><span>ENTREGADOS (ACUMULADO)</span><b>{numero(ejecucion?.avance?.pedidosEntregados)}</b></div>
          <div className="kpi" style={{ '--k': 'var(--ambar)' }}><span>EN RUTA</span><b>{numero(cuenta('EN_RUTA'))}</b></div>
          <div className="kpi" style={{ '--k': 'var(--red)' }}><span>SIN ASIGNAR</span><b>{numero(cuenta('NO_ASIGNADO'))}</b></div>
        </div>

        <div className="card">
          <header>
            <h2>PEDIDOS DEL PLAN VIGENTE</h2>
            <div className="filtro-opciones">
              <button type="button" className={filtro === '' ? 'activo' : ''} onClick={() => setFiltro('')}>Todos</button>
              {Object.entries(ETIQUETA).map(([valor, [texto]]) => <button key={valor} type="button"
                className={filtro === valor ? 'activo' : ''} onClick={() => setFiltro(valor)}>{texto}</button>)}
            </div>
          </header>
          {visibles.length === 0
            ? <p className="vacio" style={{ margin: 16 }}>No hay pedidos que mostrar en la instantánea actual.</p>
            : <div className="tabla-wrap"><table>
              <thead><tr><th>Pedido</th><th>Destino</th><th>Cantidad</th><th>Unidad</th><th>Ruta</th><th>Situación</th><th>Plazo</th><th /></tr></thead>
              <tbody>{visibles.slice(0, LIMITE).map((pedido, indice) => <tr key={`${pedido.id}-${indice}`}>
                <td><b>{pedido.id}</b></td>
                <td>{pedido.destino?.x}, {pedido.destino?.y}</td>
                <td>{numero(pedido.cantidad)}</td>
                <td>{pedido.vehiculo}</td>
                <td>{pedido.ruta}</td>
                <td><span className={`pill ${ETIQUETA[pedido.situacion][1]}`}>{ETIQUETA[pedido.situacion][0]}</span></td>
                <td>{fecha(pedido.fechaLimite)}</td>
                <td><button type="button" className="enlace-fila"
                  onClick={() => setSeguido(pedido.id)}>Seguir</button></td>
              </tr>)}</tbody>
            </table></div>}
          <div className="card-pie">
            {visibles.length > LIMITE && <span>Se muestran los primeros {LIMITE} de {numero(visibles.length)} registros.</span>}
            <span>Los pedidos se leen de {fuente === 'MYSQL' ? 'MySQL' : 'los archivos del curso'}: esta
              beta no registra ni cancela pedidos. «En ruta» y «entregado» se deducen de los hitos del
              simulador; los entregados que ya salieron del plan vigente se cuentan en el acumulado de
              arriba, no en la tabla.</span>
          </div>
        </div>
      </>)}
  </section>;
}
