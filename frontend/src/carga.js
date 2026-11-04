import { UMBRALES_POR_OMISION } from './umbrales.js';

/**
 * Semáforo de carga (C27). Los cortes llegan desde la configuración del sistema (B06); los de
 * omisión son los que fijó el caso: vacío en 0, baja bajo 40 % y media bajo 70 %.
 */
export function estadoDeCarga(vehiculo, umbrales = UMBRALES_POR_OMISION.carga) {
  const carga = vehiculo?.cargaAbordo;
  const capacidad = vehiculo?.capacidad;
  if (!Number.isFinite(carga) || !Number.isFinite(capacidad) || capacidad <= 0
      || carga < 0 || carga > capacidad) return 'sin-datos';
  if (carga === 0) return 'vacio';
  if (carga * 100 < capacidad * umbrales.baja) return 'baja';
  if (carga * 100 < capacidad * umbrales.intermedia) return 'intermedia';
  return 'alta';
}

export function estadoDeGrupo(grupo, umbrales = UMBRALES_POR_OMISION.carga) {
  const estados = grupo.map(vehiculo => estadoDeCarga(vehiculo, umbrales));
  return estados.every(estado => estado === estados[0]) ? estados[0] : 'mixto';
}

/** Las etiquetas dicen los cortes vigentes: si se configuran otros, el texto los sigue. */
export function etiquetasDeCarga(umbrales = UMBRALES_POR_OMISION.carga) {
  return {
    vacio: 'Carga vacía',
    baja: `Carga baja (menos de ${umbrales.baja} %)`,
    intermedia: `Carga media (desde ${umbrales.baja} % hasta menos de ${umbrales.intermedia} %)`,
    alta: `Carga alta (desde ${umbrales.intermedia} % hasta 100 %)`,
    'sin-datos': 'Carga no disponible',
    mixto: 'Unidades con distintos niveles de carga'
  };
}

export const ETIQUETAS_CARGA = etiquetasDeCarga();

/** Niveles ofrecidos por el filtro del mapa (D06), en el orden del semáforo. */
export const NIVELES_CARGA = [['alta', 'Alta'], ['intermedia', 'Media'], ['baja', 'Baja'],
  ['vacio', 'Vacía']];
