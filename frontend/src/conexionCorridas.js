const ESTADOS_ACTIVOS = new Set(['PENDIENTE', 'EN_CURSO', 'PAUSADA']);

export const esActiva = corrida => ESTADOS_ACTIVOS.has(corrida?.estado);

export function corridaRecuperable(corrida, escenario) {
  return corrida?.escenario === escenario && esActiva(corrida);
}

export function unicaCorridaActiva(corridas, escenario) {
  return corridas?.length === 1 && corridaRecuperable(corridas[0], escenario)
    ? corridas[0] : null;
}

/**
 * Qué hacer con las corridas activas que informa el servidor, vistas desde un dispositivo que
 * todavía no sigue ninguna (G06: varios visualizadores para un único planificador).
 *
 * Quien abre la página en otro dispositivo no sabe qué escenario se inició en el primero, así
 * que se miran las corridas de todos los escenarios y no solo las del que tiene seleccionado:
 *
 * - una sola del escenario seleccionado: se adopta sin preguntar;
 * - ninguna del seleccionado pero una sola en otro escenario: se adopta solo si la persona
 *   acaba de abrir la página (`recienAbierto`); si ya eligió una vista a mano no se la cambia,
 *   y la corrida se ofrece en la lista;
 * - en cualquier otro caso se listan todas para elegir.
 */
export function decidirCorrida(corridas, escenario, recienAbierto) {
  const activas = (corridas || []).filter(esActiva);
  const delEscenario = activas.filter(corrida => corrida.escenario === escenario);
  if (delEscenario.length === 1) return { adoptar: delEscenario[0], ofrecer: [] };
  if (!delEscenario.length && activas.length === 1 && recienAbierto) {
    return { adoptar: activas[0], ofrecer: [] };
  }
  return { adoptar: null, ofrecer: activas };
}
