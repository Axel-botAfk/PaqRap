const FINAL = new Set(['TERMINADA', 'COLAPSADA', 'FALLIDA', 'CANCELADA']);

export function esFinal(estado) {
  return FINAL.has(estado);
}

async function solicitud(ruta, opciones = {}) {
  const respuesta = await fetch(ruta, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers || {}) }
  }).catch(() => { throw new Error('No se pudo conectar con el servidor.'); });
  let cuerpo;
  try {
    cuerpo = await respuesta.json();
  } catch {
    throw new Error('El servidor devolvió una respuesta no válida.');
  }
  if (!respuesta.ok) {
    const error = new Error(cuerpo.mensaje || `Error HTTP ${respuesta.status}`);
    error.status = respuesta.status;
    throw error;
  }
  return cuerpo;
}

const JSON_CUERPO = { 'Content-Type': 'application/json' };

export const api = {
  salud: () => solicitud('/api/salud'),
  periodos: () => solicitud('/api/datos/periodos'),
  periodo: (aaaamm) => solicitud(`/api/datos/periodos/${encodeURIComponent(aaaamm)}`),
  mapa: (aaaamm) => solicitud(`/api/datos/mapa/${encodeURIComponent(aaaamm)}`),
  iniciar: (datos) => solicitud('/api/ejecuciones', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(datos)
  }),
  activas: (escenario) => solicitud(`/api/ejecuciones/activas?escenario=${encodeURIComponent(escenario)}`),
  consultar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}`),
  inventario: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/inventario`),
  averias: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/averias`),
  registrarAveria: (id, datos) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/averias`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos)
  }),
  fotogramas: (id, desde = 0) => solicitud(
    `/api/ejecuciones/${encodeURIComponent(id)}/fotogramas?desde=${encodeURIComponent(desde)}`),
  planes: (id, desde = 0) => solicitud(
    `/api/ejecuciones/${encodeURIComponent(id)}/planes?desde=${encodeURIComponent(desde)}`),
  cancelar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/cancelar`, {
    method: 'POST'
  }),
  pausar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/pausar`, { method: 'POST' }),
  reanudar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/reanudar`, { method: 'POST' }),
  flota: () => solicitud('/api/flota'),
  actualizarTipoFlota: (tipo, datos) => solicitud(`/api/flota/tipos/${encodeURIComponent(tipo)}`, {
    method: 'PUT', headers: JSON_CUERPO, body: JSON.stringify(datos)
  }),
  agregarUnidad: (datos) => solicitud('/api/flota/unidades', {
    method: 'POST', headers: JSON_CUERPO, body: JSON.stringify(datos)
  }),
  actualizarUnidad: (id, datos) => solicitud(`/api/flota/unidades/${encodeURIComponent(id)}`, {
    method: 'PUT', headers: JSON_CUERPO, body: JSON.stringify(datos)
  }),
  eliminarUnidad: (id) => solicitud(`/api/flota/unidades/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  restablecerFlota: () => solicitud('/api/flota/restablecer', { method: 'POST' }),
  eventos: (id, desde = 0) => solicitud(
    `/api/ejecuciones/${encodeURIComponent(id)}/eventos?desde=${encodeURIComponent(desde)}`)
};

export function urlWebSocket(id) {
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${location.host}/ws/ejecuciones/${encodeURIComponent(id)}`;
}
