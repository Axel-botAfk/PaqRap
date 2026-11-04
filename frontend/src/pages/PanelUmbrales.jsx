import React, { useEffect, useState } from 'react';
import { normalizarUmbrales, UMBRALES_POR_OMISION } from '../umbrales.js';

/**
 * Opción del sistema para los cortes del semáforo (B06).
 *
 * Antes vivían como literales en `carga.js` y `stock.js`. Aquí se editan y quedan guardados en
 * este navegador; el mapa, el panel de unidades, el inventario y los indicadores globales usan
 * los mismos valores, de modo que todos los semáforos de la pantalla cambian a la vez.
 *
 * El formulario no acepta un corte bajo por encima del alto: eso dejaría el semáforo sin banda
 * intermedia, así que se avisa y no se guarda.
 */
const CAMPOS = [
  ['carga', 'baja', 'Carga baja bajo (%)', 'Por debajo de este porcentaje la unidad va en rojo.'],
  ['carga', 'intermedia', 'Carga media bajo (%)', 'Hasta aquí va en ámbar; de aquí en adelante, en verde.'],
  ['stock', 'critico', 'Stock crítico bajo (%)', 'Por debajo de este porcentaje el almacén va en rojo.'],
  ['stock', 'advertencia', 'Stock en advertencia bajo (%)', 'Hasta aquí va en ámbar; de aquí en adelante, en verde.']
];

export default function PanelUmbrales({ umbrales, alGuardar, alRestablecer }) {
  const [borrador, setBorrador] = useState(umbrales);
  const [aviso, setAviso] = useState('');

  // Si los umbrales cambian desde fuera (al restablecer), el formulario los sigue.
  useEffect(() => { setBorrador(umbrales); setAviso(''); }, [umbrales]);

  const cambiar = (grupo, clave, valor) => setBorrador(actual => ({
    ...actual, [grupo]: { ...actual[grupo], [clave]: valor === '' ? '' : Number(valor) }
  }));

  function enviar(evento) {
    evento.preventDefault();
    const normalizados = normalizarUmbrales(borrador);
    const invertido = (grupo, menor, mayor) => Number(borrador[grupo]?.[menor]) >= Number(borrador[grupo]?.[mayor]);
    if (invertido('carga', 'baja', 'intermedia') || invertido('stock', 'critico', 'advertencia')) {
      setAviso('El corte bajo tiene que ser menor que el alto. No se guardó nada.');
      return;
    }
    setAviso('Umbrales guardados; todos los semáforos de la pantalla ya los usan.');
    alGuardar(normalizados);
  }

  const porOmision = JSON.stringify(umbrales) === JSON.stringify(UMBRALES_POR_OMISION);

  return <form onSubmit={enviar}>
    {CAMPOS.map(([grupo, clave, etiqueta, ayuda]) => <div key={`${grupo}-${clave}`} className="field">
      <label htmlFor={`u-${grupo}-${clave}`}>{etiqueta}</label>
      <input id={`u-${grupo}-${clave}`} className="input" type="number" min="1" max="99" step="1"
        required value={borrador[grupo]?.[clave] ?? ''}
        onChange={e => cambiar(grupo, clave, e.target.value)} />
      <p className="nota">{ayuda}</p>
    </div>)}

    <div className="acciones">
      <button className="btn btn-primary" type="submit">Guardar umbrales</button>
      <button className="btn" type="button" disabled={porOmision}
        onClick={alRestablecer}>Restablecer</button>
    </div>
    {aviso && <p className="nota nota-aviso" role="status">{aviso}</p>}
    <p className="nota">Valores del caso: carga 40 % y 70 %; stock 20 % y 60 %. Se guardan en este
      navegador, no en el servidor, así que cada visualizador conserva los suyos.</p>
  </form>;
}
