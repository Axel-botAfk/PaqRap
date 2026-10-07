import React from 'react';
import { Kpi } from '../components/Kpis.jsx';
import { numero, SIN_DATO } from '../services/formato.js';

/* Averías de la flota.

   Las averías son el único evento fortuito de la operación; los bloqueos de calles son
   planificados, vienen en el archivo mensual con vigencia horaria y expiran solos, así que
   no se gestionan desde aquí.

   Esta pantalla lee el estado de las unidades de la corrida activa (AVERIADO y
   EN_MANTENIMIENTO ya vienen en VehiculoVista). Registrar una avería a mano requiere un
   endpoint de escritura que todavía no existe. */

const REGLAS = [
  ['T1', 'Menor', '2 horas de indisponibilidad; la unidad se reincorpora automáticamente al vencer el periodo.'],
  ['T2', 'Intermedia', 'Indisponible hasta el final del turno siguiente a aquel en que ocurrió.'],
  ['T3', 'Mayor', 'Al menos 2 días de mantenimiento; retorna a operación en el turno de 15:00 a 23:00.']
];

export default function Averias({ ejecucion, alIrASimulacion, alIrAOperacion }) {
  const vehiculos = ejecucion?.vehiculos || [];
  const averiadas = vehiculos.filter(v => v.estado === 'AVERIADO');
  const enMantenimiento = vehiculos.filter(v => v.estado === 'EN_MANTENIMIENTO');
  const fuera = [...averiadas, ...enMantenimiento];

  return <>
    <div className="kpis">
      <Kpi etiqueta="UNIDADES AVERIADAS" color="var(--red)" nota="Fuera de servicio por avería"
        valor={ejecucion ? numero(averiadas.length) : SIN_DATO} />
      <Kpi etiqueta="EN MANTENIMIENTO" color="var(--ambar)" nota="Mantenimiento preventivo programado"
        valor={ejecucion ? numero(enMantenimiento.length) : SIN_DATO} />
      <Kpi etiqueta="UNIDADES OPERATIVAS" color="var(--verde)" nota="Disponibles o en ruta"
        valor={ejecucion ? numero(vehiculos.length - fuera.length) : SIN_DATO} />
    </div>

    <div className="card">
      <header>
        <h2>Unidades fuera de servicio</h2>
        {fuera.length > 0 && <button type="button" className="btn" onClick={alIrAOperacion}>
          VER EN EL MAPA
        </button>}
      </header>
      <div className="body sin-aire">
        {!ejecucion
          ? <p className="vacio">
            No hay una corrida activa.<br />
            <button type="button" className="btn" style={{ marginTop: 10 }}
              onClick={alIrASimulacion}>CONFIGURAR UNA CORRIDA</button>
          </p>
          : fuera.length === 0
            ? <p className="vacio">Ninguna unidad está fuera de servicio en la instantánea actual.</p>
            : <div className="tabla-caja"><table>
              <thead><tr>
                <th>Unidad</th><th>Tipo</th><th>Nodo (x, y)</th><th>Carga a bordo</th><th>Situación</th>
              </tr></thead>
              <tbody>{fuera.map(vehiculo => <tr key={vehiculo.id}>
                <td className="mono">{vehiculo.id}</td>
                <td>{vehiculo.tipo}</td>
                <td className="mono">({vehiculo.ubicacion?.x}, {vehiculo.ubicacion?.y})</td>
                <td>{numero(vehiculo.cargaAbordo)} / {numero(vehiculo.capacidad)}</td>
                <td>{vehiculo.estado === 'AVERIADO'
                  ? <span className="pill r">Averiada</span>
                  : <span className="pill a">Mantenimiento</span>}</td>
              </tr>)}</tbody>
            </table></div>}
      </div>
    </div>

    <div className="card">
      <header><h2>Reglas de indisponibilidad</h2></header>
      <div className="body">
        <dl className="resumen">
          {REGLAS.map(([codigo, nombre, regla]) => <div key={codigo}
            style={{ alignItems: 'flex-start' }}>
            <dt style={{ flex: '0 0 92px', fontWeight: 800, color: 'var(--ink)' }}>
              {codigo} · {nombre}
            </dt>
            <dd style={{ fontWeight: 400, fontSize: 11.5, textAlign: 'right', lineHeight: 1.45 }}>
              {regla}
            </dd>
          </div>)}
        </dl>
        <p className="nota">
          En los tipos 2 y 3 la unidad permanece en el nodo hasta 4 h y luego es trasladada de
          forma instantánea al almacén central con los paquetes no trasvasados. Cada trasvase
          suma 30 minutos a la ruta que lo recibe. El planificador aplica estas ventanas con los
          turnos oficiales de 07:00, 15:00 y 23:00.
        </p>
      </div>
    </div>

    <div className="card">
      <header><h2>Registrar una avería</h2></header>
      <div className="body">
        <div className="aviso aviso-ambar">
          <b>PENDIENTE</b>
          <span>
            Registrar una avería a mano y disparar la reasignación necesita un endpoint de
            escritura que la API todavía no tiene. Las averías que se ven arriba son las que el
            simulador genera durante la corrida. El contrato que falta está anotado en{' '}
            <span className="mono">docs/api-pendiente.md</span>.
          </span>
        </div>
        <p className="nota">
          Los bloqueos de calles no se gestionan aquí: son planificados, llegan en el archivo
          mensual con su vigencia horaria y dejan de aplicar solos cuando esa vigencia termina.
        </p>
      </div>
    </div>
  </>;
}
