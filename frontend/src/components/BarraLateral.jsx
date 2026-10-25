import React from 'react';
import IconoUI from './IconoUI.jsx';

export const VISTAS = [
  ['pedidos', 'Pedidos'],
  ['averias', 'Averías de la flota'],
  ['operacion', 'Operación diaria'],
  ['simulacion', 'Simulación']
];

/** Menú lateral. Se pliega con el burger: en el mapa recupera 224 px de ancho útil. */
export default function BarraLateral({ vista, alCambiar, fuente }) {
  const esMapa = vista === 'operacion' || vista === 'simulacion';
  return <aside className="side">
    <nav aria-label="Secciones principales">
      {VISTAS.map(([clave, titulo]) => <button key={clave} type="button"
        data-on={vista === clave ? '' : undefined} onClick={() => alCambiar(clave)}>
        {!esMapa && <IconoUI nombre={{ pedidos: 'pedidos', averias: 'averias', operacion: 'calendario', simulacion: 'engranaje' }[clave]} size={18} />}
        <span>{titulo}</span></button>)}
    </nav>
  </aside>;
}
