// Formato de los valores que se muestran en pantalla. Vive aparte porque lo usan por igual
// el mapa, los rieles y las hojas.

const FORMATO_FECHA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
});

const FORMATO_HORA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit'
});

/** Solo hora y minuto: en la lista de unidades el día ya lo da el reloj de la corrida. */
const FORMATO_SOLO_HORA = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' });

export function hora(valor) {
  if (!valor) return '—';
  const fechaLocal = new Date(valor);
  return Number.isNaN(fechaLocal.getTime()) ? valor : FORMATO_SOLO_HORA.format(fechaLocal);
}

export function fecha(valor) {
  if (!valor) return '—';
  const fechaLocal = new Date(valor);
  return Number.isNaN(fechaLocal.getTime()) ? valor : FORMATO_FECHA.format(fechaLocal);
}

/** Como fecha, con segundos: el reloj de la simulación avanza a la vista. */
export function fechaConSegundos(valor) {
  if (!valor) return '—';
  const fechaLocal = new Date(valor);
  return Number.isNaN(fechaLocal.getTime()) ? valor : FORMATO_HORA.format(fechaLocal);
}

export function numero(valor) {
  return typeof valor === 'number' ? new Intl.NumberFormat('es-PE').format(valor) : '—';
}

export function decimal(valor, unidad = '') {
  return typeof valor === 'number'
    ? `${new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 }).format(valor)}${unidad}`
    : '—';
}

export function minutos(valor) {
  if (typeof valor !== 'number') return '—';
  return valor >= 60 ? decimal(valor / 60, ' h') : decimal(valor, ' min');
}

/** Tiempo real de reloj transcurrido, para la barra de la simulación. */
export function duracion(ms) {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  const total = Math.floor(ms / 1000);
  const horas = Math.floor(total / 3600);
  const resto = total % 3600;
  const mins = Math.floor(resto / 60);
  const segs = resto % 60;
  if (horas) return `${horas} h ${String(mins).padStart(2, '0')} min`;
  if (mins) return `${mins} min ${String(segs).padStart(2, '0')} s`;
  return `${segs} s`;
}

export function textoRitmo(ritmo) {
  if (ritmo.velocidad === 1) {
    return `Tiempo real (K = 1): se planifica cada ${decimal(ritmo.saMs / 1000, ' s')} y el reloj no salta.`;
  }
  return `${minutos(ritmo.minutosPorVentana)} simulados cada ${decimal(ritmo.saMs / 1000, ' s')}`;
}

// Ritmo fijo por escenario: ya no se configura desde la pantalla.
export const DURACION_5_DIAS_MIN = 40;

export function ritmoConfigurado(escenario) {
  if (escenario === 'PERIODO_5_DIAS') {
    return `Los 5 días se reproducen en unos ${DURACION_5_DIAS_MIN} min reales.`;
  }
  if (escenario === 'OPERACION_DIARIA') {
    // No es una simulación: K = 1, así que no hay velocidad ni fecha inicial que elegir.
    return 'Tiempo real (K = 1): se planifica una vez por minuto con los pedidos registrados hasta ese momento.';
  }
  return 'Avanza 2 h simuladas cada 10 s y se detiene en el primer pedido vencido.';
}

export const ETIQUETAS = {
  PENDIENTE: 'En espera', EN_CURSO: 'En ejecución', PAUSADA: 'En pausa', TERMINADA: 'Terminada',
  COLAPSADA: 'Colapso detectado', FALLIDA: 'Fallida', CANCELADA: 'Detenida'
};

const TIPOS_VEHICULO = { AUTO: 'Auto', MOTO: 'Moto', BICICLETA: 'Bicicleta', CAMION: 'Camión' };
const ESTADOS_VEHICULO = {
  DISPONIBLE: 'Disponible', EN_RUTA: 'En ruta', AVERIADO: 'Averiado',
  EN_MANTENIMIENTO: 'En mantenimiento'
};

export const tipoVehiculo = tipo => TIPOS_VEHICULO[tipo] || tipo;
export const estadoVehiculo = estado => ESTADOS_VEHICULO[estado] || estado;
export const tipoAveria = tipo => ({ TIPO_1: 'Tipo 1', TIPO_2: 'Tipo 2', TIPO_3: 'Tipo 3' })[tipo] || tipo;
export const estadoAveria = estado => ({ REGISTRADA: 'Registrada', APLICADA: 'Aplicada',
  NO_APLICADA: 'No aplicada' })[estado] || estado;
export const motivoPedido = motivo => ({
  SIN_RUTA_FACTIBLE: 'Sin ruta factible', VENCIDO: 'Plazo vencido sin entrega'
})[motivo] || motivo;

/** Un color estable por unidad, para distinguir las rutas simultáneas en el mapa. */
export function colorDeRuta(ruta) {
  const clave = ruta.vehiculoId || ruta.id;
  const numeroVehiculo = Number(clave.match(/\d+$/)?.[0]);
  const familia = [...clave.replace(/\d+$/, '')]
    .reduce((total, letra) => total * 31 + letra.charCodeAt(0), 0);
  const codigo = Number.isFinite(numeroVehiculo) && numeroVehiculo > 0
    ? numeroVehiculo + familia * 37 : familia;
  return `hsl(${Math.round((codigo * 137.508) % 360)} 64% 39%)`;
}
