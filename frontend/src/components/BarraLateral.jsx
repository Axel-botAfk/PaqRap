import React from 'react';
import IconoUI from './IconoUI.jsx';
import { SECCIONES, seccionActiva } from '../secciones.js';

/** Menú lateral. Se pliega con el burger: en el mapa recupera 224 px de ancho útil. */
export default function BarraLateral({ vista, escenario, alCambiar }) {
  const activa = seccionActiva(vista, escenario);
  return <aside className="side">
    <nav aria-label="Secciones principales">
      {SECCIONES.map(([clave, titulo, icono]) => <button key={clave} type="button"
        data-on={activa === clave ? '' : undefined} onClick={() => alCambiar(clave)}>
        <IconoUI nombre={icono} size={18} />
        <span>{titulo}</span></button>)}
    </nav>
  </aside>;
}
