import React, { useMemo, useState } from 'react';
import { enviosEn, ventanaDeEnvios, agruparOriginales } from '../envios.js';
import { fecha, numero } from '../formato.js';

export default function EnviosOriginales({ planes, instante }) {
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
      <summary>{grupo.id} · {grupo.fracciones.length} fracción(es) · {numero(grupo.productos)} productos observados</summary>
      <div className="tabla-wrap"><table>
        <thead><tr><th>Fracción / producto</th><th>Cantidad</th><th>UT / ruta</th>
          <th>Origen / destino</th><th>Salida publicada</th><th>Llegada publicada</th></tr></thead>
        <tbody>{grupo.fracciones.map(f => <tr key={f.id}>
          <td>{f.id} / P</td><td>{numero(f.cantidad)}</td><td>{f.unidad} / {f.ruta}</td>
          <td>{f.almacen} → ({f.destino?.x}, {f.destino?.y})</td>
          <td>{fecha(f.salida)}</td><td>{fecha(f.llegada)} · {f.entregaReal ? 'Realizada' : 'Prevista / no observada'}</td>
        </tr>)}</tbody>
      </table></div>
    </details>)}
    <p className="nota">Cantidades por fracción publicada, sin duplicar reasignaciones. Los planes
      disponibles limitan la cobertura; no se afirma que un original esté completo solo por
      observar una de sus partes. Los horarios futuros son previsiones del corte publicado.</p>
  </div>;
}
