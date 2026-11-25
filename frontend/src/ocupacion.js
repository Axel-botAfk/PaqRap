import { UMBRALES_POR_OMISION } from './umbrales.js';

/**
 * Nivel de ocupación de un almacén, para pintar su ícono en el mapa.
 *
 * Ocupación es qué tan lleno está: stock disponible sobre capacidad. Es la lectura contraria al
 * semáforo de stock de `stock.js` (allí «mucho stock» es verde); este módulo sigue la codificación
 * pedida para el mapa: blanco vacío, verde baja, amarillo intermedia, rojo alta. Los cortes son
 * los mismos umbrales configurables del semáforo (B06), de modo que hay un solo juego de límites.
 *
 * El almacén central tiene stock ilimitado y un almacén sin corte de inventario no tiene dato:
 * ninguno de los dos se pinta, conservan el color propio del ícono.
 */
export function ocupacionDeAlmacen(stock, inicial, umbrales = UMBRALES_POR_OMISION.stock) {
  if (stock?.ilimitado) return { nivel: 'ilimitado', porcentaje: null };
  if (!Number.isFinite(stock?.disponible) || stock.disponible < 0) return { nivel: 'sin-datos', porcentaje: null };
  if (stock.disponible === 0) return { nivel: 'vacio', porcentaje: 0 };
  const capacidad = stock.capacidad ?? inicial;
  if (!Number.isFinite(capacidad) || capacidad <= 0) return { nivel: 'sin-datos', porcentaje: null };
  const porcentaje = (stock.disponible / capacidad) * 100;
  const nivel = porcentaje < umbrales.critico ? 'bajo'
    : porcentaje < umbrales.advertencia ? 'intermedio' : 'alto';
  return { nivel, porcentaje: Math.min(100, Math.round(porcentaje)) };
}

/** Colores del ícono por nivel: relleno, contorno y el trazo de los detalles (techo y letra). */
export const COLORES_OCUPACION = Object.freeze({
  vacio: Object.freeze({ relleno: '#FFFFFF', contorno: '#6B7690', detalle: '#6B7690' }),
  bajo: Object.freeze({ relleno: '#23A15F', contorno: '#0B6B3A', detalle: '#FFFFFF' }),
  intermedio: Object.freeze({ relleno: '#F5C518', contorno: '#B38600', detalle: '#5A4300' }),
  alto: Object.freeze({ relleno: '#E3262A', contorno: '#9B1216', detalle: '#FFFFFF' })
});

export const ETIQUETAS_OCUPACION = Object.freeze({
  vacio: 'Vacío',
  bajo: 'Ocupación baja',
  intermedio: 'Ocupación intermedia',
  alto: 'Ocupación alta',
  ilimitado: 'Stock ilimitado',
  'sin-datos': 'Sin datos'
});

/** Niveles con color propio, en el orden de la leyenda. */
export const NIVELES_OCUPACION = Object.freeze(['vacio', 'bajo', 'intermedio', 'alto']);
