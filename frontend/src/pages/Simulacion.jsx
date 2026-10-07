import React from 'react';
import { decimal, duracion, fecha, numero, SIN_DATO } from '../services/formato.js';

/* Simulación: los tres escenarios del caso. La tarjeta de configuración flota sobre el
   mapa a la izquierda y se puede plegar para liberar la vista. */

const ESCENARIOS = [
  ['OPERACION_DIARIA', 'Día a día', 'Planificación y avance de una jornada.'],
  ['PERIODO_5_DIAS', 'Semanal (5 días)', 'Cinco jornadas seguidas sobre los mismos datos.'],
  ['COLAPSO', 'Colapso logístico', 'Corre hasta el primer pedido sin ruta factible.']
];

export default function Simulacion({
  ejecucion, config, alCambiarConfig, periodos, detallePeriodo,
  alIniciar, alDetener, ocupado, cfgAbierta, alAbrirCfg, alCerrarCfg
}) {
  const resumen = ejecucion?.resumen;
  const enMarcha = ejecucion && !['TERMINADA', 'COLAPSADA', 'FALLIDA', 'CANCELADA'].includes(ejecucion.estado);

  if (!cfgAbierta) {
    return <button type="button" className="cfgtab" onClick={alAbrirCfg}>CONFIGURACIÓN</button>;
  }

  return <section className="float cfg">
    <header>
      <h2>Tipo de simulación</h2>
      <button type="button" className="x" onClick={alCerrarCfg}
        aria-label="Plegar la configuración">✕</button>
    </header>

    <div className="body">
      <form onSubmit={alIniciar}>
        <div className="escenarios">
          {ESCENARIOS.map(([id, nombre, ayuda]) => <button key={id} type="button"
            data-on={config.escenario === id || undefined}
            onClick={() => alCambiarConfig({ escenario: id })}>
            <b>{nombre}</b><small>{ayuda}</small>
          </button>)}
        </div>

        <p className="sect">Datos y fecha</p>

        <label className="campo">Periodo de datos
          <select value={config.periodo} required
            onChange={e => alCambiarConfig({ periodo: e.target.value, dia: '' })}>
            <option value="">Seleccionar periodo</option>
            {periodos.map(valor => <option key={valor} value={valor}>
              {valor.slice(0, 4)}-{valor.slice(4)}
            </option>)}
          </select>
        </label>

        {detallePeriodo && <p className="nota-dato muted">
          {numero(detallePeriodo.pedidos)} pedidos · {numero(detallePeriodo.bloqueos)} bloqueos ·{' '}
          {numero(detallePeriodo.jornadasMantenimiento)} jornadas de mantenimiento
        </p>}

        <div className="par">
          <label className="campo">Fecha inicial
            <input name="dia" type="date" value={config.dia} required
              onChange={e => alCambiarConfig({ dia: e.target.value })} />
          </label>
          <label className="campo">Hora inicial
            <input name="hora" type="time" value={config.hora} required
              onChange={e => alCambiarConfig({ hora: e.target.value })} />
          </label>
        </div>

        {config.escenario === 'COLAPSO' && <label className="campo">Horizonte máximo (días)
          <input type="number" min="1" max="60" value={config.horizonte} required
            onChange={e => alCambiarConfig({ horizonte: e.target.value })} />
        </label>}

        <label className="campo">Algoritmo
          <select value={config.algoritmo} onChange={e => alCambiarConfig({ algoritmo: e.target.value })}>
            <option value="GRASP">GRASP</option>
            <option value="TABU">Vecino más cercano + Búsqueda Tabú</option>
          </select>
        </label>

        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={ocupado || !periodos.length}>
            {ocupado ? 'PROCESANDO…' : '▶ INICIAR'}
          </button>
          <button className="btn" type="button" disabled={ocupado || !enMarcha} onClick={alDetener}>
            ■ DETENER
          </button>
        </div>
      </form>

      <p className="sect" style={{ marginTop: 18 }}>Tiempos</p>
      <dl className="resumen">
        <div><dt>Reloj simulado</dt><dd className="mono">{fecha(ejecucion?.reloj, SIN_DATO)}</dd></div>
        <div><dt>Avance simulado</dt><dd>{duracion(ejecucion?.inicio, ejecucion?.reloj)}</dd></div>
        <div><dt>Última instantánea</dt><dd className="mono">{fecha(ejecucion?.actualizadoEn, SIN_DATO)}</dd></div>
      </dl>
      <p className="nota">
        El reloj simulado avanza sobre las fechas del archivo del curso; la última instantánea
        es tiempo real del servidor.
      </p>

      <p className="sect" style={{ marginTop: 18 }}>Parámetros de la corrida</p>
      <dl className="resumen">
        <div><dt>Semilla</dt><dd className="mono">20260901</dd></div>
        <div><dt>Construcciones GRASP</dt><dd>8</dd></div>
        <div><dt>Alfa GRASP</dt><dd>0,30</dd></div>
        <div><dt>Iteraciones Tabú</dt><dd>120</dd></div>
        <div><dt>Tenencia Tabú</dt><dd>8</dd></div>
        <div><dt>Muestra del vecindario</dt><dd>40</dd></div>
      </dl>
      <p className="nota">
        Fijos en esta entrega. Se muestran para que la corrida sea reproducible y citable en
        el informe.
      </p>

      {resumen && <>
        <p className="sect" style={{ marginTop: 18 }}>Resumen de la corrida</p>
        <dl className="resumen">
          <div><dt>Pedidos recibidos</dt><dd>{numero(resumen.pedidosRecibidos)}</dd></div>
          <div><dt>Completos en plazo</dt><dd>{numero(resumen.pedidosOriginalesEnPlazo)}</dd></div>
          <div><dt>Pendientes al cierre</dt><dd>{numero(resumen.pedidosPendientes)}</dd></div>
          <div><dt>Distancia total</dt><dd>{decimal(resumen.distanciaTotalKm, ' km')}</dd></div>
          <div><dt>Costo total</dt><dd>S/ {decimal(resumen.costoTotal)}</dd></div>
          <div><dt>Planificaciones</dt><dd>{numero(resumen.iteracionesPlanificacion)}</dd></div>
          <div><dt>Fin de la simulación</dt><dd>{fecha(resumen.fin)}</dd></div>
        </dl>
      </>}
    </div>
  </section>;
}
