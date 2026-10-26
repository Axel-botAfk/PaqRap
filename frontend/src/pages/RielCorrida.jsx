import React from 'react';
import Acordeon from '../components/Acordeon.jsx';
import { fecha, numero, decimal, minutos, textoRitmo, ritmoConfigurado, colorDeRuta,
  tipoVehiculo, tipoAveria as etiquetaTipoAveria, estadoAveria } from '../formato.js';

const ESCENARIOS_SIMULACION = [
  ['PERIODO_5_DIAS', 'Semanal (5 días)', 'Cinco jornadas en 30 a 60 minutos reales.'],
  ['COLAPSO', 'Colapso logístico', 'Avanza hasta el primer pedido vencido.']
];

/**
 * Controles de la corrida, dentro del riel. Las dos vistas de mapa comparten este panel: la de
 * operación diaria fija el escenario y la de simulación deja elegir entre los otros dos.
 */
export default function RielCorrida({ vista, cfg, ejecucion, corriendo, ocupado, rutas,
  calculandoRutas, rutaSeleccionada, alSeleccionarRuta, inventario, mapaDatos, averias,
  aviso, progreso }) {
  const simulacion = vista === 'simulacion';
  const { escenario, detalle, dia, hora, horizonte, limites,
    unidadAveriada, tipoAveria, flota } = cfg;
  const inicialPorId = new Map((mapaDatos?.almacenes || [])
    .filter(a => a.stockInicial > 0).map(a => [a.id, a.stockInicial]));

  // Costo y distancia del plan vigente. El backend ya los publica por ruta (`RutaVista`); el
  // resumen con el costo total solo llega al terminar la corrida, y esto se ve mientras corre.
  const totalRutas = rutas.reduce((acumulado, ruta) => ({
    km: acumulado.km + (ruta.distanciaKm || 0),
    costo: acumulado.costo + (ruta.costo || 0)
  }), { km: 0, costo: 0 });

  return <>
    <Acordeon titulo={simulacion ? 'Tipo de simulación' : 'Configuración de la corrida'}>
      <form onSubmit={cfg.alIniciar}>
        {simulacion && <div className="radios">
          {ESCENARIOS_SIMULACION.map(([valor, titulo, ayuda]) => <label key={valor}>
            <input type="radio" name="escenario" value={valor} checked={escenario === valor}
              disabled={corriendo} onChange={() => cfg.setEscenario(valor)} />
            <span><b>{titulo}</b><small>{ayuda}</small></span>
          </label>)}
        </div>}

        {/* El periodo sale de la fecha: elegir el mes aparte era pedir dos veces lo mismo. */}
        <div className="row">
          <div className="field"><label htmlFor="c-dia">Fecha inicial</label>
            <input id="c-dia" className="input" name="dia" type="date" value={dia} required
              min={limites.desde} max={limites.hasta}
              onChange={e => cfg.setDia(e.target.value)} /></div>
          <div className="field"><label htmlFor="c-hora">Hora inicial</label>
            <input id="c-hora" className="input" name="hora" type="time" value={hora} required
              onChange={e => cfg.setHora(e.target.value)} /></div>
        </div>
        {detalle
          ? <p className="dato-periodo">{numero(detalle.pedidos)} pedidos · {numero(detalle.bloqueos)} bloqueos · {numero(detalle.jornadasMantenimiento)} jornadas de mantenimiento en {dia.slice(0, 7)}</p>
          : <p className="dato-periodo">Elige una fecha entre {limites.desde || '—'} y {limites.hasta || '—'}; los datos del mes se cargan solos.</p>}

        {escenario === 'COLAPSO' && <div className="field"><label htmlFor="c-hor">Horizonte máximo (días)</label>
          <input id="c-hor" className="input" type="number" min="1" max="60" value={horizonte} required
            onChange={e => cfg.setHorizonte(e.target.value)} /></div>}

        <p className="nota">Planificador: Vecino Más Cercano + Búsqueda Tabú. {ritmoConfigurado(escenario)}</p>

        <div className="acciones">
          <button className="btn btn-primary" type="submit" disabled={ocupado || !limites.desde}>
            {ocupado ? 'Procesando…' : '▶ Iniciar'}</button>
        </div>

        {ejecucion?.ritmo && <>
          <div className="bar"><i style={{ width: `${Math.round(progreso * 100)}%` }} /></div>
          <p className="nota">Ventana {numero(ejecucion.ritmo.ventana)} de {numero(ejecucion.ritmo.ventanas)} · {textoRitmo(ejecucion.ritmo)}</p>
        </>}
        {aviso && <p className="nota nota-aviso" role="status">{aviso}</p>}
      </form>
    </Acordeon>

    <Acordeon titulo="Rutas del instante" nota={numero(rutas.length)}>
      {calculandoRutas ? <p className="vacio" role="status">Calculando rutas…</p>
        : rutas.length === 0 ? <p className="vacio">No hay rutas planificadas para este instante.</p>
          : <div className="lista-rutas">{rutas.map((ruta, indice) => <button key={`${ruta.id}-${indice}`}
            type="button" className={`rt ${rutaSeleccionada === ruta.id ? 'rt-activa' : ''}`}
            style={{ '--ruta-color': colorDeRuta(ruta) }}
            onClick={() => alSeleccionarRuta(rutaSeleccionada === ruta.id ? null : ruta.id)}>
            <span className="l1"><b>{ruta.vehiculoId}</b><span className="num">{decimal(ruta.distanciaKm, ' km')}</span></span>
            <span className="l2"><span className="sub">{ruta.id} · {ruta.paradas.length} parada(s)</span>
              {typeof ruta.costo === 'number' && <span className="costo">S/ {decimal(ruta.costo)}</span>}</span>
          </button>)}</div>}
      {rutas.length > 0 && !calculandoRutas && <p className="nota nota-total">
        <span>Total del instante</span>
        <b>{decimal(totalRutas.km, ' km')} · S/ {decimal(totalRutas.costo)}</b></p>}
    </Acordeon>

    <Acordeon titulo="Registrar avería" abierto={false} nota={averias.length ? numero(averias.length) : null}>
      <form onSubmit={cfg.alRegistrarAveria}>
        <div className="field"><label htmlFor="a-unidad">Placa</label>
          <select id="a-unidad" className="input" value={unidadAveriada} required
            onChange={e => cfg.setUnidadAveriada(e.target.value)}>
            <option value="">Seleccionar vehículo</option>
            {flota.map(v => <option key={v.id} value={v.id}>{v.id} · {tipoVehiculo(v.tipo)}</option>)}
          </select>
        </div>
        <div className="field"><label htmlFor="a-tipo">Tipo de avería</label>
          <select id="a-tipo" className="input" value={tipoAveria} onChange={e => cfg.setTipoAveria(e.target.value)}>
            <option value="TIPO_1">Tipo 1 · reparación en sitio</option>
            <option value="TIPO_2">Tipo 2 · vuelve al central</option>
            <option value="TIPO_3">Tipo 3 · mantenimiento prolongado</option>
          </select>
        </div>
        <button className="btn btn-primary btn-block" type="submit" disabled={ocupado || !unidadAveriada || !corriendo}>Guardar</button>
        <p className="nota">El planificador gestiona la indisponibilidad y el traslado al almacén central según el tipo. Los pedidos afectados se replanifican en el siguiente corte.</p>
        {averias.length > 0 && <ul className="averias-lista">{averias.map(a => <li key={`${a.unidadId}-${a.instante}`}>
          {a.unidadId} · {etiquetaTipoAveria(a.tipo)} · {estadoAveria(a.estado)} · {fecha(a.instante)} · {numero(a.pedidosAfectados || 0)} pedido(s) replanificado(s)
        </li>)}</ul>}
      </form>
    </Acordeon>

    <Acordeon titulo="Inventario de almacenes" abierto={false}>
      {!inventario ? <p className="vacio">{ejecucion
        ? 'No hay un corte de inventario disponible para este instante.'
        : 'Sin una corrida seleccionada, se muestran el Stock inicial.'}</p>
        : <>
          <div className="almacenes">{inventario.almacenes.map(almacen => {
            // La API no publica capacidad; el llenado se mide contra el stock inicial del caso.
            const inicial = inicialPorId.get(almacen.id);
            const porcentaje = almacen.ilimitado || !inicial ? 100
              : Math.max(0, Math.min(100, Math.round((almacen.disponible / inicial) * 100)));
            return <div key={almacen.id} className="almacen">
              <div className="almacen-linea">
                <span>{almacen.id} <em>({almacen.ubicacion.x}, {almacen.ubicacion.y})</em></span>
                <b>{almacen.ilimitado ? '∞' : `${numero(almacen.disponible)}${inicial ? ` / ${numero(inicial)}` : ''}`}</b>
              </div>
              <div className="bar"><i className={porcentaje < 20 ? 'bajo' : porcentaje < 60 ? 'medio' : ''}
                style={{ width: `${porcentaje}%` }} /></div>
            </div>;
          })}</div>
          <p className="nota">Inventario del último corte disponible, a las {fecha(inventario.reloj)}.</p>
        </>}
    </Acordeon>

    {ejecucion?.colapso && <Acordeon titulo="Colapso logístico">
      <div className="alerta-lista">
        <b>Pedido {ejecucion.colapso.pedido?.id || 'no disponible'}</b>
        {ejecucion.colapso.pedido && <span>Destino ({ejecucion.colapso.pedido.destino?.x}, {ejecucion.colapso.pedido.destino?.y}) · {numero(ejecucion.colapso.pedido.cantidad)} productos · plazo {fecha(ejecucion.colapso.pedido.fechaLimite)}</span>}
      </div>
      <dl>
        <div><dt>Instante del colapso</dt><dd>{fecha(ejecucion.colapso.instante)}</dd></div>
        <div><dt>Ventana</dt><dd>{numero(ejecucion.colapso.ventana)}</dd></div>
        <div><dt>Tiempo real transcurrido</dt><dd>{minutos(ejecucion.colapso.milisegundosReales / 60000)}</dd></div>
        <div><dt>Pedidos entregados</dt><dd>{numero(ejecucion.colapso.pedidosEntregados)}</dd></div>
        <div><dt>Pedidos vencidos</dt><dd>{numero(ejecucion.colapso.pedidosVencidos)}</dd></div>
        <div><dt>Pedidos en cola</dt><dd>{numero(ejecucion.colapso.pedidosEnCola)}</dd></div>
        <div><dt>Vehículos disponibles / averiados</dt><dd>{numero(ejecucion.colapso.vehiculosDisponibles)} / {numero(ejecucion.colapso.vehiculosAveriados)}</dd></div>
        <div><dt>Replanificaciones por averías</dt><dd>{numero(ejecucion.colapso.replanificaciones)}</dd></div>
      </dl>
    </Acordeon>}

    <Acordeon titulo="Resumen de la corrida" abierto={false}>
      {!ejecucion?.resumen ? <p className="vacio">El servidor publica el resumen al terminar la corrida o al detectar el colapso.</p>
        : <dl>
          <div><dt>Pedidos recibidos</dt><dd>{numero(ejecucion.resumen.pedidosRecibidos)}</dd></div>
          <div><dt>Completos / en plazo</dt><dd>{numero(ejecucion.resumen.pedidosOriginalesCompletos)} / {numero(ejecucion.resumen.pedidosOriginalesEnPlazo)}</dd></div>
          <div><dt>Pedidos pendientes</dt><dd>{numero(ejecucion.resumen.pedidosPendientes)}</dd></div>
          <div><dt>Productos recibidos</dt><dd>{numero(ejecucion.resumen.productosRecibidos)}</dd></div>
          <div><dt>Distancia total</dt><dd>{decimal(ejecucion.resumen.distanciaTotalKm, ' km')}</dd></div>
          <div><dt>Costo total</dt><dd>S/ {decimal(ejecucion.resumen.costoTotal)}</dd></div>
          <div><dt>Fin de simulación</dt><dd>{fecha(ejecucion.resumen.fin)}</dd></div>
        </dl>}
    </Acordeon>
  </>;
}
