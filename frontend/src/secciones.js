/**
 * Secciones del menú lateral: [clave, título, ícono]. «Simulación 5D» y «Colapso logístico» son
 * entradas propias del menú principal; ambas se muestran sobre la vista de mapa `simulacion`, y el
 * escenario decide cuál de las dos está activa (ver `seccionActiva`).
 */
export const SECCIONES = [
  ['operacion', 'Operación diaria', 'calendario'],
  ['simulacion5d', 'Simulación 5D', 'engranaje'],
  ['colapso', 'Colapso logístico', 'campana'],
  ['pedidos', 'Pedidos', 'pedidos'],
  ['flota', 'Mantenimiento de la flota', 'camion'],
  ['averias', 'Averías de la flota', 'averias']
];

/** Escenario de simulación que abre cada entrada del menú. */
export const ESCENARIO_DE_SECCION = { simulacion5d: 'PERIODO_5_DIAS', colapso: 'COLAPSO' };

/** Entrada resaltada del menú para la vista y el escenario en curso. */
export const seccionActiva = (vista, escenario) => vista === 'simulacion'
  ? (escenario === 'COLAPSO' ? 'colapso' : 'simulacion5d') : vista;

/** Título de cabecera de cada sección. */
export const TITULOS_SECCION = Object.fromEntries(SECCIONES.map(([clave, titulo]) => [clave, titulo]));
