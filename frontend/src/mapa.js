// Retícula del caso: nodos 0..70 y 0..50, con aristas de 1 km y sin diagonales.
export const ANCHO = 70;
export const ALTO = 50;
const COLUMNAS = ANCHO + 1;
const TOTAL = COLUMNAS * (ALTO + 1);
const DIRECCIONES = [[1, 0], [0, 1], [-1, 0], [0, -1]];

function valido(punto) {
  return punto && Number.isInteger(punto.x) && Number.isInteger(punto.y)
    && punto.x >= 0 && punto.x <= ANCHO && punto.y >= 0 && punto.y <= ALTO;
}

const indice = punto => punto.y * COLUMNAS + punto.x;

// Camino visual del plan vigente. Los bloqueos se evalúan en la instantánea mostrada;
// no representa el itinerario histórico ni sustituye la evaluación temporal del motor.
export function caminoEnGrilla(origen, destino, bloqueados = new Set()) {
  if (!valido(origen) || !valido(destino)) return [];
  const inicio = indice(origen);
  const fin = indice(destino);
  if (bloqueados.has(inicio) || bloqueados.has(fin)) return [];
  if (inicio === fin) return [origen];

  const anterior = new Int32Array(TOTAL).fill(-1);
  const cola = new Int32Array(TOTAL);
  let primero = 0;
  let ultimo = 0;
  cola[ultimo++] = inicio;
  anterior[inicio] = inicio;

  while (primero < ultimo && anterior[fin] === -1) {
    const actual = cola[primero++];
    const x = actual % COLUMNAS;
    const y = Math.floor(actual / COLUMNAS);
    for (const [dx, dy] of DIRECCIONES) {
      const siguiente = { x: x + dx, y: y + dy };
      if (!valido(siguiente)) continue;
      const posicion = indice(siguiente);
      if (bloqueados.has(posicion) || anterior[posicion] !== -1) continue;
      anterior[posicion] = actual;
      cola[ultimo++] = posicion;
    }
  }

  if (anterior[fin] === -1) return [];
  const camino = [];
  for (let actual = fin; actual !== inicio; actual = anterior[actual]) {
    camino.push({ x: actual % COLUMNAS, y: Math.floor(actual / COLUMNAS) });
  }
  camino.push(origen);
  return camino.reverse();
}

export function nodosBloqueados(bloqueos) {
  return new Set(bloqueos.flatMap(b => b.nodos || []).filter(valido).map(indice));
}

export function tramosDeRuta(ruta, bloqueados) {
  const destinos = (ruta.paradas || []).map(p => p.destino);
  const tramos = [];
  let origen = ruta.origen;
  for (const destino of destinos) {
    const tramo = caminoEnGrilla(origen, destino, bloqueados);
    if (tramo.length > 1) tramos.push(tramo);
    origen = destino;
  }
  return tramos;
}
