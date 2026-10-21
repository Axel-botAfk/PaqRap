import React, { useMemo } from 'react';
import { fecha, decimal, numero } from '../formato.js';
import { aMs } from '../reloj.js';
import { seguimientoDePedido } from '../pedidos.js';

const HITO = {
  SALIDA: 'Sale del almacén', ALMACEN: 'Pasa por almacén', ENTREGA: 'Entrega',
  FIN_ENTREGA: 'Fin de la entrega', CORTE: 'Corte de la ventana'
};

const SITUACION = {
  PLANIFICADO: ['Planificado', 'n'], EN_RUTA: ['En ruta', 'a'],
  ENTREGADO: ['Entregado', 'v'], NO_ASIGNADO: ['Sin asignar', 'r']
};

/**
 * Seguimiento de un pedido (imagen 11 del prototipo): a qué ruta quedó asignado en cada
 * replanificación y qué hitos informó el simulador, en una línea de tiempo.
 *
 * Sale del historial de planes, la misma fuente que la columna «Situación», de modo que las
 * dos vistas no pueden contradecirse. Es planificación informada, no telemetría.
 */
export default function PedidoSeguimiento({ pedido, planes, situacion, alVolver }) {
  const { asignaciones, hitos } = useMemo(
    () => seguimientoDePedido(planes, pedido?.id, aMs), [planes, pedido?.id]);

  if (!pedido) return null;
  const ultima = asignaciones[asignaciones.length - 1];
  const [texto, clase] = SITUACION[situacion] || SITUACION.PLANIFICADO;

  return <div className="card">
    <header>
      <h2>SEGUIMIENTO · {pedido.id}</h2>
      <button type="button" className="btn btn-secondary" onClick={alVolver}>Volver a la tabla</button>
    </header>

    <div className="card-cuerpo">
      <dl className="ficha-pedido">
        <div><dt>Situación</dt><dd><span className={`pill ${clase}`}>{texto}</span></dd></div>
        <div><dt>Destino</dt><dd>{pedido.destino?.x}, {pedido.destino?.y}</dd></div>
        <div><dt>Cantidad</dt><dd>{numero(pedido.cantidad)} unidades</dd></div>
        <div><dt>Plazo</dt><dd>{fecha(pedido.fechaLimite)}</dd></div>
        <div><dt>Unidad</dt><dd>{ultima?.vehiculoId || '—'}</dd></div>
        <div><dt>Almacén de despacho</dt><dd>{ultima?.almacenId || '—'}</dd></div>
      </dl>
    </div>

    <header><h2>LÍNEA DE TIEMPO</h2></header>
    <div className="card-cuerpo">
      {hitos.length === 0
        ? <p className="vacio">El simulador aún no informa hitos de este pedido. Los trayectos
          solo llegan hasta el corte de su ventana, así que una unidad que acaba de salir puede
          no tener todavía ningún hito de entrega.</p>
        : <ol className="linea-tiempo">{hitos.map((h, i) => <li key={`${h.vehiculoId}-${h.hito}-${i}`}
          data-propio={h.delPedido ? '' : undefined}>
          <b>{h.retoma ? 'Retoma la ruta tras replanificar' : (HITO[h.hito] || h.hito)}</b>
          <span>{fecha(h.instante)} · nodo ({h.ubicacion?.x}, {h.ubicacion?.y}) · {h.vehiculoId}</span>
        </li>)}</ol>}
    </div>

    <header><h2>ASIGNACIONES · {numero(asignaciones.length)}</h2></header>
    {asignaciones.length === 0
      ? <p className="vacio" style={{ margin: 16 }}>Ningún plan conocido le asignó una ruta.</p>
      : <div className="tabla-wrap"><table>
        <thead><tr><th>Plan vigente desde</th><th>Ruta</th><th>Unidad</th><th>Almacén</th><th>Distancia</th><th>Costo</th></tr></thead>
        <tbody>{asignaciones.map((a, i) => <tr key={`${a.rutaId}-${i}`}>
          <td>{fecha(a.reloj)}</td>
          <td><b>{a.rutaId}</b></td>
          <td>{a.vehiculoId} · {a.tipoVehiculo}</td>
          <td>{a.almacenId}</td>
          <td>{decimal(a.distanciaKm, ' km')}</td>
          <td>{typeof a.costo === 'number' ? `S/ ${decimal(a.costo)}` : '—'}</td>
        </tr>)}</tbody>
      </table></div>}

    <div className="card-pie">
      <span>Cada fila es una replanificación: si el pedido aparece más de una vez, cambió de
        ruta o de unidad por una avería o un bloqueo.</span>
      <span>La línea de tiempo muestra los hitos del plan. Los marcados en rojo son del pedido;
        la salida es de su unidad y es lo que lo pone «en ruta».</span>
    </div>
  </div>;
}
