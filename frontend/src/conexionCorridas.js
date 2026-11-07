const ESTADOS_ACTIVOS = new Set(['PENDIENTE', 'EN_CURSO', 'PAUSADA']);

export function corridaRecuperable(corrida, escenario) {
  return corrida?.escenario === escenario && ESTADOS_ACTIVOS.has(corrida.estado);
}

export function unicaCorridaActiva(corridas, escenario) {
  return corridas?.length === 1 && corridaRecuperable(corridas[0], escenario)
    ? corridas[0] : null;
}
