import React from 'react';
import InventarioAlmacenes from '../components/InventarioAlmacenes.jsx';
import { decimal, fecha, numero, SIN_DATO } from '../services/formato.js';

/* Operación día a día: seguimiento de la jornada en curso.

   A diferencia de Simulación, esta vista no lleva tarjeta de configuración sobre el mapa:
   su panel va en el riel derecho, encima de las rutas, para que la retícula se vea entera.
   La corrida se lanza desde Simulación. */

export default function OperacionDiaria({ ejecucion, alIrASimulacion }) {
  const avance = ejecucion?.avance;

  return <section className="float jornada">
    <header><h2>Jornada en curso</h2></header>

    <div className="body">
      {!ejecucion
        ? <>
          <p className="vacio">No hay una corrida activa.</p>
          <button type="button" className="btn btn-primario" style={{ width: '100%' }}
            onClick={alIrASimulacion}>CONFIGURAR UNA CORRIDA</button>
        </>
        : <>
          <dl className="resumen">
            <div><dt>Escenario</dt><dd>{ejecucion.escenario}</dd></div>
            <div><dt>Algoritmo</dt><dd>{ejecucion.algoritmo}</dd></div>
            <div><dt>Reloj simulado</dt><dd className="mono">{fecha(ejecucion.reloj)}</dd></div>
            <div><dt>Pedidos en cola</dt><dd>{numero(avance?.pedidosEnCola, SIN_DATO)}</dd></div>
            <div><dt>Productos a bordo</dt><dd>{numero(avance?.productosAbordo, SIN_DATO)}</dd></div>
            <div><dt>Productos vencidos</dt><dd>{numero(avance?.productosVencidos, SIN_DATO)}</dd></div>
            <div><dt>Cálculo del plan</dt>
              <dd>{decimal(avance ? avance.milisegundosPlanificacion / 1000 : undefined, ' s')}</dd></div>
          </dl>
          <InventarioAlmacenes />
        </>}
    </div>
  </section>;
}
