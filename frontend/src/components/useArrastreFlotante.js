import { useEffect, useRef, useState } from 'react';

const limitar = (valor, minimo, maximo) => Math.max(minimo, Math.min(maximo, valor));

// El encuadre de los controles se limita al área visible del mapa, sin mover el SVG.
export default function useArrastreFlotante() {
  const elemento = useRef(null);
  const gesto = useRef(null);
  const movido = useRef(false);
  const [desplazamiento, setDesplazamiento] = useState({ x: 0, y: 0 });

  useEffect(() => {
    const ajustar = () => {
      const rect = elemento.current?.getBoundingClientRect();
      const mapa = document.querySelector('.mapsec')?.getBoundingClientRect();
      if (!rect || !mapa) return;
      setDesplazamiento(actual => ({
        x: actual.x + limitar(rect.x, mapa.left, mapa.right - rect.width) - rect.x,
        y: actual.y + limitar(rect.y, mapa.top, mapa.bottom - rect.height) - rect.y
      }));
    };
    window.addEventListener('resize', ajustar);
    return () => window.removeEventListener('resize', ajustar);
  }, []);

  function onPointerDown(evento) {
    if (evento.button !== 0) return;
    const rect = elemento.current?.getBoundingClientRect();
    const mapa = document.querySelector('.mapsec')?.getBoundingClientRect();
    if (!rect || !mapa) return;
    movido.current = false;
    gesto.current = { clientX: evento.clientX, clientY: evento.clientY,
      x: desplazamiento.x, y: desplazamiento.y, rect, mapa };
    evento.currentTarget.setPointerCapture(evento.pointerId);
  }

  function onPointerMove(evento) {
    const inicio = gesto.current;
    if (!inicio) return;
    const dx = evento.clientX - inicio.clientX;
    const dy = evento.clientY - inicio.clientY;
    if (Math.hypot(dx, dy) > 4) movido.current = true;
    if (!movido.current) return;
    setDesplazamiento({
      x: inicio.x + limitar(inicio.rect.x + dx, inicio.mapa.left,
        inicio.mapa.right - inicio.rect.width) - inicio.rect.x,
      y: inicio.y + limitar(inicio.rect.y + dy, inicio.mapa.top,
        inicio.mapa.bottom - inicio.rect.height) - inicio.rect.y
    });
  }

  function onPointerUp() { gesto.current = null; }
  function consumeClick() {
    const fueArrastre = movido.current;
    movido.current = false;
    return fueArrastre;
  }

  return { ref: elemento, style: { translate: `${desplazamiento.x}px ${desplazamiento.y}px` },
    onPointerDown, onPointerMove, onPointerUp, onPointerCancel: onPointerUp, consumeClick };
}
