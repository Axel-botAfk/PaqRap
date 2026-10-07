import React, { useMemo, useState } from 'react';
import CargaMasiva from '../components/CargaMasiva.jsx';
import { Kpi } from '../components/Kpis.jsx';
import { fecha, numero, SIN_DATO, SIN_DATO_CORTO } from '../services/formato.js';

/* Pedidos de la corrida activa: los del plan vigente y los que quedaron sin asignar.
   Lectura solamente; el registro individual de pedidos entra en un avance posterior. */

const PLAZOS = [4, 8, 12, 18, 36];

/** Horas entre el registro y el límite: es el campo hl del archivo de ventas. */
function horasDePlazo(pedido) {
  if (!pedido.fechaRegistro || !pedido.fechaLimite) return null;
  const ms = new Date(pedido.fechaLimite).getTime() - new Date(pedido.fechaRegistro).getTime();
  return Number.isFinite(ms) ? ms / 3600000 : null;
}

/** Lo que falta para el plazo, contra el reloj simulado, como «04:12». */
function restante(pedido, reloj) {
  if (!reloj || !pedido.fechaLimite) return null;
  const ms = new Date(pedido.fechaLimite).getTime() - new Date(reloj).getTime();
  if (!Number.isFinite(ms)) return null;
  if (ms < 0) return { texto: 'vencido', clase: 'r' };
  const horas = Math.floor(ms / 3600000);
  const minutos = Math.floor((ms % 3600000) / 60000);
  const texto = `${String(horas).padStart(2, '0')}:${String(minutos).padStart(2, '0')}`;
  return { texto, clase: horas < 1 ? 'r' : horas < 4 ? 'a' : 'v' };
}

export default function Pedidos({ ejecucion, alIrASimulacion }) {
  const [estado, setEstado] = useState('');
  const [plazo, setPlazo] = useState('');
  const [pestana, setPestana] = useState('listado');

  const filas = useMemo(() => [
    ...(ejecucion?.rutas || []).flatMap(ruta => ruta.paradas.map(pedido =>
      ({ ...pedido, ruta: ruta.id, unidad: ruta.vehiculoId, almacen: ruta.almacenId }))),
    ...(ejecucion?.noAsignados || []).map(pedido => ({ ...pedido, ruta: null }))
  ], [ejecucion]);

  const visibles = useMemo(() => filas.filter(fila => {
    if (estado === 'plan' && !fila.ruta) return false;
    if (estado === 'sin' && fila.ruta) return false;
    if (plazo) {
      const horas = horasDePlazo(fila);
      if (horas === null || Math.abs(horas - Number(plazo)) > 0.5) return false;
    }
    return true;
  }), [filas, estado, plazo]);

  const sinAsignar = filas.filter(f => !f.ruta).length;

  return <>
    <div className="kpis">
      <Kpi etiqueta="EN EL PLAN VIGENTE" color="var(--auto)" nota="Paradas asignadas a una ruta"
        valor={ejecucion ? numero(filas.length - sinAsignar) : SIN_DATO} />
      <Kpi etiqueta="SIN ASIGNAR" color="var(--red)" nota="Última planificación"
        valor={ejecucion ? numero(sinAsignar) : SIN_DATO} />
      <Kpi etiqueta="ENTREGADOS" color="var(--verde)" nota="Acumulado de la corrida"
        valor={numero(ejecucion?.avance?.pedidosEntregados, SIN_DATO)} />
      <Kpi etiqueta="VENCIDOS" color="var(--ambar)" nota="Fuera del plazo comprometido"
        valor={numero(ejecucion?.avance?.pedidosVencidos, SIN_DATO)} />
    </div>

    <div className="tabs">
      <button type="button" data-on={pestana === 'listado' || undefined}
        onClick={() => setPestana('listado')}>Pedidos registrados</button>
      <button type="button" data-on={pestana === 'carga' || undefined}
        onClick={() => setPestana('carga')}>Carga masiva del mes</button>
    </div>

    {pestana === 'carga' ? <CargaMasiva /> : <div className="card">
      <header>
        <h2>Pedidos registrados</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <select value={estado} onChange={e => setEstado(e.target.value)}
            aria-label="Filtrar por estado"
            style={{ padding: '5px 8px', borderRadius: 6, border: '1px solid var(--line-strong)', fontSize: 12 }}>
            <option value="">Todo estado</option>
            <option value="plan">Planificado</option>
            <option value="sin">Sin asignar</option>
          </select>
          <select value={plazo} onChange={e => setPlazo(e.target.value)}
            aria-label="Filtrar por plazo"
            style={{ padding: '5px 8px', borderRadius: 6, border: '1px solid var(--line-strong)', fontSize: 12 }}>
            <option value="">Todo plazo (hl)</option>
            {PLAZOS.map(h => <option key={h} value={h}>{h} h</option>)}
          </select>
        </div>
      </header>

      <div className="body sin-aire">
        {!ejecucion
          ? <p className="vacio">
            No hay una corrida activa.<br />
            <button type="button" className="btn" style={{ marginTop: 10 }}
              onClick={alIrASimulacion}>CONFIGURAR UNA CORRIDA</button>
          </p>
          : visibles.length === 0
            ? <p className="vacio">Ningún pedido coincide con los filtros elegidos.</p>
            : <div className="tabla-caja"><table>
              <thead><tr>
                <th>Código</th><th>Nodo (x, y)</th><th>qq</th><th>hl</th>
                <th>Plazo restante</th><th>Almacén</th><th>Unidad</th><th>Estado</th>
              </tr></thead>
              <tbody>{visibles.slice(0, 200).map((fila, indice) => {
                const falta = restante(fila, ejecucion.reloj);
                const horas = horasDePlazo(fila);
                return <tr key={`${fila.id}-${indice}`}>
                  <td className="mono">{fila.idOriginal || fila.id}</td>
                  <td className="mono">({fila.destino?.x}, {fila.destino?.y})</td>
                  <td>{numero(fila.cantidad)}</td>
                  <td>{horas === null ? SIN_DATO_CORTO : `${Math.round(horas)} h`}</td>
                  <td>{falta
                    ? <span className={`pill ${falta.clase}`}>{falta.texto}</span>
                    : SIN_DATO_CORTO}</td>
                  <td>{fila.almacen || SIN_DATO_CORTO}</td>
                  <td className="mono">{fila.unidad || SIN_DATO_CORTO}</td>
                  <td>{fila.ruta
                    ? <span className="pill n">Planificado</span>
                    : <span className="pill r" title={fila.motivo || ''}>Sin asignar</span>}</td>
                </tr>;
              })}</tbody>
            </table>
              {visibles.length > 200 && <p className="nota" style={{ padding: '0 16px 12px' }}>
                Se muestran los primeros 200 de {numero(visibles.length)} registros de esta instantánea.
              </p>}
            </div>}
      </div>
    </div>}

    <p className="nota muted" style={{ maxWidth: '86ch' }}>
      Un pedido con más unidades que la capacidad de una unidad se atiende en entregas parciales,
      que pueden repartirse entre varias unidades o viajes; se completa solo cuando la cantidad
      total se entrega dentro del plazo. Los entregados se contabilizan en el avance, no en esta
      lista. El seguimiento fracción por fracción todavía no lo expone la API.
    </p>
  </>;
}
