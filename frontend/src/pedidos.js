// Reglas del pedido, tomadas del caso y del lector del backend (`LectorVentas.java`): el
// formato del archivo mensual de ventas, los plazos admitidos y los límites de la retícula.
// Viven aquí y no en los componentes para poder probarlas sin montar React.
import { ANCHO, ALTO } from './mapa.js';

/** 36 h es el plazo regular; los demás son los priorizados del caso. */
export const PLAZOS = [4, 8, 12, 18, 36];

/** Capacidad de la unidad más grande. Por encima, el pedido va en entregas parciales. */
export const CAPACIDAD_MAYOR = 24;

// El mismo patrón que compila LectorVentas, para aceptar y rechazar exactamente lo mismo.
const REGISTRO = /^(\d{1,2})d(\d{1,2})h(\d{1,2})m:(-?\d+),(-?\d+),([^,]+),(\d+),(\d+)$/;
const PERIODO_EN_NOMBRE = /(\d{4})(\d{2})/;

export function diasDelMes(aaaamm) {
  const anio = Number(String(aaaamm).slice(0, 4));
  const mes = Number(String(aaaamm).slice(4, 6));
  if (!anio || !mes || mes < 1 || mes > 12) return 0;
  return new Date(Date.UTC(anio, mes, 0)).getUTCDate();
}

/** El periodo sale del nombre del archivo, igual que en el backend (`ventas202601.txt`). */
export function periodoDelNombre(nombre) {
  const hallado = PERIODO_EN_NOMBRE.exec(nombre || '');
  return hallado ? hallado[1] + hallado[2] : '';
}

export function enLaReticula(x, y) {
  return Number.isInteger(x) && Number.isInteger(y)
    && x >= 0 && x <= ANCHO && y >= 0 && y <= ALTO;
}

/**
 * Comprobaciones del alta de un pedido, como lista y no como un único mensaje: el estándar
 * de GUI pide mostrarlas todas a la vez. `error` bloquea el registro; `aviso` no.
 */
const vacio = valor => valor === '' || valor === null || valor === undefined;

export function comprobaciones(borrador) {
  const { cliente = '', x = '', y = '', cantidad = '', plazo = '', recepcion = '' } = borrador || {};
  const nx = Number(x);
  const ny = Number(y);
  const unidades = Number(cantidad);
  const horas = Number(plazo);
  const lista = [];

  const nombre = String(cliente).trim();
  lista.push(!nombre
    ? { clave: 'cliente', nivel: 'error', texto: 'Falta el código de cliente.' }
    : /^c\d+$/i.test(nombre)
      ? { clave: 'cliente', nivel: 'ok', texto: `Cliente ${nombre}.` }
      : { clave: 'cliente', nivel: 'aviso', texto: `«${nombre}» no sigue la forma cNNNN del archivo de ventas.` });

  lista.push(vacio(x) || vacio(y)
    ? { clave: 'nodo', nivel: 'error', texto: 'Falta el nodo (x, y) de entrega.' }
    : enLaReticula(nx, ny)
      ? { clave: 'nodo', nivel: 'ok', texto: `Nodo (${nx}, ${ny}) dentro de la retícula.` }
      : { clave: 'nodo', nivel: 'error', texto: `El nodo debe caer entre (0, 0) y (${ANCHO}, ${ALTO}).` });

  lista.push(vacio(cantidad)
    ? { clave: 'cantidad', nivel: 'error', texto: 'Falta la cantidad de producto P.' }
    : Number.isInteger(unidades) && unidades > 0
      ? unidades > CAPACIDAD_MAYOR
        ? { clave: 'cantidad', nivel: 'aviso', texto: `${unidades} unidades superan los ${CAPACIDAD_MAYOR} de un auto: se atenderá con entregas parciales.` }
        : { clave: 'cantidad', nivel: 'ok', texto: `${unidades} unidades caben en una sola unidad de transporte.` }
      : { clave: 'cantidad', nivel: 'error', texto: 'La cantidad debe ser un entero mayor que cero.' });

  lista.push(PLAZOS.includes(horas)
    ? { clave: 'plazo', nivel: 'ok', texto: horas === 36 ? 'Plazo regular de 36 h.' : `Plazo priorizado de ${horas} h.` }
    : { clave: 'plazo', nivel: 'error', texto: `El plazo debe ser uno de ${PLAZOS.join(', ')} horas.` });

  lista.push(recepcion
    ? { clave: 'recepcion', nivel: 'ok', texto: 'Fecha y hora de recepción registradas.' }
    : { clave: 'recepcion', nivel: 'error', texto: 'Falta la fecha y hora de recepción.' });

  return lista;
}

export function hayErrores(lista) {
  return lista.some(c => c.nivel === 'error');
}

/** Línea del archivo mensual a partir de un borrador válido, en el formato del caso. */
export function aRegistro({ cliente, x, y, cantidad, plazo, recepcion }) {
  const momento = new Date(recepcion);
  const dd = String(momento.getDate()).padStart(2, '0');
  const hh = String(momento.getHours()).padStart(2, '0');
  const mm = String(momento.getMinutes()).padStart(2, '0');
  return `${dd}d${hh}h${mm}m:${Number(x)},${Number(y)},${String(cliente).trim()},${Number(cantidad)},${Number(plazo)}`;
}

/**
 * Lee el archivo mensual de ventas y valida su estructura antes de incorporarlo, que es lo
 * que pide la exigencia. A diferencia del backend —que aborta en la primera línea mala— aquí
 * se recorren todas y se devuelven los fallos juntos: el operador necesita verlos de una vez.
 * El correlativo avanza con cada línea leída, válida o no, para que los identificadores
 * coincidan con los que asignaría el backend una vez corregido el archivo.
 */
export function parsearVentas(texto, aaaamm) {
  const dias = diasDelMes(aaaamm);
  const pedidos = [];
  const errores = [];
  let correlativo = 0;
  let leidas = 0;

  String(texto || '').split(/\r?\n/).forEach((cruda, indice) => {
    const linea = cruda.trim();
    if (!linea || linea.startsWith('#')) return;   // el lector salta vacías y comentarios
    leidas += 1;
    correlativo += 1;
    const numero = indice + 1;
    const falla = motivo => errores.push({ numero, linea, motivo });

    const hallado = REGISTRO.exec(linea);
    if (!hallado) {
      falla('No tiene el formato ##d##h##m:posX,posY,cliente,qq,hl.');
      return;
    }

    const dia = Number(hallado[1]);
    const hora = Number(hallado[2]);
    const minuto = Number(hallado[3]);
    const x = Number(hallado[4]);
    const y = Number(hallado[5]);
    const cliente = hallado[6].trim();
    const cantidad = Number(hallado[7]);
    const horas = Number(hallado[8]);

    if (dias && (dia < 1 || dia > dias)) return falla(`El día ${dia} no existe en el periodo ${aaaamm}.`);
    if (hora > 23 || minuto > 59) return falla(`La hora ${hora}:${minuto} no es válida.`);
    if (!enLaReticula(x, y)) return falla(`El nodo (${x}, ${y}) cae fuera de la retícula de ${ANCHO} × ${ALTO}.`);
    if (cantidad <= 0) return falla('La cantidad debe ser mayor que cero.');
    if (horas <= 0) return falla('El plazo debe ser mayor que cero.');

    pedidos.push({
      id: `P-${String(correlativo).padStart(5, '0')}`,
      cliente, destino: { x, y }, cantidad, horas,
      recepcion: `${aaaamm.slice(0, 4)}-${aaaamm.slice(4, 6)}-${String(dia).padStart(2, '0')}`
        + `T${String(hora).padStart(2, '0')}:${String(minuto).padStart(2, '0')}`,
      // El backend solo exige plazo > 0; los que no son del caso se dejan pasar con aviso.
      inusual: !PLAZOS.includes(horas) ? `Plazo de ${horas} h fuera de ${PLAZOS.join(', ')}.` : null
    });
  });

  return { pedidos, errores, leidas };
}

/**
 * Recorrido de un pedido a lo largo del historial de planes: a qué ruta y unidad quedó
 * asignado en cada replanificación y qué hitos informó el simulador. Misma fuente que
 * `estadosDePedido()`, así que la línea de tiempo y la columna «Situación» no se contradicen.
 */
export function seguimientoDePedido(planes, pedidoId, aMs) {
  const asignaciones = [];
  const hitos = [];
  const vistos = new Set();
  let yaSalio = false;

  for (const plan of planes || []) {
    const unidades = new Set();
    for (const ruta of plan.rutas || []) {
      const parada = (ruta.paradas || []).find(p => p.id === pedidoId);
      if (!parada) continue;
      unidades.add(ruta.vehiculoId);
      asignaciones.push({
        reloj: plan.reloj, rutaId: ruta.id, vehiculoId: ruta.vehiculoId,
        tipoVehiculo: ruta.tipoVehiculo, almacenId: ruta.almacenId, origen: ruta.origen,
        distanciaKm: ruta.distanciaKm, costo: ruta.costo, pedido: parada
      });
    }
    for (const trayecto of plan.trayectos || []) {
      const suya = unidades.has(trayecto.vehiculoId);
      for (const paso of trayecto.pasos || []) {
        const delPedido = paso.pedidoId === pedidoId;
        // De la unidad que lo lleva interesa además su salida: es lo que lo pone «en ruta».
        if (!delPedido && !(suya && paso.hito === 'SALIDA')) continue;
        const clave = `${trayecto.vehiculoId}|${paso.hito}|${paso.instante}`;
        if (vistos.has(clave)) continue;
        vistos.add(clave);
        const retoma = paso.hito === 'SALIDA' && yaSalio;
        if (paso.hito === 'SALIDA') yaSalio = true;
        hitos.push({
          instante: paso.instante, hito: paso.hito, ubicacion: paso.ubicacion,
          vehiculoId: trayecto.vehiculoId, delPedido, retoma
        });
      }
    }
  }

  hitos.sort((a, b) => aMs(a.instante) - aMs(b.instante));
  return { asignaciones, hitos };
}
