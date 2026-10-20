// Reloj del visualizador anclado al tiempo real. El servidor publica una ventana cada Sa
// segundos y dice cuándo la publicó; el instante mostrado sale de cuánto tiempo real pasó
// desde entonces, no de sumar un paso por cada tick. Así no se adelanta ni se atrasa aunque
// el navegador dibuje más o menos cuadros.

/** Lo máximo que avanza el reloj mostrado en un cuadro; evita el salto al despertar la pestaña. */
export const TOPE_CUADRO_MS = 250;

/** Fechas LocalDateTime del backend (sin zona) a milisegundos, tratándolas como UTC. */
export function aMs(iso) {
  if (!iso) return NaN;
  return Date.parse(iso.length === 16 ? `${iso}:00Z` : `${iso}Z`);
}

/** Milisegundos a cadena LocalDateTime comparable con las del backend. */
export function aIso(ms) {
  return new Date(ms).toISOString().slice(0, 19);
}

/** Diferencia entre el reloj del navegador y el del servidor al recibir una vista. */
export function desfaseConServidor(ritmo, recibidoMs) {
  return ritmo && Number.isFinite(ritmo.servidorAhoraMs) ? recibidoMs - ritmo.servidorAhoraMs : 0;
}

/** Cuánto de la ventana visible corresponde haber reproducido ahora, entre 0 y Sa. */
export function transcurridoObjetivo(ritmo, ahoraMs, desfaseMs = 0) {
  if (ritmo.transcurridoAlPausarMs != null) return ritmo.transcurridoAlPausarMs;
  const ahoraEnServidor = ahoraMs - desfaseMs;
  return Math.max(0, Math.min(ritmo.saMs, ahoraEnServidor - ritmo.publicadoMs));
}

/** Instante simulado: inicio de la ventana más lo reproducido por K, sin pasar del corte. */
export function instanteSimulado(ritmo, transcurridoMs) {
  const desde = aMs(ritmo.relojDesde);
  const hasta = aMs(ritmo.relojHasta);
  if (!Number.isFinite(desde) || !Number.isFinite(hasta) || hasta <= desde) {
    return Number.isFinite(hasta) ? hasta : desde;
  }
  return Math.min(hasta, desde + transcurridoMs * ritmo.k);
}

/**
 * Reloj con memoria del último cuadro. Cada llamada devuelve el instante simulado (ms) a mostrar.
 *
 * El avance por cuadro se limita a TOPE_CUADRO_MS: si la pestaña estuvo dormida, el reloj
 * alcanza al objetivo en pocos cuadros en lugar de saltar. Al llegar una ventana nueva arranca
 * desde su inicio; al abrir o recargar la página se ubica directamente donde va la corrida.
 */
export function crearRelojAnclado(tope = TOPE_CUADRO_MS) {
  let ventana = null;
  let mostrado = 0;
  return (ritmo, ahoraMs, desfaseMs = 0) => {
    if (!ritmo) return null;
    const objetivo = transcurridoObjetivo(ritmo, ahoraMs, desfaseMs);
    if (ventana === null) {
      mostrado = objetivo;
    } else if (ritmo.ventana !== ventana) {
      mostrado = Math.min(objetivo, tope);
    } else {
      mostrado = objetivo < mostrado ? objetivo : Math.min(objetivo, mostrado + tope);
    }
    ventana = ritmo.ventana;
    return instanteSimulado(ritmo, mostrado);
  };
}
