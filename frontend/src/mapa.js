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

/** Última versión del plan conocida al instante reproducido (cadenas ISO locales). */
export function planVigenteEn(planes, instante) {
  if (!instante) return null;
  let vigente = null;
  for (const plan of planes) {
    if (plan.reloj > instante) break;
    vigente = plan;
  }
  return vigente;
}

// Transición visual entre dos posiciones observadas. Los nodos intermedios son
// esquemáticos; el servidor solo confirma los extremos al cierre de cada salto.
export function posicionVisual(origen, destino, progreso, bloqueados = new Set()) {
  if (!valido(destino)) return origen;
  if (!valido(origen)) return destino;
  return posicionEnCamino(caminoEnGrilla(origen, destino, bloqueados),
    origen, destino, progreso);
}

export function posicionEnCamino(camino, origen, destino, progreso) {
  if (!camino.length) return progreso >= 1 ? destino : origen;
  const paso = Math.min(camino.length - 1,
    Math.floor(Math.max(0, Math.min(1, progreso)) * (camino.length - 1)));
  return camino[paso];
}

// Recorridos de la ventana (hitos con hora, informados por el simulador) listos para
// interpolar: el camino de cada tramo se calcula una vez, no en cada cuadro.
export function prepararTrayectos(trayectos, aMs, bloqueados = new Set()) {
  return new Map((trayectos || []).map(trayecto => {
    const pasos = trayecto.pasos.map(paso => ({ ...paso, ms: aMs(paso.instante) }));
    const tramos = [];
    for (let i = 1; i < pasos.length; i++) {
      const origen = pasos[i - 1];
      const destino = pasos[i];
      tramos.push({ origen, destino,
        camino: caminoEnGrilla(origen.ubicacion, destino.ubicacion, bloqueados) });
    }
    return [trayecto.vehiculoId, { pasos, tramos }];
  }));
}

/**
 * Trazo de cada ruta en vivo, hecho con los mismos caminos por los que se mueve la unidad: lo
 * que recorre en la ventana y, desde donde queda, las paradas que le faltan. Así la unidad va
 * siempre encima de su línea. Las rutas de una unidad sin recorrido se trazan desde su almacén.
 */
export function trazosConRecorrido(rutas, preparados, bloqueados = new Set()) {
  const yaTrazadas = new Set();
  return rutas.map(ruta => {
    const preparado = preparados?.get(ruta.vehiculoId);
    if (!preparado?.pasos.length) return { ruta, tramos: tramosDeRuta(ruta, bloqueados) };
    const entregados = new Set(preparado.pasos
      .filter(paso => paso.hito === 'ENTREGA' && paso.pedidoId).map(paso => paso.pedidoId));
    const paradas = ruta.paradas || [];
    const primeraDeLaUnidad = !yaTrazadas.has(ruta.vehiculoId);
    yaTrazadas.add(ruta.vehiculoId);
    const tramos = primeraDeLaUnidad
      ? preparado.tramos.map(tramo => tramo.camino).filter(camino => camino.length > 1) : [];
    const enCurso = primeraDeLaUnidad || paradas.some(parada => entregados.has(parada.id));
    let origen = enCurso ? preparado.pasos[preparado.pasos.length - 1].ubicacion : ruta.origen;
    for (const parada of paradas) {
      if (entregados.has(parada.id)) continue;
      const tramo = caminoEnGrilla(origen, parada.destino, bloqueados);
      if (tramo.length > 1) tramos.push(tramo);
      origen = parada.destino;
    }
    return { ruta, tramos };
  });
}

/** Dónde está la unidad en el instante dado según sus hitos; null si no tiene recorrido. */
export function posicionEnTrayecto(preparado, instanteMs) {
  if (!preparado?.pasos.length) return null;
  const { pasos, tramos } = preparado;
  if (!(instanteMs > pasos[0].ms)) return pasos[0].ubicacion;
  for (const { origen, destino, camino } of tramos) {
    if (instanteMs < destino.ms) {
      const duracion = destino.ms - origen.ms;
      const progreso = duracion > 0 ? (instanteMs - origen.ms) / duracion : 1;
      return posicionEnCamino(camino, origen.ubicacion, destino.ubicacion, progreso);
    }
  }
  return pasos[pasos.length - 1].ubicacion;
}
