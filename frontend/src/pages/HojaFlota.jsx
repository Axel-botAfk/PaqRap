import React, { useEffect, useState } from 'react';
import { api } from '../services/api.js';
import { numero, decimal, tipoVehiculo } from '../formato.js';
import IconoUI from '../components/IconoUI.jsx';
import DibujoVehiculo from '../components/DibujoVehiculo.jsx';

const TIPOS = ['AUTO', 'MOTO', 'BICICLETA'];

/**
 * Mantenimiento de unidades de transporte (B04): alta, baja, activación y nodo de partida por
 * unidad, y capacidad, velocidad y costo por tipo, que es como los define el caso.
 *
 * Lo guarda el servidor, no el navegador, así que lo ven todos los dispositivos. Aplica a las
 * corridas que se inicien después: una corrida en marcha conserva la flota con la que arrancó.
 */
export default function HojaFlota({ almacenes = [] }) {
  const [flota, setFlota] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [borradores, setBorradores] = useState({});
  const [nueva, setNueva] = useState({ tipo: 'AUTO', x: '', y: '' });
  const [edicion, setEdicion] = useState(null);
  const [filtroTipo, setFiltroTipo] = useState('');

  useEffect(() => {
    let activo = true;
    api.flota().then(datos => { if (activo) setFlota(datos); })
      .catch(e => { if (activo) setError(`No se pudo cargar la flota: ${e.message}`); });
    return () => { activo = false; };
  }, []);

  async function ejecutar(accion, mensaje) {
    setOcupado(true); setError(''); setAviso('');
    try { setFlota(await accion()); setAviso(mensaje); return true; }
    catch (e) { setError(e.message); return false; }
    finally { setOcupado(false); }
  }

  const coordenada = (x, y) => (x === '' || y === '' ? null : { x: Number(x), y: Number(y) });
  const enAlmacen = (id, destino) => {
    const almacen = almacenes.find(a => a.id === id);
    if (almacen) destino(String(almacen.ubicacion.x), String(almacen.ubicacion.y));
  };

  function guardarTipo(evento, tipo) {
    evento.preventDefault();
    const b = borradores[tipo.tipo] || {};
    const valor = (clave, actual) => (b[clave] === undefined || b[clave] === '' ? actual : Number(b[clave]));
    ejecutar(() => api.actualizarTipoFlota(tipo.tipo, {
      capacidad: valor('capacidad', tipo.capacidad),
      velocidadKmH: valor('velocidadKmH', tipo.velocidadKmH),
      costoPorKm: valor('costoPorKm', tipo.costoPorKm)
    }), `Atributos de ${tipoVehiculo(tipo.tipo)} actualizados.`).then(ok => {
      if (ok) setBorradores(previos => ({ ...previos, [tipo.tipo]: undefined }));
    });
  }

  function agregar(evento) {
    evento.preventDefault();
    ejecutar(() => api.agregarUnidad({ tipo: nueva.tipo, ubicacion: coordenada(nueva.x, nueva.y) }),
      `Unidad de tipo ${tipoVehiculo(nueva.tipo)} agregada.`);
  }

  function guardarUbicacion(evento) {
    evento.preventDefault();
    const ubicacion = coordenada(edicion.x, edicion.y);
    if (!ubicacion) { setError('Indica los dos valores del nodo de partida.'); return; }
    ejecutar(() => api.actualizarUnidad(edicion.id, { ubicacion }),
      `Nodo de partida de ${edicion.id} actualizado.`).then(ok => { if (ok) setEdicion(null); });
  }

  if (!flota) {
    return <section className="sheet-contenido">
      {error ? <div role="alert" className="vacio">{error}</div>
        : <p className="vacio" role="status">Cargando la flota…</p>}
    </section>;
  }

  const unidades = filtroTipo ? flota.unidades.filter(u => u.tipo === filtroTipo) : flota.unidades;
  const activas = flota.unidades.filter(u => u.activa).length;

  return <section className="sheet-contenido">
    <div className="kpis">
      <div className="kpi" style={{ '--k': 'var(--navy)' }}><span>UNIDADES REGISTRADAS</span>
        <b>{numero(flota.unidades.length)}</b><IconoUI nombre="camion" className="kpi-icono" size={32} /></div>
      <div className="kpi" style={{ '--k': 'var(--success)' }}><span>ACTIVAS PARA NUEVAS CORRIDAS</span>
        <b>{numero(activas)}</b><IconoUI nombre="reproducir" className="kpi-icono" size={32} /></div>
      <div className="kpi" style={{ '--k': 'var(--warning)' }}><span>FUERA DE SERVICIO</span>
        <b>{numero(flota.unidades.length - activas)}</b><IconoUI nombre="averias" className="kpi-icono" size={32} /></div>
    </div>

    {error && <div role="alert" className="flota-mensaje flota-error">{error}</div>}
    {aviso && <div role="status" className="flota-mensaje">{aviso} Rige para las corridas que se
      inicien a partir de ahora.</div>}

    <div className="card flota-tipos">
      <header><h2>ATRIBUTOS POR TIPO DE UNIDAD</h2>
        <span className="muted">El caso los define por tipo, no por unidad</span></header>
      <div className="flota-tipos-lista">{flota.tipos.map(tipo => {
        const b = borradores[tipo.tipo] || {};
        const campo = (clave, valor) => setBorradores(previos => ({ ...previos,
          [tipo.tipo]: { ...(previos[tipo.tipo] || {}), [clave]: valor } }));
        const distinto = tipo.capacidad !== tipo.capacidadDelCaso
          || tipo.velocidadKmH !== tipo.velocidadDelCaso || tipo.costoPorKm !== tipo.costoDelCaso;
        return <form key={tipo.tipo} className="flota-tipo" onSubmit={e => guardarTipo(e, tipo)}>
          <div className="flota-tipo-titulo">
            <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false"><DibujoVehiculo tipo={tipo.tipo} /></svg>
            <div><b>{tipoVehiculo(tipo.tipo)}</b>
              <small>{numero(tipo.activas)} activa(s) de {numero(tipo.unidades)}</small></div>
            {distinto && <span className="pill a">Modificado</span>}
          </div>
          <div className="flota-tipo-campos">
            <div className="field"><label htmlFor={`f-cap-${tipo.tipo}`}>Capacidad (productos)</label>
              <input id={`f-cap-${tipo.tipo}`} className="input" type="number" min="1" max="500" step="1"
                value={b.capacidad ?? tipo.capacidad} onChange={e => campo('capacidad', e.target.value)} /></div>
            <div className="field"><label htmlFor={`f-vel-${tipo.tipo}`}>Velocidad (km/h)</label>
              <input id={`f-vel-${tipo.tipo}`} className="input" type="number" min="1" max="120" step="0.5"
                value={b.velocidadKmH ?? tipo.velocidadKmH} onChange={e => campo('velocidadKmH', e.target.value)} /></div>
            <div className="field"><label htmlFor={`f-cos-${tipo.tipo}`}>Costo por km (S/)</label>
              <input id={`f-cos-${tipo.tipo}`} className="input" type="number" min="0.1" max="1000" step="0.1"
                value={b.costoPorKm ?? tipo.costoPorKm} onChange={e => campo('costoPorKm', e.target.value)} /></div>
          </div>
          <div className="flota-tipo-pie">
            <small>Valores del caso: {numero(tipo.capacidadDelCaso)} productos · {decimal(tipo.velocidadDelCaso, ' km/h')} · S/ {decimal(tipo.costoDelCaso)} por km</small>
            <button className="btn btn-secondary" type="submit"
              disabled={ocupado || !borradores[tipo.tipo]}>Guardar</button>
          </div>
        </form>;
      })}</div>
    </div>

    <div className="flota-columnas">
      <form className="card" onSubmit={agregar}>
        <header><h2>AGREGAR UNIDAD</h2></header>
        <div className="card-cuerpo">
          <div className="field"><label htmlFor="fn-tipo">Tipo de unidad</label>
            <select id="fn-tipo" className="input" value={nueva.tipo}
              onChange={e => setNueva({ ...nueva, tipo: e.target.value })}>
              {TIPOS.map(t => <option key={t} value={t}>{tipoVehiculo(t)}</option>)}
            </select></div>
          <div className="field"><label htmlFor="fn-almacen">Partir desde un almacén</label>
            <select id="fn-almacen" className="input" value=""
              onChange={e => enAlmacen(e.target.value, (x, y) => setNueva({ ...nueva, x, y }))}>
              <option value="">Elegir almacén…</option>
              {almacenes.map(a => <option key={a.id} value={a.id}>{a.id} ({a.ubicacion.x}, {a.ubicacion.y})</option>)}
            </select></div>
          <div className="row">
            <div className="field"><label htmlFor="fn-x">Nodo X (0 a 70)</label>
              <input id="fn-x" className="input" type="number" min="0" max="70" step="1" value={nueva.x}
                placeholder="Central" onChange={e => setNueva({ ...nueva, x: e.target.value })} /></div>
            <div className="field"><label htmlFor="fn-y">Nodo Y (0 a 50)</label>
              <input id="fn-y" className="input" type="number" min="0" max="50" step="1" value={nueva.y}
                placeholder="Central" onChange={e => setNueva({ ...nueva, y: e.target.value })} /></div>
          </div>
          <button className="btn btn-primary btn-block" type="submit" disabled={ocupado}>Agregar unidad</button>
          <p className="nota">El código (TA, TM o TB más correlativo) lo asigna el sistema. Sin nodo,
            la unidad parte del almacén central, como el resto de la flota.</p>
        </div>
      </form>

      <div className="card">
        <header><h2>UNIDADES DE TRANSPORTE</h2>
          <div className="filtro-opciones">
            <button type="button" className={filtroTipo === '' ? 'activo' : ''}
              onClick={() => setFiltroTipo('')}>Todas</button>
            {TIPOS.map(t => <button key={t} type="button" className={filtroTipo === t ? 'activo' : ''}
              onClick={() => setFiltroTipo(t)}>{tipoVehiculo(t)}</button>)}
          </div>
        </header>
        <div className="tabla-wrap"><table>
          <thead><tr><th>Unidad</th><th>Tipo</th><th>Nodo de partida</th><th>Capacidad</th>
            <th>Velocidad</th><th>Costo/km</th><th>Estado</th><th /></tr></thead>
          <tbody>{unidades.map(unidad => <tr key={unidad.id} className={unidad.activa ? '' : 'flota-inactiva'}>
            <td><b>{unidad.id}</b>{unidad.agregada && <span className="pill n flota-nueva">Agregada</span>}</td>
            <td>{tipoVehiculo(unidad.tipo)}</td>
            <td>{edicion?.id === unidad.id
              ? <form className="flota-ubicacion" onSubmit={guardarUbicacion}>
                <input className="input" type="number" min="0" max="70" aria-label={`Nodo X de ${unidad.id}`}
                  value={edicion.x} onChange={e => setEdicion({ ...edicion, x: e.target.value })} />
                <input className="input" type="number" min="0" max="50" aria-label={`Nodo Y de ${unidad.id}`}
                  value={edicion.y} onChange={e => setEdicion({ ...edicion, y: e.target.value })} />
                <select className="input" value="" aria-label="Ubicar en un almacén"
                  onChange={e => enAlmacen(e.target.value, (x, y) => setEdicion({ ...edicion, x, y }))}>
                  <option value="">Almacén…</option>
                  {almacenes.map(a => <option key={a.id} value={a.id}>{a.id}</option>)}
                </select>
                <button className="enlace-fila" type="submit" disabled={ocupado}>Guardar</button>
                <button className="enlace-fila" type="button" onClick={() => setEdicion(null)}>Cancelar</button>
              </form>
              : <>({unidad.ubicacionInicial.x}, {unidad.ubicacionInicial.y})
                {almacenes.find(a => a.ubicacion.x === unidad.ubicacionInicial.x
                  && a.ubicacion.y === unidad.ubicacionInicial.y)
                  ? ` · ${almacenes.find(a => a.ubicacion.x === unidad.ubicacionInicial.x
                    && a.ubicacion.y === unidad.ubicacionInicial.y).id}` : ''}</>}</td>
            <td>{numero(unidad.capacidad)}</td>
            <td>{decimal(unidad.velocidadKmH, ' km/h')}</td>
            <td>S/ {decimal(unidad.costoPorKm)}</td>
            <td><span className={`pill ${unidad.activa ? 'v' : 'n'}`}>{unidad.activa ? 'Activa' : 'Fuera de servicio'}</span></td>
            <td className="flota-acciones">
              {edicion?.id !== unidad.id && <button type="button" className="enlace-fila" disabled={ocupado}
                onClick={() => setEdicion({ id: unidad.id, x: String(unidad.ubicacionInicial.x),
                  y: String(unidad.ubicacionInicial.y) })}>Cambiar nodo</button>}
              <button type="button" className="enlace-fila" disabled={ocupado}
                onClick={() => ejecutar(() => api.actualizarUnidad(unidad.id, { activa: !unidad.activa }),
                  `${unidad.id} ${unidad.activa ? 'quedó fuera de servicio' : 'vuelve a estar activa'}.`)}>
                {unidad.activa ? 'Desactivar' : 'Activar'}</button>
              {unidad.agregada && <button type="button" className="enlace-fila" disabled={ocupado}
                onClick={() => ejecutar(() => api.eliminarUnidad(unidad.id), `${unidad.id} eliminada.`)}>
                Eliminar</button>}
            </td>
          </tr>)}</tbody>
        </table></div>
        <div className="card-pie">
          <span>Los cambios se guardan en el servidor y aplican a las corridas que se inicien después;
            una corrida en marcha conserva la flota con la que arrancó. Se mantienen mientras el
            servidor siga activo. Las unidades de la flota base no se eliminan: se desactivan.</span>
          {flota.modificada && <button type="button" className="enlace-fila" disabled={ocupado}
            onClick={() => ejecutar(() => api.restablecerFlota(), 'Flota restablecida a los valores del caso.')
              .then(() => { setBorradores({}); setEdicion(null); })}>
            Restablecer la flota del caso</button>}
        </div>
      </div>
    </div>
  </section>;
}
