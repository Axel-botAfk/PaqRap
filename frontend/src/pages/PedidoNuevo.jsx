import React, { useEffect, useMemo, useRef, useState } from 'react';
import { numero } from '../formato.js';
import { comprobaciones, hayErrores, aRegistro, solicitudPedido, recepcionParaCorrida, PLAZOS } from '../pedidos.js';
import { api } from '../services/api.js';

const NIVEL = { ok: ['CORRECTO', 'v'], aviso: ['AVISO', 'a'], error: ['ERROR', 'r'] };

const VACIO = { cliente: '', x: '', y: '', cantidad: '', plazo: '36', recepcion: '' };

/**
 * Alta de un pedido (imagen 09 del prototipo). Las validaciones del caso se muestran como
 * lista de comprobaciones y no como un mensaje suelto, según el estándar de GUI.
 *
 * El servidor confirma el alta y, si se elige una corrida activa, encola el pedido para su
 * próximo corte. El identificador de solicitud se conserva al reintentar tras un fallo de red.
 */
export default function PedidoNuevo({ almacenes = [], ejecucion, alActualizarDatos = () => {} }) {
  const [borrador, setBorrador] = useState(VACIO);
  const [registrados, setRegistrados] = useState([]);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [enVivo, setEnVivo] = useState(true);
  const solicitudId = useRef(null);
  const activa = ejecucion && ['EN_CURSO', 'PAUSADA'].includes(ejecucion.estado);
  const corte = recepcionParaCorrida(ejecucion);
  useEffect(() => {
    if (activa && corte) setBorrador(previo => previo.recepcion ? previo : { ...previo, recepcion: corte });
  }, [activa, corte]);
  const lista = useMemo(() => comprobaciones(borrador), [borrador]);
  const bloqueado = hayErrores(lista);

  const campo = (clave, valor) => {
    solicitudId.current = null;
    setError('');
    setBorrador(previo => ({ ...previo, [clave]: valor }));
  };

  async function registrar(evento) {
    evento.preventDefault();
    if (bloqueado || enviando) return;
    if (!solicitudId.current) solicitudId.current = crypto.randomUUID();
    setEnviando(true);
    setError('');
    try {
      const respuesta = await api.registrarPedido(solicitudPedido(borrador, solicitudId.current,
        activa && enVivo ? ejecucion.id : null));
      setRegistrados(previos => [{ ...borrador, ...respuesta, registro: aRegistro(borrador) },
        ...previos.filter(p => p.id !== respuesta.id)]);
      solicitudId.current = null;
      setBorrador({ ...VACIO, recepcion: activa ? corte : '' });
      alActualizarDatos();
    } catch (e) { setError(e.message); }
    finally { setEnviando(false); }
  }

  return <div className="pedido-alta">
    <form className="card" onSubmit={registrar} aria-busy={enviando}>
      <header><h2>DATOS DEL PEDIDO</h2></header>
      <fieldset className="card-cuerpo" disabled={enviando} style={{ border: 0, margin: 0, minWidth: 0 }}>
        {error && <p role="alert" className="flota-mensaje flota-error">{error}</p>}
        {registrados.length > 0 && <p role="status" className="flota-mensaje">
          Pedido {registrados[0].id} confirmado por el servidor.
          {registrados[0].ejecucionId ? ' Se incorporará a esta corrida desde el siguiente corte aplicable; la asignación depende del planificador.'
            : ' Disponible para las siguientes corridas del periodo indicado.'}
          {!registrados[0].persistente && ' Solo en memoria: se perderá al reiniciar el servidor.'}
        </p>}
        {activa && <div className="field"><label><input type="checkbox" checked={enVivo}
          onChange={e => { setEnVivo(e.target.checked); solicitudId.current = null; }} /> Incorporar también a la corrida activa</label>
          <p className="nota">El pedido se suma al periodo sin reemplazar ventas existentes.
            La recepción debe estar en un corte aún no procesado y dentro del horizonte de esta corrida.</p></div>}
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
            <input id="p-cant" className="input" type="number" min="1" max="100000" value={borrador.cantidad}
              onChange={e => campo('cantidad', e.target.value)} /></div>
          <div className="field"><label htmlFor="p-plazo">Plazo de entrega</label>
            <select id="p-plazo" className="input" value={borrador.plazo}
              onChange={e => campo('plazo', e.target.value)}>
              {PLAZOS.map(h => <option key={h} value={h}>{h} h {h === 36 ? '· regular' : '· priorizado'}</option>)}
            </select></div>
        </div>

        <div className="field"><label htmlFor="p-recep">Fecha y hora de recepción</label>
          <input id="p-recep" className="input" type="datetime-local" value={borrador.recepcion}
            onChange={e => campo('recepcion', e.target.value)} />
          {activa && <button type="button" className="enlace-fila" onClick={() => campo('recepcion', corte)}>
            Usar próximo corte publicado ({corte.replace('T', ' ')})</button>}</div>

        <div className="field"><label htmlFor="p-almacen">Almacén de despacho</label>
          <select id="p-almacen" className="input" disabled value="">
            <option value="">Lo asigna el planificador</option>
            {almacenes.map(a => <option key={a.id} value={a.id}>{a.id}</option>)}
          </select></div>

        <button className="btn btn-primary btn-block" type="submit" disabled={bloqueado || enviando}>
          {enviando ? 'Registrando…' : 'Registrar pedido'}</button>
      </fieldset>
    </form>

    <div className="card">
      <header><h2>COMPROBACIONES</h2></header>
      <div className="card-cuerpo">
        <ul className="chk">{lista.map(c => <li key={c.clave}>
          <span className={`pill ${NIVEL[c.nivel][1]}`}>{NIVEL[c.nivel][0]}</span>
          <span>{c.texto}</span>
        </li>)}</ul>
      </div>

      {registrados.length > 0 && <>
        <header><h2>EN ESTA SESIÓN · {numero(registrados.length)}</h2></header>
        <div className="tabla-wrap"><table>
          <thead><tr><th>Pedido</th><th>Cliente</th><th>Destino</th><th>Cantidad</th><th>Plazo</th><th>Línea del archivo</th></tr></thead>
          <tbody>{registrados.map((p, i) => <tr key={`${p.registro}-${i}`}>
            <td><b>{p.id}</b></td><td>{p.cliente}</td>
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
