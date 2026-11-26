import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { fecha, numero } from '../formato.js';

export default function PedidosManualesLista({ periodo, version }) {
  const [pedidos, setPedidos] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let vigente = true;
    setPedidos(null);
    setError('');
    if (!periodo) return undefined;
    api.pedidosManuales(periodo).then(lista => { if (vigente) setPedidos(lista); })
      .catch(e => { if (vigente) setError(e.message); });
    return () => { vigente = false; };
  }, [periodo, version]);

  return <div className="card">
    <header><h2>PEDIDOS MANUALES GUARDADOS · {periodo || 'SIN PERIODO'}</h2></header>
    {error ? <p role="alert" className="flota-mensaje flota-error">{error}</p>
      : !periodo ? <p className="vacio">Seleccione un periodo para consultar las altas manuales.</p>
        : pedidos === null ? <p role="status" className="nota">Consultando pedidos…</p>
          : !pedidos.length ? <p className="vacio">No hay altas manuales para este periodo.</p>
            : <div className="tabla-wrap"><table>
              <thead><tr><th>Pedido</th><th>Cliente</th><th>Destino</th><th>Cantidad</th><th>Recepción</th><th>Plazo</th></tr></thead>
              <tbody>{pedidos.slice(0, 100).map(p => <tr key={p.id}>
                <td><b>{p.id}</b></td><td>{p.cliente}</td><td>{p.destino.x}, {p.destino.y}</td>
                <td>{numero(p.cantidad)}</td><td>{fecha(p.fechaRegistro)}</td><td>{p.horasPlazo} h</td>
              </tr>)}</tbody>
            </table></div>}
    <div className="card-pie">Estas altas se suman a los archivos o MySQL y a las cargas mensuales.
      La tabla del instante inferior muestra su planificación cuando la corrida los procesa.
      {pedidos?.length > 100 && ` Se muestran 100 de ${numero(pedidos.length)} altas.`}</div>
  </div>;
}
