const FINAL = new Set(['TERMINADA', 'COLAPSADA', 'FALLIDA', 'CANCELADA']);

export function esFinal(estado) {
  return FINAL.has(estado);
}

async function solicitud(ruta, opciones = {}) {
  const respuesta = await fetch(ruta, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers || {}) }
  });
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
  consultar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}`),
  fotogramas: (id, desde = 0) => solicitud(
    `/api/ejecuciones/${encodeURIComponent(id)}/fotogramas?desde=${encodeURIComponent(desde)}`),
  cancelar: (id) => solicitud(`/api/ejecuciones/${encodeURIComponent(id)}/cancelar`, {
    method: 'POST'
  })
};

export function urlWebSocket(id) {
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${location.host}/ws/ejecuciones/${encodeURIComponent(id)}`;
}
