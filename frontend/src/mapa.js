// Retícula del caso: nodos 0..70 y 0..50, con aristas de 1 km y sin diagonales.
export const ANCHO = 70;
export const ALTO = 50;
/** Proyección de los nodos de la API al lienzo SVG (origen visual arriba a la izquierda). */
export const xEnMapa = valor => Math.max(0, Math.min(ANCHO * 10, (valor ?? 0) * 10));
export const yEnMapa = valor => Math.max(0, Math.min(ALTO * 10, (ALTO - (valor ?? 0)) * 10));

/** Los pictogramas miran originalmente a la derecha; el eje Y del SVG va hacia abajo. */
export function anguloMovimiento(origen, destino) {
  if (!origen || !destino) return null;
  const dx = destino.x - origen.x;
  const dy = origen.y - destino.y;
  if (!Number.isFinite(dx) || !Number.isFinite(dy) || (dx === 0 && dy === 0)) return null;
  return Math.atan2(dy, dx) * 180 / Math.PI;
}

export const giroMasCorto = (desde, hasta) => ((hasta - desde + 540) % 360) - 180;

/** Orientación cronológica en los cortes de la API, independiente del sentido de reproducción. */
export function orientacionEnFotogramas(fotogramas, indiceActual, vehiculoId) {
  const actual = fotogramas[indiceActual]?.vehiculos?.find(v => v.id === vehiculoId)?.ubicacion;
  if (!actual) return null;
  for (let i = indiceActual - 1; i >= 0; i--) {
    const anterior = fotogramas[i]?.vehiculos?.find(v => v.id === vehiculoId)?.ubicacion;
    const angulo = anguloMovimiento(anterior, actual);
    if (angulo !== null) return angulo;
  }
  return null;
}

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

/**
 * Misma marcha que `posicionEnCamino`, pero sin saltar de nodo en nodo: entre dos nodos
 * consecutivos la posición se reparte de forma lineal. Siempre cae sobre una arista del camino
 * (una coordenada entera) y coincide con el nodo exacto cuando el progreso lo alcanza. Es para
 * dibujar; la lógica de rutas, tooltips y trazos sigue usando el nodo entero.
 */
export function posicionContinuaEnCamino(camino, origen, destino, progreso) {
  if (camino.length < 2) return posicionEnCamino(camino, origen, destino, progreso);
  const recorrido = Math.max(0, Math.min(1, progreso)) * (camino.length - 1);
  const arista = Math.min(camino.length - 2, Math.floor(recorrido));
  const fraccion = recorrido - arista;
  const desde = camino[arista];
  const hasta = camino[arista + 1];
  return { x: desde.x + (hasta.x - desde.x) * fraccion, y: desde.y + (hasta.y - desde.y) * fraccion };
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
  const porVehiculo = new Map();
  for (const ruta of rutas) {
    if (!porVehiculo.has(ruta.vehiculoId)) porVehiculo.set(ruta.vehiculoId, []);
    porVehiculo.get(ruta.vehiculoId).push(ruta);
  }
  const tramosObservados = new Map(rutas.map(ruta => [ruta.id, []]));
  for (const [vehiculoId, rutasDeUnidad] of porVehiculo) {
    const preparado = preparados?.get(vehiculoId);
    if (!preparado) continue;
    const rutaDePedido = new Map(rutasDeUnidad.flatMap(ruta =>
      (ruta.paradas || []).map(parada => [parada.id, ruta])));
    const entregas = preparado.tramos.flatMap((tramo, indice) =>
      tramo.destino.hito === 'ENTREGA' && rutaDePedido.has(tramo.destino.pedidoId)
        ? [{ indice, ruta: rutaDePedido.get(tramo.destino.pedidoId) }] : []);
    let ultimaRuta = rutasDeUnidad[0];
    for (let indice = 0; indice < preparado.tramos.length; indice++) {
      const tramo = preparado.tramos[indice];
      const ruta = entregas.find(entrega => entrega.indice >= indice)?.ruta || ultimaRuta;
      ultimaRuta = ruta;
      if (tramo.camino.length > 1) tramosObservados.get(ruta.id).push({
        camino: tramo.camino, origenMs: tramo.origen.ms, destinoMs: tramo.destino.ms
      });
    }
  }
  return rutas.map(ruta => {
    const preparado = preparados?.get(ruta.vehiculoId);
    if (!preparado?.pasos.length) {
      const tramos = tramosDeRuta(ruta, bloqueados);
      return { ruta, tramos, detalles: tramos.map(camino => ({ camino })) };
    }
    const entregados = new Set(preparado.pasos
      .filter(paso => paso.hito === 'ENTREGA' && paso.pedidoId).map(paso => paso.pedidoId));
    const paradas = ruta.paradas || [];
    const detalles = [...tramosObservados.get(ruta.id)];
    let origen = detalles.at(-1)?.camino.at(-1) || ruta.origen;
    for (const parada of paradas) {
      if (entregados.has(parada.id)) continue;
      const tramo = caminoEnGrilla(origen, parada.destino, bloqueados);
      if (tramo.length > 1) detalles.push({ camino: tramo });
      origen = parada.destino;
    }
    return { ruta, tramos: detalles.map(detalle => detalle.camino), detalles };
  });
}

/**
 * Divide una polilínea en lo ya recorrido y lo pendiente. El corte cae exactamente donde se dibuja
 * la unidad (la misma cuenta que `posicionContinuaEnCamino`), no en el último nodo que pasó: así lo
 * pendiente arranca bajo el vehículo y no una celda atrás.
 */
export function segmentosEnInstante(detalle, instanteMs) {
  const { camino, origenMs, destinoMs } = detalle;
  if (!Number.isFinite(origenMs) || !Number.isFinite(destinoMs)
      || !Number.isFinite(instanteMs) || instanteMs < origenMs
      || (instanteMs === origenMs && destinoMs > origenMs)) {
    return [{ camino, recorrido: false }];
  }
  const duracion = destinoMs - origenMs;
  const progreso = duracion > 0 ? Math.min(1, (instanteMs - origenMs) / duracion) : 1;
  if (progreso >= 1 || camino.length < 2) return [{ camino, recorrido: true }];
  const avance = Math.max(0, progreso) * (camino.length - 1);
  if (avance <= 0) return [{ camino, recorrido: false }];
  const arista = Math.min(camino.length - 2, Math.floor(avance));
  const fraccion = avance - arista;
  if (fraccion === 0) {
    return [{ camino: camino.slice(0, arista + 1), recorrido: true },
      { camino: camino.slice(arista), recorrido: false }];
  }
  const desde = camino[arista];
  const hasta = camino[arista + 1];
  const punto = { x: desde.x + (hasta.x - desde.x) * fraccion, y: desde.y + (hasta.y - desde.y) * fraccion };
  // `desfase` (km desde el último nodo) permite anclar el patrón discontinuo a la calle: sin él,
  // las rayas avanzarían pegadas a la unidad.
  return [{ camino: [...camino.slice(0, arista + 1), punto], recorrido: true },
    { camino: [punto, ...camino.slice(arista + 1)], recorrido: false,
      desfase: Math.hypot(punto.x - desde.x, punto.y - desde.y) }];
}

/** La ruta termina al concluir cada entrega; algunas no tienen tiempo de acondicionamiento. */
export function rutaFinalizadaEn(ruta, preparado, instanteMs) {
  if (!ruta.paradas?.length || !Number.isFinite(instanteMs)) return false;
  const pasos = preparado?.pasos || [];
  return ruta.paradas.every(parada => {
    const fin = pasos.find(paso => paso.hito === 'FIN_ENTREGA' && paso.pedidoId === parada.id);
    const entrega = fin || pasos.find(paso => paso.hito === 'ENTREGA' && paso.pedidoId === parada.id);
    return entrega && entrega.ms <= instanteMs;
  });
}

/**
 * Posición de la unidad en el instante dado según sus hitos; null si no tiene recorrido.
 * `colocar` decide cómo ubicarla sobre el camino del tramo vigente (por nodo o continua).
 */
function posicionSegunTrayecto(preparado, instanteMs, colocar) {
  if (!preparado?.pasos.length) return null;
  const { pasos, tramos } = preparado;
  if (!(instanteMs > pasos[0].ms)) return pasos[0].ubicacion;
  for (const { origen, destino, camino } of tramos) {
    if (instanteMs < destino.ms) {
      const duracion = destino.ms - origen.ms;
      const progreso = duracion > 0 ? (instanteMs - origen.ms) / duracion : 1;
      return colocar(camino, origen.ubicacion, destino.ubicacion, progreso);
    }
  }
  return pasos[pasos.length - 1].ubicacion;
}

/** Dónde está la unidad en el instante dado según sus hitos, en el nodo que le corresponde. */
export const posicionEnTrayecto = (preparado, instanteMs) =>
  posicionSegunTrayecto(preparado, instanteMs, posicionEnCamino);

/** Lo mismo, pero deslizándose entre nodos: la posición con la que se dibuja la unidad. */
export const posicionContinuaEnTrayecto = (preparado, instanteMs) =>
  posicionSegunTrayecto(preparado, instanteMs, posicionContinuaEnCamino);

/** Dirección de la arista recorrida en el instante; las esperas conservan la última válida. */
export function orientacionEnTrayecto(preparado, instanteMs) {
  if (!preparado?.pasos.length || !Number.isFinite(instanteMs)) return null;
  if (instanteMs <= preparado.pasos[0].ms) return null;
  let ultima = null;
  for (const { origen, destino, camino } of preparado.tramos) {
    if (instanteMs < origen.ms) break;
    if (camino.length < 2) continue;
    if (instanteMs >= destino.ms) {
      ultima = anguloMovimiento(camino[camino.length - 2], camino.at(-1)) ?? ultima;
      continue;
    }
    if (instanteMs === origen.ms) return ultima;
    const duracion = destino.ms - origen.ms;
    const progreso = duracion > 0 ? Math.max(0, (instanteMs - origen.ms) / duracion) : 1;
    const paso = Math.min(camino.length - 2, Math.floor(progreso * (camino.length - 1)));
    return anguloMovimiento(camino[paso], camino[paso + 1]) ?? ultima;
  }
  return ultima;
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
    if (plan.reloj && aMs(plan.reloj) > instanteMs) continue;
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
