import { aMs } from './reloj.js';

/** Vigencia semiabierta, igual al motor: inicio incluido, fin excluido. */
export function bloqueosEn(bloqueos, instante) {
  const t = aMs(instante);
  if (!Number.isFinite(t)) return [];
  return (bloqueos || []).filter(b => aMs(b.inicio) <= t && t < aMs(b.fin));
}
