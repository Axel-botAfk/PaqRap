import { aMs } from './reloj.js';

// El último corte observado, sin mostrar el inventario de un instante futuro.
export function inventarioEn(fotogramas, inventarioActual, instante) {
  const momento = aMs(instante);
  if (!Number.isFinite(momento)) return null;
  let ultimo = null;
  for (const corte of [...fotogramas.map(fotograma => fotograma.inventario), inventarioActual]) {
    const tiempo = aMs(corte?.reloj);
    if (Number.isFinite(tiempo) && tiempo <= momento
        && (!ultimo || tiempo >= aMs(ultimo.reloj))) ultimo = corte;
  }
  return ultimo;
}

// Los mismos cortes visuales que usa la barra de inventario del panel derecho.
export function estadoDeStock(stock, inicial) {
  if (stock?.ilimitado) return 'suficiente';
  if (!Number.isFinite(stock?.disponible) || stock.disponible < 0) return 'sin-datos';
  if (stock.disponible === 0) return 'vacio';
  if (!Number.isFinite(inicial) || inicial <= 0) return 'sin-datos';
  const porcentaje = Math.round((stock.disponible / inicial) * 100);
  if (porcentaje < 20) return 'critico';
  if (porcentaje < 60) return 'advertencia';
  return 'suficiente';
}

export const ETIQUETAS_STOCK = {
  suficiente: 'Stock suficiente',
  advertencia: 'Stock en advertencia',
  critico: 'Stock crítico',
  vacio: 'Almacén vacío',
  'sin-datos': 'Stock no disponibles para este instante'
};
