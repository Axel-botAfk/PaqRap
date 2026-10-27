/** Umbrales de carga/capacidad definidos para C27: 0, 40 y 70 %. */
export function estadoDeCarga(vehiculo) {
  const carga = vehiculo?.cargaAbordo;
  const capacidad = vehiculo?.capacidad;
  if (!Number.isFinite(carga) || !Number.isFinite(capacidad) || capacidad <= 0
      || carga < 0 || carga > capacidad) return 'sin-datos';
  if (carga === 0) return 'vacio';
  if (carga * 100 < capacidad * 40) return 'baja';
  if (carga * 100 < capacidad * 70) return 'intermedia';
  return 'alta';
}

export function estadoDeGrupo(grupo) {
  const estados = grupo.map(estadoDeCarga);
  return estados.every(estado => estado === estados[0]) ? estados[0] : 'mixto';
}

export const ETIQUETAS_CARGA = {
  vacio: 'Carga vacía',
  baja: 'Carga baja (menos de 40 %)',
  intermedia: 'Carga media (desde 40 % hasta menos de 70 %)',
  alta: 'Carga alta (desde 70 % hasta 100 %)',
  'sin-datos': 'Carga no disponible',
  mixto: 'Unidades con distintos niveles de carga'
};
