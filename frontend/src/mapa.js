// Retícula del caso: nodos 0..70 y 0..50, con aristas de 1 km y sin diagonales.
export const ANCHO = 70;
export const ALTO = 50;
/** Proyección de los nodos de la API al lienzo SVG (origen visual arriba a la izquierda). */
export const xEnMapa = valor => Math.max(0, Math.min(ANCHO * 10, (valor ?? 0) * 10));
export const yEnMapa = valor => Math.max(0, Math.min(ALTO * 10, (ALTO - (valor ?? 0)) * 10));

/** El historial usa el corte elegido; en vivo nunca toma una instantánea futura. */
export function fotogramaObservadoEn(fotogramas, indice, ejecucion, enVivo, instante, aMs) {
  const tiempo = aMs(instante);
  if (!enVivo) return fotogramas[indice]
    ?? (ejecucion ? { reloj: ejecucion.reloj, vehiculos: ejecucion.vehiculos } : null);
  const corte = [...fotogramas].reverse().find(f => aMs(f.reloj) <= tiempo);
  const tiempoActual = aMs(ejecucion?.reloj);
  if (Number.isFinite(tiempoActual) && tiempoActual <= tiempo
      && (!corte || tiempoActual >= aMs(corte.reloj))) {
    return { reloj: ejecucion.reloj, vehiculos: ejecucion.vehiculos ?? [] };
  }
  return corte ?? null;
}

export function vehiculosObservadosEn(fotogramas, indice, ejecucion, enVivo, instante, aMs) {
  return fotogramaObservadoEn(fotogramas, indice, ejecucion, enVivo, instante, aMs)?.vehiculos ?? [];
}
const COLUMNAS = ANCHO + 1;
const TOTAL = COLUMNAS * (ALTO + 1);
// Igual orden de vecinos que EnrutadorBloqueos: izquierda, derecha, abajo, arriba.
const DIRECCIONES = [[-1, 0], [1, 0], [0, -1], [0, 1]];

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
      if (anterior[posicion] !== -1) continue;
      anterior[posicion] = actual;
      // Un nodo cerrado puede ser destino, pero no se atraviesa.
      if (!bloqueados.has(posicion)) cola[ultimo++] = posicion;
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

/** Última carga observada en un hito del trayecto, sin tomar eventos futuros. */
export function cargaEnTrayecto(preparado, instanteMs, cargaDelCorte) {
  let carga = cargaDelCorte;
  for (const paso of preparado?.pasos || []) {
    if (paso.ms > instanteMs) break;
    if (Number.isFinite(paso.cargaAbordo)) carga = paso.cargaAbordo;
  }
  return carga;
}

/**
 * Estado de cada pedido en el instante reproducido, deducido de los hitos del simulador.
 *
 * La API solo distingue PLANIFICADO y NO_ASIGNADO: «en ruta» y «entregado» no existen en
 * PedidoVista, así que se derivan aquí recorriendo todos los planes conocidos.
 *
 * Entregado: su hito ENTREGA (o FIN_ENTREGA) ya ocurrió. En ruta: su unidad ya pasó por
 * SALIDA y el pedido sigue pendiente. Hacen falta las dos fuentes del plan, porque los
 * trayectos solo cubren hasta el corte de su ventana: una unidad que sale a las 08:00 con
 * entregas a las 09:30 informa SALIDA, ALMACEN y CORTE y ninguna ENTREGA, y sus paradas
 * seguirían leyéndose como planificadas aunque ya vayan a bordo. Por eso las paradas de la
 * ruta de una unidad que ya salió cuentan como en ruta aunque su trayecto no las alcance.
 */
export function estadosDePedido(planes, instanteMs, aMs) {
  const estados = new Map();
  if (!Number.isFinite(instanteMs)) return estados;
  for (const plan of planes || []) {
    const salidas = new Map();
    for (const trayecto of plan.trayectos || []) {
      for (const paso of trayecto.pasos || []) {
        const ms = aMs(paso.instante);
        if (paso.hito === 'SALIDA' && !salidas.has(trayecto.vehiculoId)) {
          salidas.set(trayecto.vehiculoId, ms);
        }
        if (!paso.pedidoId || (paso.hito !== 'ENTREGA' && paso.hito !== 'FIN_ENTREGA')) continue;
        const salida = salidas.get(trayecto.vehiculoId);
        if (ms <= instanteMs) estados.set(paso.pedidoId, 'ENTREGADO');
        else if (salida !== undefined && salida <= instanteMs && !estados.has(paso.pedidoId)) {
          estados.set(paso.pedidoId, 'EN_RUTA');
        }
      }
    }
    for (const ruta of plan.rutas || []) {
      const salida = salidas.get(ruta.vehiculoId);
      if (salida === undefined || salida > instanteMs) continue;
      for (const parada of ruta.paradas || []) {
        if (!estados.has(parada.id)) estados.set(parada.id, 'EN_RUTA');
      }
    }
  }
  return estados;
}
