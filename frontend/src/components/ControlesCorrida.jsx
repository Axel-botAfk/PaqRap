import React, { useEffect, useId, useRef, useState } from 'react';
import IconoUI from './IconoUI.jsx';
import { controlesDeCorrida } from '../controlesCorrida.js';
import { ETIQUETAS, numero } from '../formato.js';

/** Siempre visible en el riel, fuera de los bloques plegables y del formulario de inicio. */
export default function ControlesCorrida({ ejecucion, ocupado, alControlar, progreso }) {
  const [confirmar, setConfirmar] = useState(false);
  const [pendiente, setPendiente] = useState(null);
  const dialogo = useRef(null);
  const accionRef = useRef(null);
  const tituloId = useId(), descripcionId = useId();
  const controles = controlesDeCorrida(ejecucion?.estado);

  useEffect(() => {
    if (confirmar && controles.activa) dialogo.current?.showModal();
    else dialogo.current?.close();
  }, [confirmar, controles.activa]);

  useEffect(() => { setConfirmar(false); }, [ejecucion?.id]);

  async function ejecutar(accion) {
    if (accionRef.current || ocupado) return;
    accionRef.current = accion;
    setPendiente(accion);
    // Cerrar antes de la petición para que el cambio de estado sea visible en su contexto.
    setConfirmar(false);
    try { await alControlar(accion); }
    finally { accionRef.current = null; setPendiente(null); }
  }

  if (!ejecucion) return null;
  const bloqueado = ocupado || !!pendiente;
  return <section className="controles-corrida" aria-label="Control de la corrida" aria-busy={!!pendiente}>
    <div className="controles-cabecera">
      <b>Control de la corrida</b>
      <span className={`control-estado control-${ejecucion.estado.toLowerCase()}`} role="status">
        {pendiente ? 'Aplicando…' : ejecucion.estado === 'CANCELADA' ? 'Detenida' : ETIQUETAS[ejecucion.estado]}
      </span>
    </div>
    {controles.activa && <div className="controles-botones">
      <button type="button" className="btn control-principal" disabled={bloqueado || !(controles.pausar || controles.reanudar)}
        onClick={() => ejecutar(controles.reanudar ? 'reanudar' : 'pausar')}>
        <IconoUI nombre={controles.reanudar ? 'reproducir' : 'pausa'} size={18} />
        {pendiente === 'pausar' ? 'Pausando…' : pendiente === 'reanudar' ? 'Reanudando…'
          : controles.reanudar ? 'Reanudar' : 'Pausar'}
      </button>
      <button type="button" className="btn control-detener" disabled={bloqueado}
        onClick={() => setConfirmar(true)}>
        <IconoUI nombre="detener" size={16} />{pendiente === 'cancelar' ? 'Deteniendo…' : 'Detener corrida'}
      </button>
    </div>}
    {ejecucion.ritmo && <>
      <progress className="control-progreso" max="100" value={Math.max(0, Math.min(100, progreso * 100))}
        aria-label="Progreso de la corrida" />
      <small>Ventana {numero(ejecucion.ritmo.ventana)} de {numero(ejecucion.ritmo.ventanas)}</small>
    </>}
    <p className="control-ayuda">{controles.reanudar
      ? 'La corrida está pausada para todos los dispositivos. Reanuda para continuar desde el mismo punto.'
      : controles.activa ? 'Pausar conserva el avance. Detener finaliza esta corrida para todos los dispositivos.'
        : ejecucion.estado === 'CANCELADA' ? 'Corrida detenida. Puedes consultar el historial disponible o iniciar una nueva.'
          : 'Corrida finalizada. Puedes consultar sus resultados o iniciar una nueva.'}</p>
    <dialog ref={dialogo} className="confirmar-detencion" aria-labelledby={tituloId} aria-describedby={descripcionId}
      onCancel={() => setConfirmar(false)} onClose={() => setConfirmar(false)}>
      <h2 id={tituloId}>¿Detener esta corrida?</h2>
      <p id={descripcionId}>Se detendrá para todos los dispositivos y no podrás reanudarla.
        Podrás consultar el historial disponible e iniciar una nueva corrida.</p>
      <div className="confirmar-acciones">
        <button type="button" className="btn control-principal" autoFocus onClick={() => setConfirmar(false)}>
          Volver a la corrida</button>
        <button type="button" className="btn control-detener-final" onClick={() => ejecutar('cancelar')}>
          Sí, detener corrida</button>
      </div>
    </dialog>
  </section>;
}
