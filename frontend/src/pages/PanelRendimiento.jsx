import React, { useState } from 'react';
import { numero, decimal } from '../formato.js';

export default function PanelRendimiento({ id }) {
  const [datos, setDatos] = useState(null), [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false);
  async function consultar() {
    setOcupado(true); setError('');
    try {
      const respuesta = await fetch(`/api/ejecuciones/${encodeURIComponent(id)}/rendimiento`);
      if (!respuesta.ok) throw new Error('No se pudo obtener el rendimiento de esta versión del servidor.');
      setDatos(await respuesta.json());
    } catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }
  function descargar() {
    const enlace = document.createElement('a');
    const url = URL.createObjectURL(new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' }));
    enlace.href = url; enlace.download = `rendimiento-${id}.json`; enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return <>
    <button type="button" className="btn" disabled={!id || ocupado} onClick={consultar}>
      {ocupado ? 'Consultando…' : 'Consultar mediciones del servidor'}</button>
    {error && <p role="alert">{error}</p>}
    {datos && datos.id === id && <>
      <dl><div><dt>Llamadas observadas</dt><dd>{numero(datos.cantidad)}</dd></div>
        <div><dt>Ta medio / máximo / p95</dt><dd>{decimal(datos.promedioTaMs)} / {numero(datos.maximoTaMs)} / {numero(datos.percentil95TaMs)} ms</dd></div>
        <div><dt>Cálculo acumulado</dt><dd>{numero(datos.sumaTaMs)} ms</dd></div>
        <div><dt>Duración real del servidor</dt><dd>{datos.finRealMs == null ? 'En curso'
          : `${decimal((datos.finRealMs - datos.inicioRealMs) / 1000)} s`}</dd></div></dl>
      <button type="button" className="btn" onClick={descargar}>Descargar evidencia JSON</button>
    </>}
    <p className="nota">Una medición por llamada real. La duración incluye esperas de reproducción.
      Memoria RSS se mide por separado sobre el proceso Java del servidor.</p>
  </>;
}
