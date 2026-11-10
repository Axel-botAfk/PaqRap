import React, { useState } from 'react';
import { numero, decimal } from '../formato.js';

export default function PanelRendimiento({ id, medicionInicio = null }) {
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
  const segundos = ms => (ms === null ? 'Esperando…' : `${decimal(ms / 1000)} s`);
  return <>
    {/* C03 y C04: latencia percibida desde que se pulsa «Iniciar», medida en este navegador. */}
    <dl className="rendimiento-inicio">
      <div><dt>De «Iniciar» al primer plan en el mapa</dt>
        <dd>{medicionInicio ? segundos(medicionInicio.planMs) : '—'}</dd></div>
      <div><dt>De «Iniciar» a la primera unidad en movimiento</dt>
        <dd>{medicionInicio ? segundos(medicionInicio.movimientoMs) : '—'}</dd></div>
    </dl>
    {!medicionInicio && <p className="nota">Estos dos tiempos se miden cuando la corrida se inicia
      desde este navegador; el mapa base ya está visible antes de iniciar.</p>}
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
  </>;
}
