import { aMs } from './reloj.js';

/**
 * Las averías vistas desde el mapa (D10 a D13).
 *
 * El caso define dos tiempos por tipo y conviene no confundirlos: la *permanencia en el lugar*
 * —la ventana en que la unidad sigue donde se averió y se le puede trasvasar la carga— y el
 * *reingreso*, que depende de turnos y lo resuelve el backend. El mapa solo necesita el primero:
 * mientras dura, la avería se dibuja en el nodo; después la unidad queda fuera de servicio y se
 * marca atenuada mientras el corte la siga informando como AVERIADO.
 */
export const PERMANENCIA_HORAS = { TIPO_1: 2, TIPO_2: 4, TIPO_3: 4 };

export const ETIQUETAS_AVERIA = {
  TIPO_1: 'Avería tipo 1 · reparación en sitio (2 h)',
  TIPO_2: 'Avería tipo 2 · vuelve al central (4 h en el lugar)',
  TIPO_3: 'Avería tipo 3 · mantenimiento prolongado (4 h en el lugar)'
};

const HORA_MS = 3600000;

/**
 * La avería que corresponde mostrar para cada unidad en el instante dado: la última registrada
 * hasta ese momento, con la fase en que está. Sin instante válido no se afirma ninguna.
 */
export function averiasEn(averias, instante) {
  const momento = aMs(instante);
  const vigentes = new Map();
  if (!Number.isFinite(momento)) return vigentes;
  for (const averia of averias || []) {
    const inicio = aMs(averia?.instante);
    if (!averia?.unidadId || !Number.isFinite(inicio) || inicio > momento) continue;
    const permanencia = PERMANENCIA_HORAS[averia.tipo];
    if (!Number.isFinite(permanencia)) continue;
    const fin = inicio + permanencia * HORA_MS;
    const anterior = vigentes.get(averia.unidadId);
    if (anterior && anterior.inicio >= inicio) continue;
    vigentes.set(averia.unidadId, { tipo: averia.tipo, inicio, fin,
      fase: momento < fin ? 'en-sitio' : 'fuera' });
  }
  return vigentes;
}

/** Lo que se dibuja sobre el ícono: solo los tipos con permanencia en curso en el instante. */
export function averiaDeGrupo(grupo, vigentes) {
  for (const vehiculo of grupo) {
    const averia = vigentes.get(vehiculo?.id);
    if (averia?.fase === 'en-sitio') return averia;
  }
  return null;
}
