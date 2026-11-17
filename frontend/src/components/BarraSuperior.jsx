import React from 'react';
import { ETIQUETAS, fecha } from '../formato.js';
import IconoUI from './IconoUI.jsx';
import useArrastreFlotante from './useArrastreFlotante.js';

const ICONOS_OPERACION = { ENTREGADOS: 'caja', 'EN RUTA': 'ruta', PLANIFICADOS: 'calendario', 'POR PLANIFICAR': 'reloj' };

/** Cabecera común; en las vistas de mapa, los indicadores se presentan flotantes. */
export default function BarraSuperior({ titulo, menuAbierto, alAlternarMenu, ejecucion,
  chips = [], meta = [], modoOperacion = false, ahoraReal = null }) {
  const arrastreMetricas = useArrastreFlotante();
  return <>
    <div className="top">
      <div className="marca-simbolo">PQR</div>
      <div className="wordmark">PaqRap</div>
      <button type="button" className="burger" onClick={alAlternarMenu}
        aria-expanded={menuAbierto} aria-label="Mostrar u ocultar el menú"><i /></button>
      <h1>{titulo}</h1>
      {modoOperacion && <time className="top-ahora" dateTime={ahoraReal ? new Date(ahoraReal).toISOString() : undefined}>
        <IconoUI nombre="reloj" size={17} /> Ahora: {ahoraReal ? fecha(new Date(ahoraReal)) : '—'}
      </time>}
      <span className={`estado-ejecucion ${ejecucion?.estado || ''}`}>
        {ejecucion ? ETIQUETAS[ejecucion.estado] : 'Sin corrida'}
      </span>
    </div>
    <div className="kbar" ref={arrastreMetricas.ref}
      style={modoOperacion ? arrastreMetricas.style : undefined}
      onPointerDown={modoOperacion ? arrastreMetricas.onPointerDown : undefined}
      onPointerMove={modoOperacion ? arrastreMetricas.onPointerMove : undefined}
      onPointerUp={modoOperacion ? arrastreMetricas.onPointerUp : undefined}
      onPointerCancel={modoOperacion ? arrastreMetricas.onPointerCancel : undefined}>
      {chips.length > 0 && <div className="chips">
        {chips.map(chip => <div key={chip.etiqueta} className="chip"
          data-alerta={chip.alerta ? '' : undefined} title={chip.titulo}>
          {modoOperacion && <IconoUI nombre={ICONOS_OPERACION[chip.etiqueta]} size={28} className="chip-icono" />}
          <span>{chip.etiqueta}</span><b>{chip.valor}</b>
        </div>)}
      </div>}
      {meta.length > 0 && <div className="kmeta">
        {meta.map(dato => <span key={dato.etiqueta} title={dato.titulo}>
          {modoOperacion && <IconoUI nombre="reloj" size={28} className="kmeta-icono" />}
          <i>{dato.etiqueta}:</i><b>{dato.valor}</b>
        </span>)}
      </div>}
    </div>
  </>;
}
