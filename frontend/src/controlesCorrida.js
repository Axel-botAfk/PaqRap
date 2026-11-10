/** Acciones del motor; independientes de la reproducción local del historial. */
export function controlesDeCorrida(estado) {
  return {
    activa: ['PENDIENTE', 'EN_CURSO', 'PAUSADA'].includes(estado),
    pausar: estado === 'EN_CURSO',
    reanudar: estado === 'PAUSADA',
    cancelar: ['PENDIENTE', 'EN_CURSO', 'PAUSADA'].includes(estado)
  };
}
