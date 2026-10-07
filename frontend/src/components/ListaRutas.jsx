import React from 'react';
import { decimal } from '../services/formato.js';

/* Rutas del plan vigente. Al elegir una se resalta en el mapa y se abre su detalle en el
   riel derecho; no es una pantalla aparte.

   La etiqueta de estado resume la holgura de la ruta: cuánto falta para el plazo más
   ajustado de sus paradas, medido contra el reloj simulado. No viene de la API, se calcula
   aquí a partir de datos que sí vienen (fechaLimite de cada parada y reloj de la corrida).
   Si no hay reloj todavía, no se muestra etiqueta en vez de inventar una. */

const CORTE_AJUSTADO = 4;   // horas
const CORTE_FALLA = 1;      // horas

export function holguraDeRuta(ruta, reloj) {
  if (!reloj || !ruta.paradas?.length) return null;
  const ahora = new Date(reloj).getTime();
  if (!Number.isFinite(ahora)) return null;
  const limites = ruta.paradas
    .map(p => new Date(p.fechaLimite).getTime())
    .filter(Number.isFinite);
  if (limites.length === 0) return null;
  return (Math.min(...limites) - ahora) / 3600000;
}

function Etiqueta({ horas }) {
  if (horas === null) return <span className="pill n">Sin plazo</span>;
  if (horas < CORTE_FALLA) return <span className="pill r">En falla</span>;
  if (horas < CORTE_AJUSTADO) return <span className="pill a">Ajustado</span>;
  return <span className="pill v">En plazo</span>;
}

export default function ListaRutas({ ejecucion, seleccion, alSeleccionar }) {
  const rutas = ejecucion?.rutas || [];
  /* La capacidad no viaja en RutaVista; se toma de la unidad que lleva la ruta. */
  const capacidad = new Map((ejecucion?.vehiculos || []).map(v => [v.id, v.capacidad]));

  return <section className="float rutas">
    <header>
      <h2>Rutas en curso</h2>
      <span className="muted" style={{ font: '800 11px var(--font-heading)' }}>{rutas.length}</span>
    </header>
    <div className="body sin-aire">
      {rutas.length === 0
        ? <p className="vacio">Todavía no hay rutas en el plan vigente.</p>
        : rutas.map((ruta, indice) => <button key={`${ruta.id}-${indice}`} type="button"
          className="rt" data-on={seleccion === ruta.id || undefined}
          onClick={() => alSeleccionar(seleccion === ruta.id ? null : ruta.id)}>
          <span className="l1">
            <span className="id">{ruta.id}</span>
            <Etiqueta horas={holguraDeRuta(ruta, ejecucion?.reloj)} />
          </span>
          <span className="l2">
            <span className="sub">
              {ruta.vehiculoId} · {ruta.carga}
              {capacidad.has(ruta.vehiculoId) ? `/${capacidad.get(ruta.vehiculoId)}` : ''} paq ·{' '}
              {ruta.paradas.length} ped.
            </span>
            <span className="num">{decimal(ruta.distanciaKm, ' km')} · S/ {decimal(ruta.costo)}</span>
          </span>
        </button>)}
    </div>
  </section>;
}
