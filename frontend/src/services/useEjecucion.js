import { useEffect, useState } from 'react';
import { api, esFinal, urlWebSocket } from './api.js';

/* Conexión con una ejecución del planificador.

   El canal principal es el WebSocket: el Simulador publica un evento por cada avance
   del reloj simulado. Encima corre una consulta REST cada 5 s como respaldo, porque el
   contrato del backend dice que ante desconexión hay que volver a preguntar por el
   mismo identificador.

   Cuando el canal en vivo se corta, la vista queda marcada como desactualizada en lugar
   de mostrar datos como si fueran actuales (25.dis.gui.v2 seccion 8). */

const INTERVALO_RESPALDO = 5000;

export default function useEjecucion(id) {
  const [ejecucion, setEjecucion] = useState(null);
  const [error, setError] = useState('');
  const [enVivo, setEnVivo] = useState(false);
  const [desactualizado, setDesactualizado] = useState(false);

  useEffect(() => {
    if (!id) {
      setEjecucion(null);
      setEnVivo(false);
      setDesactualizado(false);
      return undefined;
    }

    let activo = true;
    let socket;
    let intervalo;

    const recibir = vista => {
      if (!activo) return;
      setEjecucion(vista);
      setDesactualizado(false);
      if (esFinal(vista.estado) && intervalo) {
        clearInterval(intervalo);
        intervalo = undefined;
      }
    };

    const consultar = () => api.consultar(id).then(recibir).catch(e => {
      if (activo) setError(`No se pudo recuperar la ejecución: ${e.message}`);
    });

    consultar();
    intervalo = setInterval(consultar, INTERVALO_RESPALDO);

    try {
      socket = new WebSocket(urlWebSocket(id));
      socket.onopen = () => { if (activo) { setEnVivo(true); setDesactualizado(false); } };
      socket.onmessage = evento => {
        try {
          const mensaje = JSON.parse(evento.data);
          if (mensaje.ejecucion) recibir(mensaje.ejecucion);
        } catch {
          /* Un evento que no se puede leer no invalida la vista: el respaldo REST sigue. */
        }
      };
      socket.onclose = () => {
        if (!activo) return;
        setEnVivo(false);
        /* La siguiente consulta REST que llegue lo vuelve a poner en falso. */
        setDesactualizado(true);
      };
      socket.onerror = () => { if (activo) { setEnVivo(false); setDesactualizado(true); } };
    } catch {
      setEnVivo(false);
      setDesactualizado(true);
    }

    return () => {
      activo = false;
      if (intervalo) clearInterval(intervalo);
      socket?.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  return { ejecucion, setEjecucion, error, setError, enVivo, desactualizado };
}
