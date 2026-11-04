import { aMs } from './reloj.js';
import { UMBRALES_POR_OMISION } from './umbrales.js';

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

// Los mismos cortes visuales que usa la barra de inventario del panel derecho, y los mismos
// que la configuración del sistema permite ajustar (B06).
export function estadoDeStock(stock, inicial, umbrales = UMBRALES_POR_OMISION.stock) {
  if (stock?.ilimitado) return 'suficiente';
  if (!Number.isFinite(stock?.disponible) || stock.disponible < 0) return 'sin-datos';
  if (stock.disponible === 0) return 'vacio';
  const capacidad = stock.capacidad ?? inicial;
  if (!Number.isFinite(capacidad) || capacidad <= 0) return 'sin-datos';
  const porcentaje = (stock.disponible / capacidad) * 100;
  if (porcentaje < umbrales.critico) return 'critico';
  if (porcentaje < umbrales.advertencia) return 'advertencia';
  return 'suficiente';
}

export const ETIQUETAS_STOCK = {
  suficiente: 'Stock suficiente',
  advertencia: 'Stock en advertencia',
  critico: 'Stock crítico',
  vacio: 'Almacén vacío',
  'sin-datos': 'Stock no disponibles para este instante'
};

/** Niveles ofrecidos por el filtro del mapa (D07), en el orden del semáforo. */
export const NIVELES_STOCK = [['suficiente', 'Suficiente'], ['advertencia', 'Advertencia'],
  ['critico', 'Crítico'], ['vacio', 'Vacío']];
