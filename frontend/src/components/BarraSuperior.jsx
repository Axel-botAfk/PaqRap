import React from 'react';
import { ETIQUETAS, fechaReloj } from '../formato.js';

/**
 * Cabecera común. En las vistas de mapa lleva los controles del mapa (los pone el propio mapa en
 * `alRanuraControles`) y dos relojes cortos: REAL, la hora de este dispositivo, y SIM, el instante
 * simulado que se está mostrando. Ambos hasta el minuto.
 */
export default function BarraSuperior({ titulo, menuAbierto, alAlternarMenu, ejecucion,
  enMapa = false, ahoraReal = null, instanteSimulado = null, alRanuraControles = null }) {
  return <div className="top">
    <div className="marca-simbolo">PQR</div>
    <div className="wordmark">PaqRap</div>
    <button type="button" className="burger" onClick={alAlternarMenu}
      aria-expanded={menuAbierto} aria-label="Mostrar u ocultar el menú"><i /></button>
    <h1>{titulo}</h1>
    {enMapa && <div className="top-controles" ref={alRanuraControles} />}
    {enMapa && <div className="top-relojes">
      <time className="top-reloj top-reloj-real" title="Hora real de este dispositivo"
        dateTime={ahoraReal ? new Date(ahoraReal).toISOString() : undefined}>
        <abbr title="Tiempo real">REAL</abbr><b>{ahoraReal ? fechaReloj(new Date(ahoraReal)) : '—'}</b>
      </time>
      <time className="top-reloj top-reloj-sim" title="Instante simulado que muestra el mapa"
        dateTime={instanteSimulado || undefined}>
        <abbr title="Tiempo simulado">SIM</abbr><b>{instanteSimulado ? fechaReloj(instanteSimulado) : '—'}</b>
      </time>
    </div>}
    <span className={`estado-ejecucion ${ejecucion?.estado || ''}`}>
      {ejecucion ? ETIQUETAS[ejecucion.estado] : 'Sin corrida'}
    </span>
  </div>;
}
