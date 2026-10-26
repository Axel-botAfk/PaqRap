import React from 'react';
import { fecha, numero, tipoAveria, estadoAveria, motivoPedido } from '../formato.js';
import IconoUI from '../components/IconoUI.jsx';

const ETIQUETAS_EVENTO = {
  REPLANIFICACION: 'Replanificación',
  AVERIA_APLICADA: 'Unidad fuera de servicio',
  COLAPSO: 'Colapso'
};

/** Hoja de averías y excepciones: solo hechos que informó la corrida activa. */
export default function HojaAverias({ ejecucion, averias, eventosOperacion }) {
  const sinAsignar = ejecucion?.noAsignados || [];
  const colapso = ejecucion?.resumen?.huboColapso;
  const fallo = ejecucion?.estado === 'FALLIDA';
  const hechos = eventosOperacion.filter(e => ETIQUETAS_EVENTO[e.tipo]).slice(-30).reverse();
  const total = sinAsignar.length + averias.length + hechos.length + (colapso ? 1 : 0) + (fallo ? 1 : 0);

  return <section className="sheet-contenido">
    <div className="kpis">
      <div className="kpi" style={{ '--k': 'var(--brand-red)' }}><span>AVERÍAS REGISTRADAS</span><b>{numero(averias.length)}</b><IconoUI nombre="averias" className="kpi-icono" size={32} /></div>
      <div className="kpi" style={{ '--k': 'var(--warning)' }}><span>HECHOS DE LA OPERACIÓN</span><b>{numero(hechos.length)}</b><IconoUI nombre="engranaje" className="kpi-icono" size={32} /></div>
      <div className="kpi" style={{ '--k': 'var(--navy)' }}><span>PEDIDOS SIN ASIGNAR</span><b>{numero(sinAsignar.length)}</b><IconoUI nombre="caja" className="kpi-icono" size={32} /></div>
    </div>

    <div className="card">
      <header><h2><IconoUI nombre="campana" size={22} /> ALERTAS OBSERVADAS</h2><span className="muted">{numero(total)} en la instantánea actual</span></header>
      <div className="card-cuerpo">
        {total === 0 && <p className="vacio"><IconoUI nombre="campana" size={38} />Sin alertas observadas.</p>}
        {colapso && <div className="alerta-lista">
          <b>Colapso logístico</b>
          <span>Primer incumplimiento: {fecha(ejecucion.resumen.instanteColapso)}. Pedido: {ejecucion.resumen.pedidoQueColapso || 'no disponible'}.</span>
        </div>}
        {fallo && <div className="alerta-lista">
          <b>Ejecución fallida</b>
          <span>{ejecucion.mensajeError || 'La simulación no pudo completarse.'}</span>
        </div>}
        {averias.map(a => <div className="alerta-lista" key={`${a.unidadId}-${a.instante}`}>
          <b>Avería {tipoAveria(a.tipo)} · {a.unidadId}</b>
          <span>{estadoAveria(a.estado)} · registrada a las {fecha(a.instante)} · {numero(a.pedidosAfectados || 0)} pedido(s) replanificado(s).</span>
        </div>)}
        {hechos.map(e => <div className="alerta-lista" key={`e${e.indice}`}>
          <b>{ETIQUETAS_EVENTO[e.tipo]}{e.pedidoId ? ` · ${e.pedidoId}` : e.vehiculoId ? ` · ${e.vehiculoId}` : ''}</b>
          <span>{fecha(e.reloj)} · {e.detalle}</span>
        </div>)}
        {sinAsignar.slice(0, 20).map((pedido, indice) => <div className="alerta-lista" key={`${pedido.id}-${indice}`}>
          <b>{pedido.id} · sin asignar</b>
          <span>{motivoPedido(pedido.motivo) || 'Sin motivo específico disponible'} · plazo {fecha(pedido.fechaLimite)}</span>
        </div>)}
        {sinAsignar.length > 20 && <p className="nota">Hay {numero(sinAsignar.length - 20)} pedidos adicionales sin asignar.</p>}
      </div>
    </div>
  </section>;
}
