import React, { useMemo, useState } from 'react';
import { numero } from '../formato.js';
import { comprobaciones, hayErrores, aRegistro, PLAZOS } from '../pedidos.js';
import { api } from '../services/api.js';

const NIVEL = { ok: ['CORRECTO', 'v'], aviso: ['AVISO', 'a'], error: ['ERROR', 'r'] };

const VACIO = { cliente: '', x: '', y: '', cantidad: '', plazo: '36', recepcion: '' };

/**
 * Alta de un pedido (imagen 09 del prototipo). Las validaciones del caso se muestran como
 * lista de comprobaciones y no como un mensaje suelto, según el estándar de GUI.
 *
 * Con una corrida activa el pedido se manda a `POST /api/ejecuciones/{id}/pedidos` y entra a la
 * cola del simulador en el corte siguiente: es lo que permite que la operación en tiempo real
 * muestre demanda que no existía al arrancar. La fecha de registro la pone el servidor con el
 * reloj de la corrida, no el formulario, para que ningún pedido se cuele antes del instante ya
 * publicado; el campo de recepción queda como dato del operador.
 *
 * Sin corrida activa no hay dónde registrarlo, así que se mantiene el comportamiento anterior:
 * queda en el navegador como línea del archivo mensual lista para pegar. El pie lo dice.
 */
export default function PedidoNuevo({ almacenes = [], ejecucionId = null, corriendo = false }) {
  const [borrador, setBorrador] = useState(VACIO);
  const [registrados, setRegistrados] = useState([]);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const lista = useMemo(() => comprobaciones(borrador), [borrador]);
  const bloqueado = hayErrores(lista);
  const enVivo = !!ejecucionId && corriendo;

  const campo = (clave, valor) => setBorrador(previo => ({ ...previo, [clave]: valor }));

  async function registrar(evento) {
    evento.preventDefault();
    if (bloqueado || enviando) return;
    setError('');
    const linea = aRegistro(borrador);
    if (!enVivo) {
      setRegistrados(previos => [{ ...borrador, registro: linea, destino: null }, ...previos]);
      setBorrador(VACIO);
      return;
    }
    setEnviando(true);
    try {
      const creado = await api.registrarPedido(ejecucionId, {
        cliente: borrador.cliente,
        x: Number(borrador.x),
        y: Number(borrador.y),
        cantidad: Number(borrador.cantidad),
        plazoHoras: Number(borrador.plazo)
      });
      setRegistrados(previos => [{ ...borrador, registro: linea, destino: creado }, ...previos]);
      setBorrador(VACIO);
    } catch (e) { setError(e.message); }
    finally { setEnviando(false); }
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

        <button className="btn btn-primary btn-block" type="submit" disabled={bloqueado || enviando}>
          {enviando ? 'Registrando…' : enVivo ? 'Registrar pedido en la operación' : 'Registrar pedido'}</button>
        {error && <p className="nota nota-aviso" role="alert">{error}</p>}
        <p className="nota">{enVivo
          ? 'Entra a la cola del planificador en el siguiente corte; el servidor le pone la hora de la operación.'
          : 'Sin corrida activa el pedido no se manda al servidor: queda como línea del archivo mensual.'}</p>
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
