import React, { useEffect, useRef, useState } from 'react';
import { fecha, numero } from '../formato.js';
import { parsearVentas, periodoDelNombre } from '../pedidos.js';
import { api } from '../services/api.js';
import IconoUI from '../components/IconoUI.jsx';

const MUESTRA = 50;

/**
 * Carga masiva del archivo mensual de ventas (imagen 10 del prototipo). Primero se valida en el
 * navegador, línea por línea, como pide la exigencia; luego «Incorporar» lo envía al servidor,
 * que repite la validación con las mismas reglas y reemplaza los pedidos de ese mes. Las líneas
 * con error se descartan. Rige para las corridas que se inicien después.
 */
export default function PedidoCargaMasiva({ periodo = '', alActualizarDatos = () => {} }) {
  const [aaaamm, setAaaamm] = useState(periodo);
  const [archivo, setArchivo] = useState('');
  const [resultado, setResultado] = useState(null);
  const [leyendo, setLeyendo] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [incorporada, setIncorporada] = useState(null);
  const [error, setError] = useState('');
  const [cargas, setCargas] = useState(null);
  const entrada = useRef(null);

  const refrescarCargas = () => api.cargasVentas().then(setCargas).catch(() => setCargas(null));
  useEffect(() => { refrescarCargas(); }, []);

  async function tomar(evento) {
    const elegido = evento.target.files?.[0];
    if (!elegido) return;
    setLeyendo(true);
    try {
      const contenido = await elegido.text();
      const delNombre = periodoDelNombre(elegido.name);
      const usado = delNombre || aaaamm;
      setArchivo(elegido.name);
      setAaaamm(usado);
      setTexto(contenido);
      setIncorporada(null);
      setError('');
      setResultado({ ...parsearVentas(contenido, usado), periodo: usado, delNombre: !!delNombre });
    } finally {
      setLeyendo(false);
    }
  }

  // Si el periodo se corrige a mano, la vista previa se vuelve a calcular con él.
  function cambiarPeriodo(valor) {
    setAaaamm(valor);
    if (texto && resultado && !resultado.delNombre) {
      setResultado({ ...parsearVentas(texto, valor), periodo: valor, delNombre: false });
    }
  }

  async function incorporar() {
    setEnviando(true);
    setError('');
    try {
      const respuesta = await api.cargarVentas(resultado.periodo, archivo, texto);
      setIncorporada(respuesta);
      refrescarCargas();
      alActualizarDatos();
    } catch (e) { setError(e.message); }
    finally { setEnviando(false); }
  }

  async function quitar(periodoCarga) {
    setError('');
    try {
      await api.quitarVentas(periodoCarga);
      if (incorporada?.periodo === periodoCarga) setIncorporada(null);
      refrescarCargas();
      alActualizarDatos();
    } catch (e) { setError(e.message); }
  }

  function limpiar() {
    setResultado(null);
    setTexto('');
    setIncorporada(null);
    setError('');
    setArchivo('');
    if (entrada.current) entrada.current.value = '';
  }

  const inusuales = resultado?.pedidos.filter(p => p.inusual) || [];

  return <>
    <div className="card">
      <header><h2>ARCHIVO MENSUAL DE VENTAS</h2>
        {resultado && <button type="button" className="btn btn-secondary" onClick={limpiar}>Elegir otro</button>}
      </header>
      <div className="card-cuerpo">
        <div className="row">
          <div className="field"><label htmlFor="cm-archivo">Archivo</label>
            <input id="cm-archivo" ref={entrada} className="input" type="file" accept=".txt,text/plain"
              onChange={tomar} /></div>
          <div className="field"><label htmlFor="cm-periodo">Periodo (aaaamm)</label>
            <input id="cm-periodo" className="input" value={aaaamm} placeholder="202601"
              onChange={e => cambiarPeriodo(e.target.value.trim())}
              disabled={resultado?.delNombre} /></div>
        </div>
        <p className="nota nota-carga"><IconoUI nombre="info" size={24} /><span>Un registro por línea, con el formato
          <code> ##d##h##m:posX,posY,cIdCliente,qq,hl</code>. El periodo sale del nombre del
          archivo cuando lo trae (<code>ventas.202601.txt</code>); si no, se indica a mano,
          porque de él dependen los días válidos del mes.</span></p>
        {leyendo && <p className="nota" role="status">Leyendo el archivo…</p>}
      </div>
    </div>

    {error && <div role="alert" className="flota-mensaje flota-error">{error}</div>}

    {resultado && <div className="card carga-incorporar">
      <header><h2>INCORPORAR AL SISTEMA</h2></header>
      <div className="card-cuerpo">
        {incorporada ? <div role="status" className="flota-mensaje">
          Se incorporaron {numero(incorporada.registros)} pedidos de {incorporada.periodo.slice(0, 4)}-{incorporada.periodo.slice(4)}
          {incorporada.descartados ? ` y se descartaron ${numero(incorporada.descartados)} línea(s) con error` : ''}.
          {incorporada.reemplazo ? ' Reemplazan los pedidos que el sistema tenía para ese mes.' : ' El mes se agregó al calendario.'}
          {' '}Rige para las corridas que se inicien a partir de ahora.
          {!incorporada.persistente && ' Atención: el servidor no pudo guardarlo en disco y se perderá al reiniciarse.'}
        </div> : <>
          <p className="nota">Se enviarán {numero(resultado.pedidos.length)} pedidos válidos de
            {' '}{resultado.periodo ? `${resultado.periodo.slice(0, 4)}-${resultado.periodo.slice(4)}` : '—'}
            {resultado.errores.length ? `; las ${numero(resultado.errores.length)} línea(s) con error se descartan` : ''}.
            Reemplazan los pedidos de ese mes; los bloqueos y el mantenimiento no cambian.</p>
          <button type="button" className="btn btn-primary"
            disabled={enviando || !resultado.pedidos.length || !/^\d{6}$/.test(resultado.periodo || '')}
            onClick={incorporar}>
            {enviando ? 'Incorporando…' : `Incorporar ${numero(resultado.pedidos.length)} pedidos`}</button>
        </>}
      </div>
    </div>}

    {cargas?.length > 0 && <div className="card">
      <header><h2>MESES CARGADOS DESDE ESTA PANTALLA</h2></header>
      <div className="tabla-wrap"><table>
        <thead><tr><th>Periodo</th><th>Archivo</th><th>Pedidos</th><th>Descartados</th><th>Primer / último pedido</th><th>Cargado</th><th /></tr></thead>
        <tbody>{cargas.map(c => <tr key={c.periodo}>
          <td><b>{c.periodo.slice(0, 4)}-{c.periodo.slice(4)}</b></td>
          <td>{c.nombre}{!c.persistente && <span className="pill a flota-nueva">Solo en memoria</span>}</td>
          <td>{numero(c.registros)}</td>
          <td>{numero(c.descartados)}</td>
          <td>{fecha(c.primerPedido)} · {fecha(c.ultimoPedido)}</td>
          <td>{fecha(c.cargadoEn)}</td>
          <td><button type="button" className="enlace-fila" onClick={() => quitar(c.periodo)}>Quitar carga</button></td>
        </tr>)}</tbody>
      </table></div>
      <div className="card-pie"><span>Quitar una carga devuelve ese mes a los datos originales del sistema.</span></div>
    </div>}

    {resultado && <>
      <div className="kpis">
        <div className="kpi" style={{ '--k': 'var(--ink)' }}><span>LÍNEAS LEÍDAS</span><b>{numero(resultado.leidas)}</b></div>
        <div className="kpi" style={{ '--k': 'var(--verde)' }}><span>PEDIDOS VÁLIDOS</span><b>{numero(resultado.pedidos.length)}</b></div>
        <div className="kpi" style={{ '--k': 'var(--ambar)' }}><span>CON AVISO</span><b>{numero(inusuales.length)}</b></div>
        <div className="kpi" style={{ '--k': 'var(--red)' }}><span>LÍNEAS CON ERROR</span><b>{numero(resultado.errores.length)}</b></div>
      </div>

      {resultado.errores.length > 0 && <div className="card">
        <header><h2>LÍNEAS RECHAZADAS</h2></header>
        <div className="tabla-wrap"><table>
          <thead><tr><th>Línea</th><th>Contenido</th><th>Motivo</th></tr></thead>
          <tbody>{resultado.errores.slice(0, MUESTRA).map(e => <tr key={e.numero}>
            <td><b>{numero(e.numero)}</b></td>
            <td><code>{e.linea}</code></td>
            <td>{e.motivo}</td>
          </tr>)}</tbody>
        </table></div>
        <div className="card-pie">
          {resultado.errores.length > MUESTRA && <span>Se muestran los primeros {MUESTRA} de {numero(resultado.errores.length)} errores.</span>}
        </div>
      </div>}

      <div className="card">
        <header><h2>PEDIDOS LEÍDOS</h2></header>
        {resultado.pedidos.length === 0
          ? <p className="vacio" style={{ margin: 16 }}>Ninguna línea del archivo resultó válida.</p>
          : <div className="tabla-wrap"><table>
            <thead><tr><th>Pedido</th><th>Cliente</th><th>Destino</th><th>Cantidad</th><th>Plazo</th><th>Recepción</th></tr></thead>
            <tbody>{resultado.pedidos.slice(0, MUESTRA).map(p => <tr key={p.id}>
              <td><b>{p.id}</b></td>
              <td>{p.cliente}</td>
              <td>{p.destino.x}, {p.destino.y}</td>
              <td>{numero(p.cantidad)}</td>
              <td>{p.inusual
                ? <span className="pill a" title={p.inusual}>{p.horas} h</span>
                : `${p.horas} h`}</td>
              <td>{fecha(p.recepcion)}</td>
            </tr>)}</tbody>
          </table></div>}
        <div className="card-pie">
          {resultado.pedidos.length > MUESTRA && <span>Se muestran los primeros {MUESTRA} de {numero(resultado.pedidos.length)} pedidos.</span>}
          <span>Vista previa de la validación. Usa «Incorporar» para enviar los pedidos al
            servidor, que vuelve a validarlos con las mismas reglas.</span>
        </div>
      </div>
    </>}
  </>;
}
