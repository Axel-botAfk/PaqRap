import React, { useEffect, useRef, useState } from 'react';

const limitar = (valor, minimo, maximo) => Math.max(minimo, Math.min(maximo, valor));

function leerRecordado(clave) {
  try {
    const valor = JSON.parse(localStorage.getItem(clave));
    return valor && typeof valor === 'object' ? valor : null;
  } catch { return null; }
}

function recordar(clave, valor) {
  try { localStorage.setItem(clave, JSON.stringify(valor)); } catch { /* sin almacenamiento */ }
}

/**
 * Indicador flotante sobre el mapa: se arrastra desde su cabecera y se pliega con la flecha,
 * abierto o plegado. Mientras no se mueve conserva el lugar que le da el CSS; al soltarlo queda
 * donde se dejó, siempre dentro del lienzo. Doble clic en la cabecera lo devuelve a su sitio.
 * El lugar y el pliegue se recuerdan en este navegador.
 */
export default function PanelFlotante({ id, titulo, icono = null, resumen = null,
  abiertoInicial = true, className = '', children }) {
  const clave = `paqrap.panel.${id}`;
  const recordado = useRef(undefined);
  if (recordado.current === undefined) recordado.current = leerRecordado(clave);
  const [abierto, setAbierto] = useState(recordado.current?.abierto ?? abiertoInicial);
  const [lugar, setLugar] = useState(recordado.current?.lugar ?? null);
  const panel = useRef(null);
  const gesto = useRef(null);

  useEffect(() => { recordar(clave, { abierto, lugar }); }, [clave, abierto, lugar]);

  function encajar(x, y) {
    const elemento = panel.current;
    const lienzo = elemento?.offsetParent;
    if (!elemento || !lienzo) return { x, y };
    return { x: Math.round(limitar(x, 0, Math.max(0, lienzo.clientWidth - elemento.offsetWidth))),
      y: Math.round(limitar(y, 0, Math.max(0, lienzo.clientHeight - elemento.offsetHeight))) };
  }

  // Al plegar, desplegar o cambiar el tamaño de la ventana, el panel no debe salirse del mapa.
  const movido = lugar !== null;
  useEffect(() => {
    if (!movido) return undefined;
    const ajustar = () => setLugar(actual => {
      if (!actual) return actual;
      const nuevo = encajar(actual.x, actual.y);
      return nuevo.x === actual.x && nuevo.y === actual.y ? actual : nuevo;
    });
    ajustar();
    window.addEventListener('resize', ajustar);
    return () => window.removeEventListener('resize', ajustar);
  }, [abierto, movido]);

  function empezar(evento) {
    if (evento.button !== 0 || evento.target.closest('button')) return;
    const elemento = panel.current;
    gesto.current = { clientX: evento.clientX, clientY: evento.clientY,
      x: elemento.offsetLeft, y: elemento.offsetTop, arrastrando: false };
    evento.currentTarget.setPointerCapture(evento.pointerId);
  }

  function mover(evento) {
    const inicio = gesto.current;
    if (!inicio) return;
    const dx = evento.clientX - inicio.clientX;
    const dy = evento.clientY - inicio.clientY;
    if (!inicio.arrastrando && Math.hypot(dx, dy) <= 4) return;
    inicio.arrastrando = true;
    setLugar(encajar(inicio.x + dx, inicio.y + dy));
  }

  const terminar = () => { gesto.current = null; };

  return <section ref={panel} aria-label={typeof titulo === 'string' ? titulo : undefined}
    className={`panel-flotante ${abierto ? '' : 'panel-flotante-plegado'} ${movido ? 'panel-flotante-movido' : ''} ${className}`}
    style={movido ? { left: lugar.x, top: lugar.y, right: 'auto', bottom: 'auto' } : undefined}>
    <header onPointerDown={empezar} onPointerMove={mover} onPointerUp={terminar}
      onPointerCancel={terminar} onDoubleClick={evento => {
        if (!evento.target.closest('button')) setLugar(null);
      }} title="Arrastra para mover · doble clic para devolverlo a su lugar">
      <span className="panel-flotante-asa" aria-hidden="true" />
      {icono}
      <strong>{titulo}</strong>
      {!abierto && resumen && <span className="panel-flotante-resumen">{resumen}</span>}
      <button type="button" className="panel-flotante-plegar" aria-expanded={abierto}
        aria-label={abierto ? `Plegar ${titulo}` : `Desplegar ${titulo}`}
        onClick={() => setAbierto(!abierto)}><i aria-hidden="true" /></button>
    </header>
    {abierto && <div className="panel-flotante-cuerpo">{children}</div>}
  </section>;
}
