import React, { useState } from 'react';
import { fecha } from '../services/formato.js';

/* Banner de avería embebido sobre el mapa, modal de colapso y aviso de error.
   Los tres acompañan el color con texto, como pide 25.dis.gui.v2 seccion 8. */

export function BannerAveria({ ejecucion, alVerUnidad }) {
  const [descartado, setDescartado] = useState('');

  const fuera = (ejecucion?.vehiculos || [])
    .filter(v => v.estado === 'AVERIADO' || v.estado === 'EN_MANTENIMIENTO');
  if (fuera.length === 0) return null;

  /* Se descarta un conjunto concreto de unidades: si aparece otra avería, el aviso vuelve. */
  const firma = fuera.map(v => `${v.id}:${v.estado}`).sort().join('|');
  if (firma === descartado) return null;

  const averiadas = fuera.filter(v => v.estado === 'AVERIADO');
  const titular = averiadas.length > 0 ? 'AVERÍA' : 'MANTENIMIENTO';
  const lista = (averiadas.length > 0 ? averiadas : fuera).map(v => v.id).join(', ');

  return <div className="banner" role="status">
    <span className="k">{titular}</span>
    <b>{fuera.length === 1 ? 'Unidad fuera de servicio' : `${fuera.length} unidades fuera de servicio`}</b>
    <span>{lista} · sus pedidos a bordo se reasignan en la siguiente planificación.</span>
    <button type="button" className="btn" onClick={alVerUnidad}>VER AVERÍAS</button>
    <button type="button" className="btn"
      style={{ background: 'transparent', color: '#fff', borderColor: 'rgba(255,255,255,.6)' }}
      onClick={() => setDescartado(firma)}>DESCARTAR</button>
  </div>;
}

export function ModalColapso({ ejecucion, alCerrar, alRepetir }) {
  const resumen = ejecucion?.resumen;
  if (!resumen?.huboColapso) return null;

  return <div className="modal-fondo" role="dialog" aria-modal="true" aria-labelledby="titulo-colapso">
    <div className="modal">
      <h2 id="titulo-colapso">La simulación colapsó</h2>
      <p>
        La corrida se detuvo en el primer pedido sin ruta factible: el planificador no pudo
        asignarlo a ninguna unidad dentro de su plazo.
      </p>
      <dl className="resumen" style={{ marginTop: 16 }}>
        <div><dt>Instante del colapso</dt><dd>{fecha(resumen.instanteColapso)}</dd></div>
        <div><dt>Pedido que colapsó</dt><dd className="mono">{resumen.pedidoQueColapso || 'No informado'}</dd></div>
      </dl>
      <div className="acciones">
        <button type="button" className="btn btn-primario" onClick={alCerrar}>VER EL MAPA CONGELADO</button>
        <button type="button" className="btn" onClick={alRepetir}>REPETIR LA CORRIDA</button>
      </div>
    </div>
  </div>;
}

export function AvisoError({ mensaje, alCerrar }) {
  if (!mensaje) return null;
  return <div className="aviso aviso-error" role="alert">
    <b>ATENCIÓN</b>
    <span>{mensaje}</span>
    <button type="button" onClick={alCerrar} aria-label="Cerrar el aviso">×</button>
  </div>;
}
