import { estadoDeCarga } from './carga.js';
import { estadoDeStock } from './stock.js';

/**
 * Indicadores globales del mapa (G01 a G04): el llenado de la flota y el de los almacenes en
 * conjunto, cada uno con su semáforo. Son los mismos umbrales que ya usan el ícono de cada
 * unidad (`carga.js`) y el de cada almacén (`stock.js`): el agregado no inventa cortes propios,
 * así que el semáforo global y el del elemento individual nunca se contradicen.
 *
 * El almacén central es ilimitado y queda fuera del porcentaje —no tiene tope contra el que
 * medirlo—, pero se informa aparte para que no parezca omitido.
 */
function sinDatos(extra = {}) {
  return { carga: 0, capacidad: 0, porcentaje: null, estado: 'sin-datos', unidades: 0, ...extra };
}

/** Ocupación de toda la flota: suma de cargas contra suma de capacidades. */
export function ocupacionDeFlota(vehiculos, umbrales = undefined) {
  const medibles = (vehiculos || []).filter(v => Number.isFinite(v?.cargaAbordo)
    && Number.isFinite(v?.capacidad) && v.capacidad > 0
    && v.cargaAbordo >= 0 && v.cargaAbordo <= v.capacidad);
  if (!medibles.length) return sinDatos();
  const carga = medibles.reduce((total, v) => total + v.cargaAbordo, 0);
  const capacidad = medibles.reduce((total, v) => total + v.capacidad, 0);
  return { carga, capacidad, porcentaje: Math.round((carga / capacidad) * 100),
    estado: estadoDeCarga({ cargaAbordo: carga, capacidad }, umbrales), unidades: medibles.length };
}

/** Ocupación de los almacenes con tope: disponible contra stock inicial del caso. */
export function ocupacionDeAlmacenes(almacenes, inicialPorId, umbrales = undefined) {
  const lista = almacenes || [];
  const ilimitados = lista.filter(almacen => almacen?.ilimitado).length;
  const medibles = lista.filter(almacen => !almacen?.ilimitado
    && Number.isFinite(almacen?.disponible) && almacen.disponible >= 0
    && Number.isFinite(almacen.capacidad ?? inicialPorId?.get(almacen.id))
    && (almacen.capacidad ?? inicialPorId.get(almacen.id)) > 0);
  if (!medibles.length) return sinDatos({ ilimitados });
  const carga = medibles.reduce((total, almacen) => total + almacen.disponible, 0);
  const capacidad = medibles.reduce((total, almacen) => total + (almacen.capacidad ?? inicialPorId.get(almacen.id)), 0);
  return { carga, capacidad, porcentaje: Math.round((carga / capacidad) * 100),
    estado: estadoDeStock({ disponible: carga }, capacidad, umbrales), unidades: medibles.length,
    ilimitados };
}
