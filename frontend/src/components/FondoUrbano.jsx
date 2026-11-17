import React from 'react';
import { ANCHO, ALTO } from '../mapa.js';

const PASO = 10;
const LIENZO_ANCHO = ANCHO * PASO;
const LIENZO_ALTO = ALTO * PASO;
const DESDE_X = -ANCHO;
const DESDE_Y = -ALTO;

function FondoUrbano() {
  return <g aria-hidden="true" pointerEvents="none">
    <rect x={-LIENZO_ANCHO} y={-LIENZO_ALTO} width={LIENZO_ANCHO * 3}
      height={LIENZO_ALTO * 3} fill="#F7F8FB" />
    {Array.from({ length: ANCHO * 3 + 1 }, (_, n) => n + DESDE_X).map(i => <line key={`v${i}`}
      x1={i * PASO} x2={i * PASO} y1={-LIENZO_ALTO} y2={LIENZO_ALTO * 2}
      className={`grid-line ${i % 10 === 0 ? 'grid-major' : ''}`} />)}
    {Array.from({ length: ALTO * 3 + 1 }, (_, n) => n + DESDE_Y).map(i => <line key={`h${i}`}
      x1={-LIENZO_ANCHO} x2={LIENZO_ANCHO * 2} y1={i * PASO} y2={i * PASO}
      className={`grid-line ${i % 10 === 0 ? 'grid-major' : ''}`} />)}
  </g>;
}

export default React.memo(FondoUrbano);
