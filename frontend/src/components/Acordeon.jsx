import React, { useId, useState } from 'react';

/**
 * Bloque plegable del riel. El prototipo apila mucha información a la derecha del mapa; que
 * cada bloque se cierre es lo que deja el mapa como superficie principal.
 */
export default function Acordeon({ titulo, abierto: inicial = true, nota = null, children }) {
  const [abierto, setAbierto] = useState(inicial);
  const id = useId();
  return <section className={`acc ${abierto ? 'acc-abierto' : ''}`}>
    <h3>
      <button type="button" aria-expanded={abierto} aria-controls={id}
        onClick={() => setAbierto(!abierto)}>
        <span>{titulo}</span>
        {nota && <em>{nota}</em>}
        <i aria-hidden="true" />
      </button>
    </h3>
    <div id={id} className="acc-cuerpo" hidden={!abierto}>{children}</div>
  </section>;
}
