// Formato de los valores que se muestran en pantalla. Vive aparte porque lo usan por igual
// el mapa, los rieles y las hojas.

const FORMATO_FECHA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
});

const FORMATO_HORA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit'
});

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
  return `${minutos(ritmo.minutosPorVentana)} simulados cada ${decimal(ritmo.saMs / 1000, ' s')}`;
}

// Ritmo fijo por escenario: ya no se configura desde la pantalla.
export const VELOCIDAD_DIA_A_DIA = 60;
export const DURACION_5_DIAS_MIN = 40;

export function ritmoConfigurado(escenario) {
  if (escenario === 'PERIODO_5_DIAS') {
    return `Los 5 días se reproducen en unos ${DURACION_5_DIAS_MIN} min reales.`;
  }
  if (escenario === 'OPERACION_DIARIA') {
    return `La jornada se reproduce en unos ${minutos(1440 / VELOCIDAD_DIA_A_DIA)} reales.`;
  }
  return 'Avanza 2 h simuladas cada 10 s y se detiene en el primer pedido vencido.';
}

export const ETIQUETAS = {
  PENDIENTE: 'En espera', EN_CURSO: 'En ejecución', PAUSADA: 'En pausa', TERMINADA: 'Terminada',
  COLAPSADA: 'Colapso detectado', FALLIDA: 'Fallida', CANCELADA: 'Cancelada'
};

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
