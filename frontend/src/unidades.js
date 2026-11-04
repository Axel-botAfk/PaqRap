import { estadoDeCarga } from './carga.js';

/**
 * La lista de unidades de transporte del panel (E02 a E05 y E13).
 *
 * Cada fila reúne lo que la API ya publica para el instante mostrado: la unidad del último
 * corte —carga, capacidad y estado— y, si el plan vigente le asignó una ruta, los pedidos que
 * traslada con su cantidad de productos. No se inventa ocupación para una unidad sin datos: se
 * deja en nulo y el panel lo dice.
 */
export function filasDeUnidades(vehiculos, rutas) {
  const rutaPorVehiculo = new Map((rutas || []).map(ruta => [ruta.vehiculoId, ruta]));
  return (vehiculos || []).filter(v => v?.id).map(vehiculo => {
    const ruta = rutaPorVehiculo.get(vehiculo.id) || null;
    const pedidos = ruta?.paradas || [];
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
      pedidos: pedidos.map(p => ({ id: p.id, cantidad: p.cantidad })),
      productos: pedidos.reduce((total, p) => total + (Number.isFinite(p.cantidad) ? p.cantidad : 0), 0)
    };
  });
}

export const ORDENES = [
  ['id', 'Unidad'],
  ['ocupacion-desc', 'Ocupación ↓'],
  ['ocupacion-asc', 'Ocupación ↑'],
  ['pedidos-desc', 'Pedidos ↓']
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
  switch (criterio) {
    case 'ocupacion-desc': return lista.sort(porOcupacion(-1));
    case 'ocupacion-asc': return lista.sort(porOcupacion(1));
    case 'pedidos-desc': return lista.sort((a, b) =>
      b.pedidos.length === a.pedidos.length ? porId(a, b) : b.pedidos.length - a.pedidos.length);
    default: return lista.sort(porId);
  }
}
