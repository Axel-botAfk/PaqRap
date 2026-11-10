import React, { useEffect, useMemo, useRef } from 'react';
import { ultimoPlanEstable, reporteDePlan, reporteCsv } from '../reporte.js';
import { fecha, numero, decimal, minutos, tipoVehiculo, motivoPedido, ETIQUETAS } from '../formato.js';

const ESCENARIOS = {
  OPERACION_DIARIA: 'Operación día a día',
  PERIODO_5_DIAS: 'Simulación de 5 días',
  COLAPSO: 'Simulación hasta el colapso'
};

/**
 * Reporte de la última planificación estable (G08 a G10), sobre el mapa.
 *
 * Se abre solo cuando la corrida termina y queda disponible desde «Resumen de la corrida». En
 * un colapso el plan estable es el último vigente hasta el primer incumplimiento. Se puede
 * imprimir o descargar como CSV para adjuntarlo como evidencia.
 */
export default function ReportePlan({ ejecucion, planes, alCerrar }) {
  const dialogo = useRef(null);
  const colapso = ejecucion?.colapso || null;
  const finalizada = ['TERMINADA', 'COLAPSADA', 'CANCELADA', 'FALLIDA'].includes(ejecucion?.estado);
  const reporte = useMemo(() => reporteDePlan(
    ultimoPlanEstable(planes, colapso?.instante || null)), [planes, colapso?.instante]);
  const resumen = ejecucion?.resumen || null;

  // La pantalla se redibuja varias veces por segundo: el foco y la tecla Escape se instalan
  // una sola vez para no quitarle el foco a los botones en cada latido.
  const cerrar = useRef(alCerrar);
  cerrar.current = alCerrar;
  useEffect(() => {
    dialogo.current?.focus();
    const alTeclear = evento => { if (evento.key === 'Escape') cerrar.current(); };
    window.addEventListener('keydown', alTeclear);
    return () => window.removeEventListener('keydown', alTeclear);
  }, []);

  function descargar() {
    const url = URL.createObjectURL(new Blob([reporteCsv(reporte)], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `ultima-planificacion-${ejecucion.id}.csv`;
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return <div className="reporte-fondo" onClick={alCerrar}>
    <section className="reporte" role="dialog" aria-modal="true" aria-labelledby="reporte-titulo"
      tabIndex={-1} ref={dialogo} onClick={evento => evento.stopPropagation()}>
      <header className="reporte-cabecera">
        <div>
          <h2 id="reporte-titulo">Reporte de la última planificación estable</h2>
          <p>{ESCENARIOS[ejecucion.escenario] || ejecucion.escenario} · {ETIQUETAS[ejecucion.estado] || ejecucion.estado}
            {' '}· inicio {fecha(ejecucion.inicio)}{resumen?.fin ? ` · fin ${fecha(resumen.fin)}` : ''}</p>
        </div>
        <div className="reporte-acciones">
          <button type="button" className="btn btn-secondary" disabled={!reporte} onClick={descargar}>Descargar CSV</button>
          <button type="button" className="btn btn-secondary" disabled={!reporte} onClick={() => window.print()}>Imprimir</button>
          <button type="button" className="btn btn-primary" onClick={alCerrar}>Cerrar</button>
        </div>
      </header>

      <div className="reporte-cuerpo">
        {!finalizada && <p className="reporte-aviso" role="status">La corrida sigue en marcha: este es
          el plan vigente por ahora y puede ser reemplazado por una replanificación. El reporte
          definitivo se presenta al finalizar.</p>}

        {colapso && <div className="reporte-colapso">
          <b>Colapso logístico a las {fecha(colapso.instante)}</b>
          <span>Primer pedido vencido: {colapso.pedido?.id || 'no disponible'} · ventana {numero(colapso.ventana)}
            {' '}· {minutos(colapso.milisegundosReales / 60000)} reales · entregados {numero(colapso.pedidosEntregados)},
            vencidos {numero(colapso.pedidosVencidos)}, en cola {numero(colapso.pedidosEnCola)} ·
            unidades disponibles {numero(colapso.vehiculosDisponibles)}, averiadas {numero(colapso.vehiculosAveriados)}.
            El plan de abajo es el último vigente antes de ese instante.</span>
        </div>}

        {resumen && <>
          <h3>Resultado de la corrida</h3>
          <dl className="reporte-kpis">
            <div><dt>Pedidos recibidos</dt><dd>{numero(resumen.pedidosRecibidos)}</dd></div>
            <div><dt>Completos / en plazo</dt><dd>{numero(resumen.pedidosOriginalesCompletos)} / {numero(resumen.pedidosOriginalesEnPlazo)}</dd></div>
            <div><dt>Pendientes</dt><dd>{numero(resumen.pedidosPendientes)}</dd></div>
            <div><dt>Productos recibidos</dt><dd>{numero(resumen.productosRecibidos)}</dd></div>
            <div><dt>Distancia total</dt><dd>{decimal(resumen.distanciaTotalKm, ' km')}</dd></div>
            <div><dt>Costo total</dt><dd>S/ {decimal(resumen.costoTotal)}</dd></div>
            <div><dt>Planificaciones</dt><dd>{numero(resumen.iteracionesPlanificacion)}</dd></div>
          </dl>
        </>}

        {!reporte ? <p className="vacio">Todavía no hay un plan publicado para esta corrida.</p> : <>
          <h3>Plan n.º {numero(reporte.indice + 1)} · calculado a las {fecha(reporte.reloj)}
            {reporte.hasta ? ` · vigente hasta ${fecha(reporte.hasta)}` : ''}</h3>
          <dl className="reporte-kpis">
            <div><dt>Rutas</dt><dd>{numero(reporte.totales.rutas)}</dd></div>
            <div><dt>Unidades</dt><dd>{numero(reporte.totales.unidades)}</dd></div>
            <div><dt>Pedidos / fracciones</dt><dd>{numero(reporte.totales.pedidos)}</dd></div>
            <div><dt>Productos</dt><dd>{numero(reporte.totales.productos)}</dd></div>
            <div><dt>Distancia</dt><dd>{decimal(reporte.totales.km, ' km')}</dd></div>
            <div><dt>Costo</dt><dd>S/ {decimal(reporte.totales.costo)}</dd></div>
            <div><dt>Sin asignar</dt><dd>{numero(reporte.noAsignados.length)}</dd></div>
          </dl>

          {reporte.rutas.length === 0
            ? <p className="vacio">El último plan no tiene rutas: no quedaban pedidos por despachar.</p>
            : <div className="tabla-wrap reporte-tabla"><table>
              <thead><tr><th>Ruta</th><th>Unidad</th><th>Almacén</th><th>Salida</th><th>N.º</th>
                <th>Pedido / fracción</th><th>Destino</th><th>Productos</th><th>Llegada</th>
                <th>Plazo</th><th>Situación</th></tr></thead>
              {reporte.rutas.map(ruta => <tbody key={`${ruta.id}-${ruta.unidad}`}>
                {ruta.paradas.map((p, i) => <tr key={p.id}>
                  {i === 0 && <>
                    <td rowSpan={ruta.paradas.length}><b>{ruta.id}</b><small>{decimal(ruta.km, ' km')} · S/ {decimal(ruta.costo)}</small></td>
                    <td rowSpan={ruta.paradas.length}><b>{ruta.unidad}</b><small>{tipoVehiculo(ruta.tipo)} · carga {numero(ruta.carga)}</small></td>
                    <td rowSpan={ruta.paradas.length}>{ruta.almacen}</td>
                    <td rowSpan={ruta.paradas.length}>{fecha(ruta.salida)}</td>
                  </>}
                  <td>{p.orden}</td>
                  <td><b>{p.id}</b>{p.original !== p.id && <small>de {p.original}</small>}</td>
                  <td>({p.destino?.x}, {p.destino?.y})</td>
                  <td>{numero(p.cantidad)}</td>
                  <td>{fecha(p.llegada)}</td>
                  <td>{fecha(p.limite)}</td>
                  <td><span className={`pill ${p.entregado ? 'v' : p.enPlazo === false ? 'r' : 'n'}`}>
                    {p.entregado ? 'Entregado' : p.enPlazo === false ? 'Previsto fuera de plazo' : 'Previsto'}</span></td>
                </tr>)}
              </tbody>)}
            </table></div>}

          {reporte.noAsignados.length > 0 && <>
            <h3>Pedidos sin asignar en este plan</h3>
            <div className="tabla-wrap reporte-tabla"><table>
              <thead><tr><th>Pedido / fracción</th><th>Destino</th><th>Productos</th><th>Plazo</th><th>Motivo</th></tr></thead>
              <tbody>{reporte.noAsignados.map(p => <tr key={p.id}>
                <td><b>{p.id}</b></td><td>({p.destino?.x}, {p.destino?.y})</td>
                <td>{numero(p.cantidad)}</td><td>{fecha(p.limite)}</td>
                <td>{motivoPedido(p.motivo) || '—'}</td>
              </tr>)}</tbody>
            </table></div>
          </>}
          <p className="nota">«Entregado» es una entrega observada dentro de la vigencia del plan; el
            resto son llegadas previstas por el planificador. Corrida {ejecucion.id}.</p>
        </>}
      </div>
    </section>
  </div>;
}
