/* Formato de fechas y números para toda la interfaz.
   Regla de 25.dis.gui.v2 seccion 8: cuando no hay dato se muestra «Sin datos»,
   nunca 0 ni 100 %. En celdas estrechas se usa SIN_DATO_CORTO. */

export const SIN_DATO = 'Sin datos';
export const SIN_DATO_CORTO = '—';

const FECHA_HORA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
});

const SOLO_HORA = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' });

export function fecha(valor, ausente = SIN_DATO_CORTO) {
  if (!valor) return ausente;
  const momento = new Date(valor);
  return Number.isNaN(momento.getTime()) ? valor : FECHA_HORA.format(momento);
}

export function hora(valor, ausente = SIN_DATO_CORTO) {
  if (!valor) return ausente;
  const momento = new Date(valor);
  return Number.isNaN(momento.getTime()) ? valor : SOLO_HORA.format(momento);
}

export function numero(valor, ausente = SIN_DATO_CORTO) {
  return typeof valor === 'number' ? new Intl.NumberFormat('es-PE').format(valor) : ausente;
}

export function decimal(valor, unidad = '', ausente = SIN_DATO_CORTO) {
  return typeof valor === 'number'
    ? `${new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 }).format(valor)}${unidad}`
    : ausente;
}

/** Segundos transcurridos entre el inicio y la última instantánea, como «1 h 04 m». */
export function duracion(desde, hasta) {
  if (!desde || !hasta) return SIN_DATO_CORTO;
  const ms = new Date(hasta).getTime() - new Date(desde).getTime();
  if (!Number.isFinite(ms) || ms < 0) return SIN_DATO_CORTO;
  const minutos = Math.floor(ms / 60000);
  const horas = Math.floor(minutos / 60);
  return horas > 0 ? `${horas} h ${String(minutos % 60).padStart(2, '0')} m` : `${minutos} m`;
}
