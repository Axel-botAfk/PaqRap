import React from 'react';

/**
 * Riel derecho sobre el mapa. Se pliega entero y deja en su lugar una pestaña para volver a
 * abrirlo, igual que el prototipo: el mapa gana 326 px sin perder el acceso a los controles.
 */
export default function Riel({ titulo, abierto, alAlternar, children }) {
  if (!abierto) {
    return <button type="button" className="riel-pestania" onClick={alAlternar}
      aria-expanded="false" title={`Mostrar ${titulo.toLowerCase()}`}>
      {titulo} <i aria-hidden="true" />
    </button>;
  }
  return <aside className="riel" aria-label={titulo}>
    <header>
      <h2>{titulo}</h2>
      <button type="button" className="x" onClick={alAlternar}
        aria-expanded="true" aria-label={`Ocultar ${titulo.toLowerCase()}`}>×</button>
    </header>
    <div className="riel-cuerpo">{children}</div>
  </aside>;
}
