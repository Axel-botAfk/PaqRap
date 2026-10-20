import React from 'react';
import { ETIQUETAS } from '../formato.js';

/**
 * Barra superior fija y, debajo, la franja de indicadores. Ocupan juntas 104 px (--hdr):
 * todo lo demás, el mapa incluido, se cuelga de esa altura.
 */
export default function BarraSuperior({ titulo, menuAbierto, alAlternarMenu, ejecucion,
  chips = [], meta = [] }) {
  return <>
    <div className="top">
      <div className="marca-simbolo">PQR</div>
      <div className="wordmark">PaqRap</div>
      <button type="button" className="burger" onClick={alAlternarMenu}
        aria-expanded={menuAbierto} aria-label="Mostrar u ocultar el menú"><i /></button>
      <h1>{titulo}</h1>
      <span className={`estado-ejecucion ${ejecucion?.estado || ''}`}>
        {ejecucion ? ETIQUETAS[ejecucion.estado] : 'Sin corrida'}
      </span>
    </div>
    <div className="kbar">
      {chips.length > 0 && <div className="chips">
        {chips.map(chip => <div key={chip.etiqueta} className="chip"
          data-alerta={chip.alerta ? '' : undefined} title={chip.titulo}>
          <span>{chip.etiqueta}</span><b>{chip.valor}</b>
        </div>)}
      </div>}
      {meta.length > 0 && <div className="kmeta">
        {meta.map(dato => <span key={dato.etiqueta} title={dato.titulo}>
          <i>{dato.etiqueta}:</i><b>{dato.valor}</b>
        </span>)}
      </div>}
    </div>
  </>;
}
