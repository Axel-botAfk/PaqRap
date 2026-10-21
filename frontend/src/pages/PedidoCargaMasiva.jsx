import React, { useRef, useState } from 'react';
import { fecha, numero } from '../formato.js';
import { parsearVentas, periodoDelNombre } from '../pedidos.js';

const MUESTRA = 50;

/**
 * Carga masiva del archivo mensual de ventas (imagen 10 del prototipo). La exigencia pide
 * validar la estructura *antes* de incorporar los registros, y eso es exactamente lo que se
 * puede hacer sin servidor: se lee el archivo en el navegador y se reporta línea por línea.
 *
 * El backend lee sus propios TXT del disco al arrancar; no hay endpoint de subida, así que la
 * incorporación todavía es manual. Lo que esta pantalla ahorra es descubrir el archivo roto
 * a la mitad de una corrida.
 */
export default function PedidoCargaMasiva({ periodo = '' }) {
  const [aaaamm, setAaaamm] = useState(periodo);
  const [archivo, setArchivo] = useState('');
  const [resultado, setResultado] = useState(null);
  const [leyendo, setLeyendo] = useState(false);
  const entrada = useRef(null);

  async function tomar(evento) {
    const elegido = evento.target.files?.[0];
    if (!elegido) return;
    setLeyendo(true);
    try {
      const texto = await elegido.text();
      const delNombre = periodoDelNombre(elegido.name);
      const usado = delNombre || aaaamm;
      setArchivo(elegido.name);
      setAaaamm(usado);
      setResultado({ ...parsearVentas(texto, usado), periodo: usado, delNombre: !!delNombre });
    } finally {
      setLeyendo(false);
    }
  }

  function limpiar() {
    setResultado(null);
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
              onChange={e => setAaaamm(e.target.value.trim())}
              disabled={resultado?.delNombre} /></div>
        </div>
        <p className="nota">Un registro por línea, con el formato
          <code> ##d##h##m:posX,posY,cIdCliente,qq,hl</code>. El periodo sale del nombre del
          archivo cuando lo trae (<code>ventas.202601.txt</code>); si no, se indica a mano,
          porque de él dependen los días válidos del mes.</p>
        {leyendo && <p className="nota" role="status">Leyendo el archivo…</p>}
      </div>
    </div>

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
          <span>El backend aborta la lectura en la primera línea mala; aquí se recorren todas
            para corregirlas de una vez.</span>
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
          <span>Los identificadores son los que asignaría el backend: correlativos por orden de
            aparición, contando también las líneas rechazadas.</span>
          <span>La API no recibe archivos: esta pantalla valida la estructura, no incorpora los
            pedidos. El archivo se deja en el directorio de datos del servidor
            (<code>PAQRAP_DATA_DIR</code>) y se inicia una corrida con una fecha de ese mes.</span>
        </div>
      </div>
    </>}
  </>;
}
