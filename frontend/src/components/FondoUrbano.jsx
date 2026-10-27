import React from 'react';
import { ANCHO, ALTO } from '../mapa.js';

// Las calles siguen los ejes de la red real: un nodo equivale a 10 unidades SVG.
// El dibujo de barrios, edificios y parques es puramente decorativo.
const PASO = 10;
const AVENIDAS_X = [
  [0, 6, 14, 21, 30, 38, 46, 54, 62, ANCHO],
  [0, 5, 13, 22, 29, 37, 45, 55, 63, ANCHO],
  [0, 7, 15, 23, 31, 40, 47, 56, 64, ANCHO],
  [0, 6, 14, 20, 28, 36, 44, 53, 61, ANCHO],
  [0, 8, 16, 24, 32, 39, 48, 57, 65, ANCHO],
  [0, 6, 13, 21, 30, 38, 46, 55, 63, ANCHO],
  [0, 7, 15, 22, 29, 37, 45, 54, 62, ANCHO]
];
const AVENIDAS_Y = [0, 6, 13, 20, 28, 35, 43, ALTO];

function variacion(x, y, semilla) {
  let valor = Math.imul(x + 1, 73856093) ^ Math.imul(y + 1, 19349663)
    ^ Math.imul(semilla + 1, 83492791);
  valor = Math.imul(valor ^ (valor >>> 13), 1274126177);
  return ((valor ^ (valor >>> 16)) >>> 0) / 4294967295;
}

const redondear = numero => Number(numero.toFixed(1));

function poligono(x, y, ancho, alto, corte) {
  const derecha = redondear(x + ancho);
  const abajo = redondear(y + alto);
  const esquina = redondear(corte);
  return `M${redondear(x + esquina)} ${redondear(y)}H${redondear(derecha - esquina)}`
    + `L${derecha} ${redondear(y + esquina)}V${redondear(abajo - esquina)}`
    + `L${redondear(derecha - esquina)} ${abajo}H${redondear(x + esquina)}`
    + `L${redondear(x)} ${redondear(abajo - esquina)}V${redondear(y + esquina)}Z`;
}

function edificio(x, y, ancho, alto) {
  return `M${redondear(x)} ${redondear(y)}h${redondear(ancho)}v${redondear(alto)}`
    + `h${redondear(-ancho)}Z`;
}

function arbol(x, y, radio) {
  const cx = redondear(x);
  const cy = redondear(y);
  const r = redondear(radio);
  return `M${redondear(cx - r)} ${cy}a${r} ${r} 0 1 0 ${redondear(r * 2)} 0`
    + `a${r} ${r} 0 1 0 ${redondear(-r * 2)} 0Z`;
}

function crearCiudad() {
  const barrios = [];
  const edificios = [];
  const parques = [];
  const arboles = [];
  const calles = [];
  const centrosAvenida = [];

  for (let fila = 0; fila < AVENIDAS_Y.length - 1; fila += 1) {
    const avenidas = AVENIDAS_X[fila];
    for (let columna = 0; columna < avenidas.length - 1; columna += 1) {
      const x = avenidas[columna] * PASO + 2.7;
      const y = AVENIDAS_Y[fila] * PASO + 2.7;
      const ancho = (avenidas[columna + 1] - avenidas[columna]) * PASO - 5.4;
      const alto = (AVENIDAS_Y[fila + 1] - AVENIDAS_Y[fila]) * PASO - 5.4;
      barrios.push(poligono(x, y, ancho, alto,
        1.2 + variacion(columna, fila, 0) * 1.6));

      // Los edificios no repiten la pauta de los nodos: varían de tamaño y posición
      // dentro de cada barrio, mientras las calles de la red quedan por encima.
      const cantidad = Math.round(ancho * alto / 185);
      for (let edificioIndice = 0; edificioIndice < cantidad; edificioIndice += 1) {
        const semilla = edificioIndice * 4;
        const bx = x + 3 + variacion(columna, fila, semilla + 1) * (ancho - 13);
        const by = y + 3 + variacion(columna, fila, semilla + 2) * (alto - 12);
        const bw = 3 + variacion(columna, fila, semilla + 3) * 7;
        const bh = 2.8 + variacion(columna, fila, semilla + 4) * 6;
        edificios.push(edificio(bx, by, bw, bh));
      }

      if (variacion(columna, fila, 7) < .24) {
        const px = x + 9 + variacion(columna, fila, 8) * Math.max(1, ancho - 33);
        const py = y + 8 + variacion(columna, fila, 9) * Math.max(1, alto - 29);
        parques.push(poligono(px, py, Math.min(19, ancho - 12),
          Math.min(16, alto - 11), 2.2));
        for (let arbolIndice = 0; arbolIndice < 4; arbolIndice += 1) {
          arboles.push(arbol(px + 3 + variacion(columna, fila, 30 + arbolIndice) * 12,
            py + 3 + variacion(columna, fila, 40 + arbolIndice) * 9, .55));
        }
      }
    }
  }

  // Cada arista de la retícula sigue estando insinuada; las avenidas resaltan sin
  // cambiar las coordenadas por las que se mueven rutas y vehículos.
  for (let x = 1; x < ANCHO; x += 1) calles.push(`M${x * PASO} 0V${ALTO * PASO}`);
  for (let y = 1; y < ALTO; y += 1) calles.push(`M0 ${y * PASO}H${ANCHO * PASO}`);
  for (let fila = 0; fila < AVENIDAS_X.length; fila += 1) {
    const inicioY = AVENIDAS_Y[fila] * PASO;
    const finY = AVENIDAS_Y[fila + 1] * PASO;
    for (const x of AVENIDAS_X[fila].slice(1, -1)) {
      centrosAvenida.push(`M${x * PASO} ${inicioY}Q${x * PASO + .5}`
        + ` ${redondear((inicioY + finY) / 2)} ${x * PASO} ${finY}`);
    }
  }
  for (const y of AVENIDAS_Y.slice(1, -1)) {
    centrosAvenida.push(`M0 ${y * PASO}Q175 ${y * PASO + .5} 350 ${y * PASO}`
      + `Q525 ${y * PASO - .5} ${ANCHO * PASO} ${y * PASO}`);
  }
  return {
    barrios: barrios.join(''),
    edificios: edificios.join(''),
    parques: parques.join(''),
    arboles: arboles.join(''),
    calles: calles.join(''),
    centrosAvenida: centrosAvenida.join('')
  };
}

const CIUDAD = crearCiudad();

function FondoUrbano() {
  return <g aria-hidden="true" pointerEvents="none">
    <defs>
      <pattern id="fondo-urbano-operacion" patternUnits="userSpaceOnUse"
        patternContentUnits="userSpaceOnUse" width={ANCHO * PASO} height={ALTO * PASO}>
        <rect width={ANCHO * PASO} height={ALTO * PASO} fill="#E2EBF7" />
        <path d={CIUDAD.barrios} fill="#F1F4F8" />
        <path d={CIUDAD.edificios} fill="#E9EEF5" />
        <path d={CIUDAD.parques} fill="#DCEFE1" />
        <path d={CIUDAD.arboles} fill="#C9E5D1" />
        <path d={CIUDAD.calles} fill="none" stroke="#E2EBF7" strokeWidth=".9" opacity=".42" />
        <path d={CIUDAD.centrosAvenida} fill="none" stroke="#E2EBF7" strokeWidth="5.4" />
        <path d={CIUDAD.centrosAvenida} fill="none" stroke="#F7F8FB"
          strokeWidth=".55" opacity=".65" />
      </pattern>
    </defs>
    <rect x="-1500" y="-1500" width="3700" height="3500"
      fill="url(#fondo-urbano-operacion)" />
  </g>;
}

export default React.memo(FondoUrbano);
