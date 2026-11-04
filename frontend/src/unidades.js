import { estadoDeCarga } from './carga.js';
import { aMs } from './reloj.js';

const MINUTO_MS = 60000;
const ENTREGAS = new Set(['ENTREGA', 'FIN_ENTREGA']);

/**
 * Horarios de una unidad dentro de la ventana que publica su trayecto (E06, E07, E14 y E15).
 *
 * El caso distingue la llegada de la salida, así que se devuelven las dos: la salida es el
 * primer paso del trayecto —el hito SALIDA cuando existe— y la llegada es la próxima entrega
 * a partir del instante mostrado, es decir el destino inmediato. Si ya no quedan entregas por
 * delante se informa la última, y los minutos quedan en nulo para no inventar un futuro.
 */
export function horariosDeTrayecto(trayecto, instante) {
  const pasos = trayecto?.pasos || [];
  const momento = aMs(instante);
  if (!pasos.length) return { salida: null, llegada: null, minutosAlDestino: null, minutosDesdeSalida: null };
  const salida = (pasos.find(paso => paso.hito === 'SALIDA') || pasos[0])?.instante || null;
  const entregas = pasos.filter(paso => ENTREGAS.has(paso.hito));
  const siguiente = Number.isFinite(momento)
    ? entregas.find(paso => aMs(paso.instante) >= momento) : null;
  const llegada = (siguiente || entregas.at(-1))?.instante || null;
  const minutos = (desde, hasta) => {
    const a = aMs(desde);
    const b = aMs(hasta);
    return Number.isFinite(a) && Number.isFinite(b) ? Math.round((b - a) / MINUTO_MS) : null;
  };
  return {
    salida,
    llegada,
    // Solo hacia adelante: una entrega ya cumplida no son «minutos para llegar».
    minutosAlDestino: siguiente ? minutos(instante, siguiente.instante) : null,
    minutosDesdeSalida: Number.isFinite(momento) ? minutos(salida, instante) : null
  };
}

/**
 * La lista de unidades de transporte del panel (E02 a E05 y E13).
 *
 * Cada fila reúne lo que la API ya publica para el instante mostrado: la unidad del último
 * corte —carga, capacidad y estado— y, si el plan vigente le asignó una ruta, los pedidos que
 * traslada con su cantidad de productos. No se inventa ocupación para una unidad sin datos: se
 * deja en nulo y el panel lo dice.
 */
export function filasDeUnidades(vehiculos, rutas, trayectos = null, instante = null) {
  const rutaPorVehiculo = new Map((rutas || []).map(ruta => [ruta.vehiculoId, ruta]));
  const trayectoPorVehiculo = new Map((trayectos || []).map(t => [t.vehiculoId, t]));
  return (vehiculos || []).filter(v => v?.id).map(vehiculo => {
    const ruta = rutaPorVehiculo.get(vehiculo.id) || null;
    const pedidos = ruta?.paradas || [];
    const horarios = horariosDeTrayecto(trayectoPorVehiculo.get(vehiculo.id), instante);
    const medible = Number.isFinite(vehiculo.cargaAbordo) && Number.isFinite(vehiculo.capacidad)
      && vehiculo.capacidad > 0;
    return {
      id: vehiculo.id,
      tipo: vehiculo.tipo,
      estado: vehiculo.estado,
      ubicacion: vehiculo.ubicacion,
      cargaAbordo: medible ? vehiculo.cargaAbordo : null,
      capacidad: medible ? vehiculo.capacidad : null,
      porcentaje: medible ? Math.round((vehiculo.cargaAbordo / vehiculo.capacidad) * 100) : null,
      nivel: estadoDeCarga(vehiculo),
      ruta: ruta?.id || null,
      ...horarios,
      pedidos: pedidos.map(p => ({ id: p.id, cantidad: p.cantidad })),
      productos: pedidos.reduce((total, p) => total + (Number.isFinite(p.cantidad) ? p.cantidad : 0), 0)
    };
  });
}

export const ORDENES = [
  ['id', 'Unidad'],
  ['ocupacion-desc', 'Ocupación ↓'],
  ['ocupacion-asc', 'Ocupación ↑'],
  ['pedidos-desc', 'Pedidos ↓'],
  ['salida-asc', 'Hora de salida ↑'],
  ['salida-desc', 'Hora de salida ↓'],
  ['llegada-asc', 'Hora de llegada ↑'],
  ['llegada-desc', 'Hora de llegada ↓']
];

const porId = (a, b) => a.id.localeCompare(b.id, 'es', { numeric: true });

/** Ordena sin mutar. Las unidades sin ocupación medible van al final, no al principio. */
export function ordenarUnidades(filas, criterio) {
  const lista = [...(filas || [])];
  const porOcupacion = signo => (a, b) => {
    if (a.porcentaje === null && b.porcentaje === null) return porId(a, b);
    if (a.porcentaje === null) return 1;
    if (b.porcentaje === null) return -1;
    return a.porcentaje === b.porcentaje ? porId(a, b) : signo * (a.porcentaje - b.porcentaje);
  };
  // Una unidad sin horario publicado va al final en los dos sentidos, como la ocupación.
  const porHora = (campo, signo) => (a, b) => {
    const x = aMs(a[campo]);
    const y = aMs(b[campo]);
    if (!Number.isFinite(x) && !Number.isFinite(y)) return porId(a, b);
    if (!Number.isFinite(x)) return 1;
    if (!Number.isFinite(y)) return -1;
    return x === y ? porId(a, b) : signo * (x - y);
  };
  switch (criterio) {
    case 'ocupacion-desc': return lista.sort(porOcupacion(-1));
    case 'ocupacion-asc': return lista.sort(porOcupacion(1));
    case 'pedidos-desc': return lista.sort((a, b) =>
      b.pedidos.length === a.pedidos.length ? porId(a, b) : b.pedidos.length - a.pedidos.length);
    case 'salida-asc': return lista.sort(porHora('salida', 1));
    case 'salida-desc': return lista.sort(porHora('salida', -1));
    case 'llegada-asc': return lista.sort(porHora('llegada', 1));
    case 'llegada-desc': return lista.sort(porHora('llegada', -1));
    default: return lista.sort(porId);
  }
}

/**
 * Búsqueda por minutos (E06 y E07), distinguiendo la llegada de la salida: «al destino» mira
 * cuánto falta para la próxima entrega y «desde el origen» cuánto pasó desde que salió. Una
 * unidad sin ese dato no se cuela en el resultado de una búsqueda que sí lo exige.
 */
export function filtrarUnidades(filas, { minutosAlDestino = null, minutosDesdeSalida = null } = {}) {
  return (filas || []).filter(fila => {
    if (Number.isFinite(minutosAlDestino)
      && !(Number.isFinite(fila.minutosAlDestino) && fila.minutosAlDestino <= minutosAlDestino)) return false;
    if (Number.isFinite(minutosDesdeSalida)
      && !(Number.isFinite(fila.minutosDesdeSalida) && fila.minutosDesdeSalida <= minutosDesdeSalida)) return false;
    return true;
  });
}
