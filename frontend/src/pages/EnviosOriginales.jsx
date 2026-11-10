import React, { useMemo, useState } from 'react';
import { enviosEn, ventanaDeEnvios, agruparOriginales } from '../envios.js';
import { fecha, numero } from '../formato.js';

/**
 * Lista de envíos con sus fracciones (E31 a E33). «Ver en el mapa» lleva la selección al mapa
 * y resalta la ruta de una fracción (F02) o todas las rutas del envío original (F03).
 */
export default function EnviosOriginales({ planes, instante, alVerEnMapa = null }) {
  const [modo, setModo] = useState('todos');
  const grupos = useMemo(() => agruparOriginales(ventanaDeEnvios(
    enviosEn(planes, instante), instante, modo)), [planes, instante, modo]);
  return <div className="card">
    <header><h2>Envíos originales y fracciones</h2>
      <select className="input" aria-label="Ventana de entregas" value={modo}
        onChange={e => setModo(e.target.value)}>
        <option value="todos">Todos los conocidos</option>
        <option value="entregados">Entregados en últimas 4 horas</option>
        <option value="proximos">Llegadas publicadas en próximas 4 horas</option>
      </select>
    </header>
    {!grupos.length && <p className="vacio">Sin envíos con hitos publicados para esta consulta.</p>}
    {grupos.map(grupo => <details key={grupo.id} className="envio-original">
      <summary>{grupo.id} · {grupo.fracciones.length} fracción(es) · {numero(grupo.productos)} productos observados
        {alVerEnMapa && <button type="button" className="enlace-fila envio-ver-mapa"
          onClick={evento => { evento.preventDefault(); alVerEnMapa({ tipo: 'original', id: grupo.id }); }}>
          Ver rutas en el mapa</button>}</summary>
      <div className="tabla-wrap"><table>
        <thead><tr><th>Fracción / producto</th><th>Cantidad</th><th>UT / ruta</th>
          <th>Origen / destino</th><th>Salida publicada</th><th>Llegada publicada</th>{alVerEnMapa && <th />}</tr></thead>
        <tbody>{grupo.fracciones.map(f => <tr key={f.id}>
          <td>{f.id} / P</td><td>{numero(f.cantidad)}</td><td>{f.unidad} / {f.ruta}</td>
          <td>{f.almacen} → ({f.destino?.x}, {f.destino?.y})</td>
          <td>{fecha(f.salida)}</td><td>{fecha(f.llegada)} · {f.entregaReal ? 'Realizada' : 'Prevista / no observada'}</td>
          {alVerEnMapa && <td><button type="button" className="enlace-fila"
            onClick={() => alVerEnMapa({ tipo: 'fraccion', id: f.id })}>Ver en el mapa</button></td>}
        </tr>)}</tbody>
      </table></div>
    </details>)}
    <p className="nota">Cantidades por fracción publicada, sin duplicar reasignaciones. Los planes
      disponibles limitan la cobertura; no se afirma que un original esté completo solo por
      observar una de sus partes. Los horarios futuros son previsiones del corte publicado.</p>
  </div>;
}
