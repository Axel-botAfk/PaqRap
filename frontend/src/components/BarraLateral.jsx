import React from 'react';

export const VISTAS = [
  ['pedidos', 'Pedidos'],
  ['averias', 'Averías de la flota'],
  ['operacion', 'Operación diaria'],
  ['simulacion', 'Simulación']
];

/** Menú lateral. Se pliega con el burger: en el mapa recupera 224 px de ancho útil. */
export default function BarraLateral({ vista, alCambiar, fuente }) {
  return <aside className="side">
    <nav aria-label="Secciones principales">
      {VISTAS.map(([clave, titulo]) => <button key={clave} type="button"
        data-on={vista === clave ? '' : undefined} onClick={() => alCambiar(clave)}>{titulo}</button>)}
    </nav>
    <footer>
      <strong>{fuente === 'MYSQL' ? 'Datos en MySQL' : 'Datos desde archivos'}</strong>
      <span>Ciudad reticulada 70 × 50 km. Las corridas viven en memoria del servidor: al
        recargar se retoma la corrida, pero si el servidor se reinicia hay que iniciar otra.</span>
    </footer>
  </aside>;
}
