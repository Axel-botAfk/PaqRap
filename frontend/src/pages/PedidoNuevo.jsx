import React, { useMemo, useState } from 'react';
import { numero } from '../formato.js';
import { comprobaciones, hayErrores, aRegistro, PLAZOS } from '../pedidos.js';

const NIVEL = { ok: ['OK', 'v'], aviso: ['AVISO', 'a'], error: ['ERROR', 'r'] };

const VACIO = { cliente: '', x: '', y: '', cantidad: '', plazo: '36', recepcion: '' };

/**
 * Alta de un pedido (imagen 09 del prototipo). Las validaciones del caso se muestran como
 * lista de comprobaciones y no como un mensaje suelto, según el estándar de GUI.
 *
 * El backend no tiene alta de pedidos: `POST /api/ejecuciones` inicia corridas y nada más.
 * Por eso lo que se registra aquí se queda en el navegador, en forma de líneas del archivo
 * mensual listas para pegar en él. El pie de la pantalla lo dice; conviene no disimularlo.
 */
export default function PedidoNuevo({ almacenes = [] }) {
  const [borrador, setBorrador] = useState(VACIO);
  const [registrados, setRegistrados] = useState([]);
  const lista = useMemo(() => comprobaciones(borrador), [borrador]);
  const bloqueado = hayErrores(lista);

  const campo = (clave, valor) => setBorrador(previo => ({ ...previo, [clave]: valor }));

  function registrar(evento) {
    evento.preventDefault();
    if (bloqueado) return;
    setRegistrados(previos => [{ ...borrador, registro: aRegistro(borrador) }, ...previos]);
    setBorrador(VACIO);
  }

  return <div className="pedido-alta">
    <form className="card" onSubmit={registrar}>
      <header><h2>DATOS DEL PEDIDO</h2></header>
      <div className="card-cuerpo">
        <div className="field"><label htmlFor="p-cliente">Cliente</label>
          <input id="p-cliente" className="input" value={borrador.cliente} placeholder="c4910"
            onChange={e => campo('cliente', e.target.value)} /></div>

        <div className="row">
          <div className="field"><label htmlFor="p-x">Nodo X</label>
            <input id="p-x" className="input" type="number" min="0" max="70" value={borrador.x}
              onChange={e => campo('x', e.target.value)} /></div>
          <div className="field"><label htmlFor="p-y">Nodo Y</label>
            <input id="p-y" className="input" type="number" min="0" max="50" value={borrador.y}
              onChange={e => campo('y', e.target.value)} /></div>
        </div>

        <div className="row">
          <div className="field"><label htmlFor="p-cant">Cantidad de producto P</label>
            <input id="p-cant" className="input" type="number" min="1" value={borrador.cantidad}
              onChange={e => campo('cantidad', e.target.value)} /></div>
          <div className="field"><label htmlFor="p-plazo">Plazo de entrega</label>
            <select id="p-plazo" className="input" value={borrador.plazo}
              onChange={e => campo('plazo', e.target.value)}>
              {PLAZOS.map(h => <option key={h} value={h}>{h} h {h === 36 ? '· regular' : '· priorizado'}</option>)}
            </select></div>
        </div>

        <div className="field"><label htmlFor="p-recep">Fecha y hora de recepción</label>
          <input id="p-recep" className="input" type="datetime-local" value={borrador.recepcion}
            onChange={e => campo('recepcion', e.target.value)} /></div>

        <div className="field"><label htmlFor="p-almacen">Almacén de despacho</label>
          <select id="p-almacen" className="input" disabled value="">
            <option value="">Lo asigna el planificador</option>
            {almacenes.map(a => <option key={a.id} value={a.id}>{a.id}</option>)}
          </select></div>

        <button className="btn btn-primary btn-block" type="submit" disabled={bloqueado}>
          Registrar pedido</button>
      </div>
    </form>

    <div className="card">
      <header><h2>COMPROBACIONES</h2></header>
      <div className="card-cuerpo">
        <ul className="chk">{lista.map(c => <li key={c.clave}>
          <span className={`pill ${NIVEL[c.nivel][1]}`}>{NIVEL[c.nivel][0]}</span>
          <span>{c.texto}</span>
        </li>)}</ul>
        <p className="nota">El almacén de despacho, la unidad y la hora estimada son campos
          calculados: los decide el planificador al incorporar el pedido, no el operador.</p>
      </div>

      {registrados.length > 0 && <>
        <header><h2>EN ESTA SESIÓN · {numero(registrados.length)}</h2></header>
        <div className="tabla-wrap"><table>
          <thead><tr><th>Cliente</th><th>Destino</th><th>Cantidad</th><th>Plazo</th><th>Línea del archivo</th></tr></thead>
          <tbody>{registrados.map((p, i) => <tr key={`${p.registro}-${i}`}>
            <td><b>{p.cliente}</b></td>
            <td>{p.x}, {p.y}</td>
            <td>{numero(Number(p.cantidad))}</td>
            <td>{p.plazo} h</td>
            <td><code>{p.registro}</code></td>
          </tr>)}</tbody>
        </table></div>
      </>}
    </div>
  </div>;
}
