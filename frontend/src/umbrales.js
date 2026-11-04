/**
 * Umbrales del semáforo, configurables desde la propia aplicación (B06).
 *
 * Hasta ahora los cortes vivían como literales en `carga.js` y `stock.js`, de modo que
 * ajustarlos obligaba a tocar el código. Aquí quedan en un solo lugar, con validación y
 * memoria en el navegador, y los consumen por igual el mapa, el panel de unidades, el
 * inventario y los indicadores globales.
 *
 * Son porcentajes entre 1 y 99, y el corte bajo siempre va por debajo del alto.
 */
export const UMBRALES_POR_OMISION = Object.freeze({
  carga: Object.freeze({ baja: 40, intermedia: 70 }),
  stock: Object.freeze({ critico: 20, advertencia: 60 })
});

const CLAVE = 'paqrap.umbrales';

const enteroValido = valor => Number.isFinite(Number(valor))
  && Number.isInteger(Number(valor)) && Number(valor) >= 1 && Number(valor) <= 99;

/** Un par válido, o el de omisión: nunca se guarda un corte que invierta el semáforo. */
function parValido(valores, omision, menor, mayor) {
  const bajo = Number(valores?.[menor]);
  const alto = Number(valores?.[mayor]);
  if (!enteroValido(bajo) || !enteroValido(alto) || bajo >= alto) return { ...omision };
  return { [menor]: bajo, [mayor]: alto };
}

export function normalizarUmbrales(valores) {
  return {
    carga: parValido(valores?.carga, UMBRALES_POR_OMISION.carga, 'baja', 'intermedia'),
    stock: parValido(valores?.stock, UMBRALES_POR_OMISION.stock, 'critico', 'advertencia')
  };
}

export function leerUmbrales() {
  try {
    const guardado = localStorage.getItem(CLAVE);
    return normalizarUmbrales(guardado ? JSON.parse(guardado) : null);
  } catch { return normalizarUmbrales(null); }
}

export function guardarUmbrales(valores) {
  const normalizados = normalizarUmbrales(valores);
  try { localStorage.setItem(CLAVE, JSON.stringify(normalizados)); } catch { /* sin almacenamiento */ }
  return normalizados;
}

export function olvidarUmbrales() {
  try { localStorage.removeItem(CLAVE); } catch { /* sin almacenamiento */ }
  return normalizarUmbrales(null);
}
