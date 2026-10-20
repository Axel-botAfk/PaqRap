import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api, esFinal, urlWebSocket } from './services/api.js';
import { ANCHO, ALTO, caminoEnGrilla, nodosBloqueados, posicionEnCamino, tramosDeRuta, planVigenteEn,
  prepararTrayectos, posicionEnTrayecto } from './mapa.js';
import { aIso, aMs, crearRelojAnclado, desfaseConServidor } from './reloj.js';

const CLAVE_EJECUCION = 'paqrap.ejecucion';
const ID_VALIDO = /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i;

function leerEjecucionGuardada() {
  try { return localStorage.getItem(CLAVE_EJECUCION); } catch { return null; }
}

function guardarEjecucion(id) {
  try { id ? localStorage.setItem(CLAVE_EJECUCION, id) : localStorage.removeItem(CLAVE_EJECUCION); } catch { /* sin almacenamiento */ }
}

const LISTA_VACIA = [];
function colorDeRuta(ruta) {
  const clave = ruta.vehiculoId || ruta.id;
  const numeroVehiculo = Number(clave.match(/\d+$/)?.[0]);
  const familia = [...clave.replace(/\d+$/, '')]
    .reduce((total, letra) => total * 31 + letra.charCodeAt(0), 0);
  const codigo = Number.isFinite(numeroVehiculo) && numeroVehiculo > 0
    ? numeroVehiculo + familia * 37 : familia;
  return `hsl(${Math.round((codigo * 137.508) % 360)} 64% 39%)`;
}

const ESCENARIOS = [
  { id: 'OPERACION_DIARIA', nombre: 'Operación día a día', ayuda: 'Un día al ritmo elegido; 1× es tiempo real.' },
  { id: 'PERIODO_5_DIAS', nombre: 'Simulación de 5 días', ayuda: 'Cinco jornadas en 30 a 60 minutos reales.' },
  { id: 'COLAPSO', nombre: 'Colapso logístico', ayuda: 'Avanza hasta el primer pedido vencido.' }
];

const ETIQUETAS = {
  PENDIENTE: 'En espera', EN_CURSO: 'En ejecución', PAUSADA: 'En pausa', TERMINADA: 'Terminada',
  COLAPSADA: 'Colapso detectado', FALLIDA: 'Fallida', CANCELADA: 'Cancelada'
};

const FORMATO_FECHA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
});

function fecha(valor) {
  if (!valor) return '—';
  const fechaLocal = new Date(valor);
  return Number.isNaN(fechaLocal.getTime()) ? valor : FORMATO_FECHA.format(fechaLocal);
}

function numero(valor) {
  return typeof valor === 'number' ? new Intl.NumberFormat('es-PE').format(valor) : '—';
}

function decimal(valor, unidad = '') {
  return typeof valor === 'number'
    ? `${new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 }).format(valor)}${unidad}`
    : '—';
}

function minutos(valor) {
  if (typeof valor !== 'number') return '—';
  return valor >= 60 ? decimal(valor / 60, ' h') : decimal(valor, ' min');
}

function textoRitmo(ritmo) {
  return `${minutos(ritmo.minutosPorVentana)} simulados cada ${decimal(ritmo.saMs / 1000, ' s')}`;
}

function ritmoConfigurado(escenario, duracionObjetivo, velocidad) {
  if (escenario === 'PERIODO_5_DIAS') {
    const duracion = Number(duracionObjetivo) || 40;
    return `Ritmo: ${minutos(7200 / (duracion * 3))} simulados cada 20 s; los 5 días duran unos ${duracion} min reales.`;
  }
  if (escenario === 'OPERACION_DIARIA') {
    const v = Number(velocidad) || 60;
    return v === 1 ? 'Ritmo: tiempo real (el día dura 24 h).'
      : `Ritmo: ${minutos(v / 3)} simulados cada 20 s; el día dura unos ${minutos(1440 / v)} reales.`;
  }
  return 'Ritmo: 2 h simuladas cada 10 s; se detiene en el primer pedido vencido.';
}

function TarjetaMetrica({ etiqueta, valor, nota, alerta = false }) {
  return <div className={`metrica ${alerta ? 'metrica-alerta' : ''}`}>
    <span>{etiqueta}</span><strong>{valor}</strong><small>{nota}</small>
  </div>;
}

function DibujoVehiculo({ tipo }) {
  switch (tipo) {
    case 'AUTO':
      return <g fill="none" stroke="#24659b" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 21V14h3l3-5h10l4 5h2v7H5Z" fill="#dcecf8" />
        <path d="M11 14h12M16 9v5" /><circle cx="10" cy="22" r="2.3" fill="#24659b" /><circle cx="23" cy="22" r="2.3" fill="#24659b" />
      </g>;
    case 'MOTO':
      return <g fill="none" stroke="#21805a" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="8" cy="23" r="3.5" /><circle cx="25" cy="23" r="3.5" />
        <path d="m8 23 5-7h6l5 7M12 16l-2-3h5m7 1 2-3h3M17 16l-2 7h10" />
      </g>;
    case 'BICICLETA':
      return <g fill="none" stroke="#b27a16" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="7" cy="23" r="4" /><circle cx="25" cy="23" r="4" />
        <path d="m7 23 7-10 5 10H7l7-10m5 10 6-10m-12 0h5m5 0h4m-13-3 2-1" />
      </g>;
    case 'CAMION':
      return <g fill="none" stroke="#754d98" strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 10h17v12H3zM20 14h5l4 5v3h-9z" fill="#ede5f5" />
        <circle cx="9" cy="23" r="2.2" fill="#754d98" /><circle cx="24" cy="23" r="2.2" fill="#754d98" />
      </g>;
    default:
      return <g fill="none" stroke="#0b3644" strokeWidth="2.4"><rect x="8" y="8" width="16" height="16" rx="3" /><path d="M16 11v7m0 3v1" /></g>;
  }
}

function IconoLeyenda({ tipo, texto }) {
  return <span className="leyenda-vehiculo"><svg viewBox="0 0 32 32" aria-hidden="true"><DibujoVehiculo tipo={tipo} /></svg>{texto}</span>;
}

function MapaOperacion({ ejecucion, rutas, calculandoRutas, rutaSeleccionada, mapaDatos, inventario, instante, fotogramas,
  indiceFotograma, alCambiarFotograma, reproduciendo, alReproducir, pasoMs, alCambiarPaso,
  enVivo = false, trayectosEnVivo = null, alVolverEnVivo = null }) {
  const [rutaEnFoco, setRutaEnFoco] = useState(null);
  const fotograma = fotogramas[indiceFotograma];
  const posicionesObservadas = fotograma?.vehiculos ?? ejecucion?.vehiculos ?? LISTA_VACIA;
  const [vehiculosAnimados, setVehiculosAnimados] = useState(posicionesObservadas);
  const anteriores = useRef(null);
  const vehiculosObservados = fotograma ? vehiculosAnimados : posicionesObservadas;
  const noAsignados = ejecucion?.noAsignados || [];
  const almacenes = mapaDatos?.almacenes || [];
  const stockPorId = new Map((inventario?.almacenes || []).map(a => [a.id, a]));
  const bloqueosActivos = (mapaDatos?.bloqueos || []).filter(b => instante && b.inicio <= instante && b.fin > instante);
  const claveBloqueos = bloqueosActivos.map(b => `${b.inicio}/${b.fin}`).join('|');
  const bloqueados = useMemo(() => nodosBloqueados(bloqueosActivos), [mapaDatos, claveBloqueos]);
  // En vivo cada unidad sigue los hitos que el simulador informó para la ventana; las que no
  // tienen recorrido se quedaron quietas y se muestran donde las deja el corte.
  const trayectosPreparados = useMemo(() => trayectosEnVivo
    ? prepararTrayectos(trayectosEnVivo, aMs, bloqueados) : null, [trayectosEnVivo, bloqueados]);
  const instanteMs = instante ? aMs(instante) : NaN;
  const vehiculos = trayectosPreparados ? posicionesObservadas.map(vehiculo => {
    const ubicacion = posicionEnTrayecto(trayectosPreparados.get(vehiculo.id), instanteMs);
    return ubicacion ? { ...vehiculo, ubicacion } : vehiculo;
  }) : vehiculosObservados;

  useEffect(() => {
    if (!fotograma) {
      anteriores.current = null;
      setVehiculosAnimados(posicionesObservadas);
      return undefined;
    }
    const origenes = anteriores.current;
    if (!origenes) {
      anteriores.current = posicionesObservadas;
      setVehiculosAnimados(posicionesObservadas);
      return undefined;
    }
    const porId = new Map(origenes.map(vehiculo => [vehiculo.id, vehiculo]));
    const trayectos = posicionesObservadas.map(vehiculo => {
      const origen = porId.get(vehiculo.id)?.ubicacion;
      return { vehiculo, origen,
        camino: origen ? caminoEnGrilla(origen, vehiculo.ubicacion, bloqueados) : [] };
    });
    let cuadro;
    const inicio = performance.now();
    const animar = ahora => {
      const progreso = Math.min(1, (ahora - inicio) / pasoMs);
      const visibles = trayectos.map(({ vehiculo, origen, camino }) => ({
        ...vehiculo,
        ubicacion: origen ? posicionEnCamino(camino, origen, vehiculo.ubicacion, progreso)
          : vehiculo.ubicacion
      }));
      anteriores.current = visibles;
      setVehiculosAnimados(visibles);
      if (progreso < 1) cuadro = requestAnimationFrame(animar);
      else anteriores.current = posicionesObservadas;
    };
    cuadro = requestAnimationFrame(animar);
    return () => cancelAnimationFrame(cuadro);
  }, [fotograma, posicionesObservadas, bloqueados, pasoMs]);
  const trazos = useMemo(() => rutas.map(ruta => ({ ruta, tramos: tramosDeRuta(ruta, bloqueados) })), [rutas, bloqueados]);
  const finalizada = ejecucion && esFinal(ejecucion.estado);
  const puntos = rutas.flatMap(ruta => ruta.paradas.map((parada, indice) => ({
    ruta, parada, orden: indice + 1, ultima: indice === ruta.paradas.length - 1
  })));
  const gruposVehiculos = [...vehiculos.reduce((grupos, vehiculo) => {
    if (!vehiculo.ubicacion) return grupos;
    const clave = `${vehiculo.ubicacion.x},${vehiculo.ubicacion.y}`;
    if (!grupos.has(clave)) grupos.set(clave, []);
    grupos.get(clave).push(vehiculo);
    return grupos;
  }, new Map()).values()];
  const salidas = [...new Map(rutas.filter(ruta => ruta.origen)
    .map(ruta => [`${ruta.origen.x},${ruta.origen.y}`, ruta.origen])).values()];
  const vacio = rutas.length === 0 && vehiculos.length === 0 && noAsignados.length === 0 && !mapaDatos;
  const x = valor => Math.max(0, Math.min(700, (valor ?? 0) * 10));
  const y = valor => Math.max(0, Math.min(500, (ALTO - (valor ?? 0)) * 10));

  return <div className="mapa-wrap">
    <div className="mapa-cabecera"><b>Visualizador de la operación</b><span>Retícula 70 × 50 km · nodos 0–70 y 0–50</span></div>
    {(fotogramas.length > 0 || enVivo) && <div className="mapa-reproductor">
      {enVivo
        ? <button type="button" disabled={fotogramas.length < 2} onClick={() => alCambiarFotograma(Math.max(0, fotogramas.length - 2))}>Historial</button>
        : <button type="button" onClick={alReproducir}>{reproduciendo ? 'Pausar' : indiceFotograma >= fotogramas.length - 1 ? 'Repetir' : 'Reproducir'}</button>}
      <input type="range" min="0" max={Math.max(0, fotogramas.length - 1)} value={enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma}
        onChange={evento => alCambiarFotograma(Number(evento.target.value))}
        aria-label="Seleccionar instante de la reproducción" />
      {enVivo
        ? <span>{ejecucion?.estado === 'PAUSADA' ? 'En pausa' : 'En vivo'} · {fecha(instante)} · ventana {ejecucion?.ritmo?.ventana ?? 0} de {ejecucion?.ritmo?.ventanas ?? '—'} · {ejecucion?.ritmo ? textoRitmo(ejecucion.ritmo) : ''}</span>
        : <span>Reproducción visual {indiceFotograma + 1}/{fotogramas.length} · {fecha(fotograma?.reloj)}{finalizada && reproduciendo ? ' · cálculo terminado' : ''}</span>}
      {!enVivo && alVolverEnVivo && <button type="button" onClick={alVolverEnVivo}>En vivo</button>}
      {!enVivo && <label>Velocidad <select value={pasoMs} onChange={evento => alCambiarPaso(Number(evento.target.value))}>
        <option value="800">Lenta</option><option value="450">Normal</option><option value="200">Rápida</option>
      </select></label>}
    </div>}
    <svg className="mapa" viewBox="0 0 700 500" role="img" aria-label="Mapa esquemático de rutas, pedidos y vehículos">
      <rect width="700" height="500" fill="#f4f8f8" />
      {Array.from({ length: ANCHO + 1 }, (_, i) => <line key={`v${i}`} x1={i * 10} x2={i * 10} y1="0" y2="500" className={`grid-line ${i % 10 === 0 ? 'grid-major' : ''}`} />)}
      {Array.from({ length: ALTO + 1 }, (_, i) => <line key={`h${i}`} x1="0" x2="700" y1={i * 10} y2={i * 10} className={`grid-line ${i % 10 === 0 ? 'grid-major' : ''}`} />)}
      {bloqueosActivos.flatMap((bloqueo, indice) => bloqueo.nodos.map((nodo, n) =>
        <rect key={`b${indice}-${n}`} x={x(nodo.x) - 4} y={y(nodo.y) - 4} width="8" height="8"
          fill="#ce5647" fillOpacity=".75"><title>{`Bloqueo activo hasta ${fecha(bloqueo.fin)}`}</title></rect>))}
      {trazos.flatMap(({ ruta, tramos }, indice) => tramos.map((tramo, n) => {
        const coordenadas = tramo.map(p => `${x(p.x)},${y(p.y)}`).join(' ');
        const resaltada = ruta.id === rutaSeleccionada || ruta.id === rutaEnFoco;
        return <g key={`${ruta.id}-${indice}-${n}`} style={{ '--ruta-color': colorDeRuta(ruta) }} className={`trazo-ruta ${finalizada && !reproduciendo && indiceFotograma >= fotogramas.length - 1 ? 'trazo-final' : ''} ${resaltada ? 'trazo-resaltado' : ''}`}>
          <polyline className="trazo-visible" points={coordenadas} fill="none" />
          <polyline className="trazo-interaccion" points={coordenadas} fill="none"
            tabIndex="0" role="button" aria-label={`Resaltar ruta ${ruta.id} del vehículo ${ruta.vehiculoId}`}
            onMouseEnter={() => setRutaEnFoco(ruta.id)} onMouseLeave={() => setRutaEnFoco(null)}
            onFocus={() => setRutaEnFoco(ruta.id)} onBlur={() => setRutaEnFoco(null)}>
            <title>{`${ruta.id} · ${ruta.vehiculoId} · ${ruta.paradas.length} parada(s)`}</title>
          </polyline>
        </g>;
      }))}
      {puntos.map(({ ruta, parada, orden, ultima }, indice) => <g key={`p${ruta.id}-${indice}`}
        className={`mapa-parada-orden ${ultima ? 'mapa-parada-final' : ''}`}
        transform={`translate(${x(parada.destino.x)},${y(parada.destino.y)})`}
        style={{ '--ruta-color': colorDeRuta(ruta) }}
        onMouseEnter={() => setRutaEnFoco(ruta.id)} onMouseLeave={() => setRutaEnFoco(null)}>
        <title>{`${ruta.vehiculoId} · parada ${orden} de ${ruta.paradas.length} · pedido ${parada.id}${ultima ? ' · última parada' : ''}`}</title>
        {ultima && <circle className="aro-final" r="13" />}
        <circle r="9" /><text textAnchor="middle" y="4">{orden}</text>
      </g>)}
      {noAsignados.map((pedido, indice) => <circle key={`n${indice}`} cx={x(pedido.destino?.x)} cy={y(pedido.destino?.y)} r="5" fill="#c64d39" stroke="white" strokeWidth="1.5" />)}
      {almacenes.map(almacen => <g key={almacen.id} transform={`translate(${x(almacen.ubicacion.x)},${y(almacen.ubicacion.y)})`}>
        <title>{`${almacen.id} · ${almacen.tipo} · ${stockPorId.get(almacen.id)?.ilimitado ? 'stock ilimitado' : stockPorId.has(almacen.id) ? `stock actual ${stockPorId.get(almacen.id).disponible}` : `stock inicial ${almacen.stockInicial}`}`}</title>
        {almacen.tipo === 'CENTRAL' ? <path d="M0 -11 11 0 0 11 -11 0Z" fill="#733e91" stroke="white" strokeWidth="2" />
          : <rect x="-9" y="-9" width="18" height="18" rx="2" fill="#a366ae" stroke="white" strokeWidth="2" />}
        <text x="0" y="4" textAnchor="middle" fontSize="10" fontWeight="800" fill="white">{almacen.tipo === 'CENTRAL' ? 'C' : 'I'}</text>
      </g>)}
      {gruposVehiculos.map(grupo => grupo.length === 1 ? <svg key={grupo[0].id}
        x={x(grupo[0].ubicacion.x) - 13} y={y(grupo[0].ubicacion.y) - 13}
        width="26" height="26" viewBox="0 0 32 32">
        <title>{`${grupo[0].id} · ${grupo[0].tipo} · ${grupo[0].estado}`}</title>
        <circle cx="16" cy="16" r="15" fill="white" stroke="#c8dce0" strokeWidth="1.5" />
        <DibujoVehiculo tipo={grupo[0].tipo} />
      </svg> : <g key={`grupo-${grupo[0].ubicacion.x}-${grupo[0].ubicacion.y}`}
        className="grupo-vehiculos"
        transform={`translate(${x(grupo[0].ubicacion.x)},${y(grupo[0].ubicacion.y)})`}>
        <title>{`${grupo.length} unidades en (${grupo[0].ubicacion.x}, ${grupo[0].ubicacion.y}): ${grupo.map(v => v.id).join(', ')}`}</title>
        <circle r="14" /><text textAnchor="middle" y="4">{grupo.length}</text>
      </g>)}
      {salidas.map(punto => <g key={`salida-${punto.x}-${punto.y}`} className="mapa-salida"
        transform={`translate(${x(punto.x)},${y(punto.y)})`}>
        <title>{`Salida de ruta desde el nodo (${punto.x}, ${punto.y})`}</title>
        <path d="M0 -10 V-17" /><circle cx="0" cy="-21" r="10" />
        <text x="0" y="-17" textAnchor="middle">S</text>
      </g>)}
    </svg>
    {calculandoRutas && <div className="mapa-vacio mapa-calculando" role="status" aria-live="polite">
      <span className="indicador-calculo" aria-hidden="true" />
      <b>{ejecucion?.estado === 'PENDIENTE' ? 'Preparando la corrida…' : 'Calculando rutas…'}</b>
      <span>El planificador está trabajando. Mostraremos las rutas nuevas cuando estén listas.</span>
    </div>}
    {vacio && <div className="mapa-vacio"><b>Sin operación cargada</b><span>Selecciona un periodo, configura una corrida e iníciala para ver el mapa.</span></div>}
    <div className="leyenda"><span><i className="punto punto-salida" /> S: salida</span><span><i className="punto punto-llegada" /> Números: orden de entrega (aro: última)</span><span><i className="punto punto-ruta" /> Rutas simultáneas por color</span><span><i className="punto punto-alerta" /> Sin asignar</span><span><i className="punto punto-bloqueo" /> Bloqueo activo ({bloqueosActivos.length})</span><span><i className="punto punto-central" /> Central</span><span><i className="punto punto-intermedio" /> Intermedio</span><IconoLeyenda tipo="AUTO" texto="Auto" /><IconoLeyenda tipo="MOTO" texto="Moto" /><IconoLeyenda tipo="BICICLETA" texto="Bicicleta" /></div>
    <div className="almacenes-contexto"><b>Almacenes del caso</b><div className="almacenes-tarjetas">{almacenes.map(almacen => <div key={almacen.id} className="almacen-tarjeta">
      <strong>{almacen.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'} · {almacen.id}</strong>
      <span>Nodo ({almacen.ubicacion.x}, {almacen.ubicacion.y}) · {stockPorId.get(almacen.id)?.ilimitado ? 'stock ilimitado' : stockPorId.has(almacen.id) ? `stock actual: ${numero(stockPorId.get(almacen.id).disponible)} productos` : `stock inicial: ${numero(almacen.stockInicial)} productos`}</span>
    </div>)}</div><small>{inventario ? `Inventario de la corrida a las ${fecha(inventario.reloj)}; durante la reproducción histórica se muestra el stock más reciente, no el de cada fotograma.` : 'Sin corrida: se muestra el stock inicial.'}</small></div>
    <p className="mapa-aviso">En vivo, el reloj avanza con el tiempo real según el ritmo del escenario y cada unidad recorre los hitos que informó el simulador (salida, almacén, entregas y corte); entre hitos el trazo por la retícula es esquemático, no GPS. En el historial las posiciones proceden de los saltos del simulador. Se muestra el plan vigente para el instante reproducido: varias rutas simultáneas y todas sus paradas numeradas. Son entregas previstas, no prueba de entrega efectiva. Las métricas superiores muestran el estado más reciente. Al terminar, el trazo se atenúa; pasa el cursor o usa Tab para resaltarlo.</p>
  </div>;
}

function ListaRutas({ rutas, seleccion, alSeleccionar, calculandoRutas }) {
  return <section className="panel">
    <div className="panel-titulo"><div><h2>Rutas del instante</h2><p>Incluye las rutas simultáneas y sus paradas en orden.</p></div><span className="contador">{rutas.length}</span></div>
    {calculandoRutas ? <p className="vacio" role="status">Calculando rutas…</p>
      : rutas.length === 0 ? <p className="vacio">No hay rutas planificadas para este instante.</p> :
      <div className="lista-rutas">{rutas.map((ruta, indice) => <button key={`${ruta.id}-${indice}`}
        type="button" className={`ruta ${seleccion === ruta.id ? 'ruta-activa' : ''}`}
        onClick={() => alSeleccionar(seleccion === ruta.id ? null : ruta.id)}>
        <span><b>{ruta.id}</b><small>{ruta.vehiculoId} · {ruta.paradas.length} parada(s)</small></span>
        <span className="ruta-distancia">{decimal(ruta.distanciaKm, ' km')}</span>
      </button>)}</div>}
  </section>;
}

function TablaPedidos({ ejecucion }) {
  const filas = [
    ...(ejecucion?.rutas || []).flatMap(ruta => ruta.paradas.map(pedido => ({ ...pedido, ruta: ruta.id }))),
    ...(ejecucion?.noAsignados || []).map(pedido => ({ ...pedido, ruta: '—' }))
  ];
  return <section className="panel">
    <div className="panel-titulo"><div><h2>Pedidos del plan vigente</h2><p>Los entregados se contabilizan en el avance, no en esta lista.</p></div><span className="contador">{filas.length}</span></div>
    {filas.length === 0 ? <p className="vacio">No hay pedidos planificados o sin asignar en la instantánea actual.</p> :
      <div className="tabla-wrap"><table><thead><tr><th>Pedido</th><th>Destino</th><th>Cantidad</th><th>Ruta</th><th>Estado</th><th>Plazo</th></tr></thead>
        <tbody>{filas.slice(0, 100).map((pedido, indice) => <tr key={`${pedido.id}-${indice}`}>
          <td>{pedido.id}</td><td>{pedido.destino?.x}, {pedido.destino?.y}</td><td>{numero(pedido.cantidad)}</td>
          <td>{pedido.ruta}</td><td><span className={`estado-pedido ${pedido.estado === 'NO_ASIGNADO' ? 'no-asignado' : ''}`}>{pedido.estado === 'NO_ASIGNADO' ? 'Sin asignar' : 'Planificado'}</span></td>
          <td>{fecha(pedido.fechaLimite)}</td></tr>)}</tbody></table>
        {filas.length > 100 && <p className="tabla-nota">Se muestran los primeros 100 de {numero(filas.length)} registros de esta instantánea.</p>}</div>}
  </section>;
}

const ETIQUETAS_EVENTO = { REPLANIFICACION: 'Replanificación', AVERIA_APLICADA: 'Unidad fuera de servicio', COLAPSO: 'Colapso' };

function PanelIncidencias({ ejecucion, averias, eventosOperacion }) {
  const sinAsignar = ejecucion?.noAsignados || [];
  const colapso = ejecucion?.resumen?.huboColapso;
  const fallo = ejecucion?.estado === 'FALLIDA';
  const hechos = eventosOperacion.filter(e => ETIQUETAS_EVENTO[e.tipo]).slice(-30).reverse();
  const hayAlertas = sinAsignar.length > 0 || colapso || fallo || averias.length > 0 || hechos.length > 0;
  return <section className="panel">
    <div className="panel-titulo"><div><h2>Alertas observadas</h2><p>Hechos informados por la corrida activa; viven en memoria del servidor.</p></div><span className="contador">{sinAsignar.length + averias.length + hechos.length + (colapso ? 1 : 0) + (fallo ? 1 : 0)}</span></div>
    {!hayAlertas && <p className="vacio">Sin alertas observadas en la instantánea actual.</p>}
    {colapso && <div className="alerta-lista"><b>Colapso logístico</b><span>Primer incumplimiento: {fecha(ejecucion.resumen.instanteColapso)}. Pedido: {ejecucion.resumen.pedidoQueColapso || 'no disponible'}.</span></div>}
    {fallo && <div className="alerta-lista"><b>Ejecución fallida</b><span>{ejecucion.mensajeError || 'La simulación no pudo completarse.'}</span></div>}
    {averias.map(a => <div className="alerta-lista" key={`${a.unidadId}-${a.instante}`}><b>Avería {a.tipo} · {a.unidadId}</b><span>{a.estado} · registrada a las {fecha(a.instante)} · {numero(a.pedidosAfectados || 0)} pedido(s) replanificado(s).</span></div>)}
    {hechos.map(e => <div className="alerta-lista" key={`e${e.indice}`}><b>{ETIQUETAS_EVENTO[e.tipo]}{e.pedidoId ? ` · ${e.pedidoId}` : e.vehiculoId ? ` · ${e.vehiculoId}` : ''}</b><span>{fecha(e.reloj)} · {e.detalle}</span></div>)}
    {sinAsignar.slice(0, 20).map((pedido, indice) => <div className="alerta-lista" key={`${pedido.id}-${indice}`}><b>{pedido.id} · sin asignar</b><span>{pedido.motivo || 'Sin motivo específico disponible'} · plazo {fecha(pedido.fechaLimite)}</span></div>)}
    {sinAsignar.length > 20 && <p className="tabla-nota">Hay {numero(sinAsignar.length - 20)} pedidos adicionales sin asignar.</p>}
  </section>;
}

function PanelColapso({ ejecucion }) {
  const colapso = ejecucion?.colapso;
  if (!colapso) return null;
  const pedido = colapso.pedido;
  return <section className="panel resumen">
    <div className="panel-titulo"><div><h2>Colapso logístico</h2><p>Primer pedido que venció sin entregarse; la corrida se detuvo en ese corte.</p></div></div>
    <div className="alerta-lista"><b>Pedido {pedido?.id || 'no disponible'}</b>{pedido && <span>Destino ({pedido.destino?.x}, {pedido.destino?.y}) · {numero(pedido.cantidad)} productos · registrado {fecha(pedido.fechaRegistro)} · plazo {fecha(pedido.fechaLimite)}</span>}</div>
    <dl><div><dt>Instante del colapso</dt><dd>{fecha(colapso.instante)}</dd></div>
      <div><dt>Ventana</dt><dd>{numero(colapso.ventana)}</dd></div>
      <div><dt>Tiempo real transcurrido</dt><dd>{minutos(colapso.milisegundosReales / 60000)}</dd></div>
      <div><dt>Pedidos entregados</dt><dd>{numero(colapso.pedidosEntregados)}</dd></div>
      <div><dt>Pedidos vencidos</dt><dd>{numero(colapso.pedidosVencidos)}</dd></div>
      <div><dt>Pedidos en cola</dt><dd>{numero(colapso.pedidosEnCola)}</dd></div>
      <div><dt>Vehículos disponibles / averiados</dt><dd>{numero(colapso.vehiculosDisponibles)} / {numero(colapso.vehiculosAveriados)}</dd></div>
      <div><dt>Replanificaciones por averías</dt><dd>{numero(colapso.replanificaciones)}</dd></div></dl>
  </section>;
}

export default function App() {
  const [escenario, setEscenario] = useState('OPERACION_DIARIA');
  const [algoritmo, setAlgoritmo] = useState('GRASP');
  const [periodos, setPeriodos] = useState([]);
  const [periodo, setPeriodo] = useState('');
  const [detalle, setDetalle] = useState(null);
  const [mapaDatos, setMapaDatos] = useState(null);
  const [fuente, setFuente] = useState('ARCHIVOS');
  const [dia, setDia] = useState('');
  const [hora, setHora] = useState('00:00');
  const [horizonte, setHorizonte] = useState(30);
  const [ejecucion, setEjecucion] = useState(null);
  const [id, setId] = useState(() => {
    const valor = new URLSearchParams(location.search).get('ejecucion') || leerEjecucionGuardada();
    return ID_VALIDO.test(valor || '') ? valor : null;
  });
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [rutaSeleccionada, setRutaSeleccionada] = useState(null);
  const [fotogramas, setFotogramas] = useState([]);
  const [planes, setPlanes] = useState([]);
  const [indiceFotograma, setIndiceFotograma] = useState(0);
  const [reproduciendo, setReproduciendo] = useState(true);
  const [pasoMs, setPasoMs] = useState(450);
  const [velocidad, setVelocidad] = useState(60);
  const [duracionObjetivo, setDuracionObjetivo] = useState(40);
  const [enVivo, setEnVivo] = useState(true);
  const [ahora, setAhora] = useState(() => Date.now());
  const [eventosOperacion, setEventosOperacion] = useState([]);
  const relojAnclado = useRef(crearRelojAnclado());
  const desfase = useRef(0);
  const [inventario, setInventario] = useState(null);
  const [averias, setAverias] = useState([]);
  const [unidadAveriada, setUnidadAveriada] = useState('');
  const [tipoAveria, setTipoAveria] = useState('TIPO_1');
  const [seccion, setSeccion] = useState('operacion');

  useEffect(() => {
    let activo = true;
    api.salud().then(info => { if (activo) setFuente(info.fuente || 'ARCHIVOS'); }).catch(() => {});
    api.periodos().then(lista => { if (activo) setPeriodos(lista); })
      .catch(e => { if (activo) setError(`No se pudo cargar el catálogo: ${e.message}`); });
    return () => { activo = false; };
  }, []);

  useEffect(() => {
    if (!periodo) { setDetalle(null); setMapaDatos(null); return; }
    let activo = true;
    api.periodo(periodo).then(info => { if (activo) { setDetalle(info); setDia(anterior => anterior || info.primeraFechaPedido || ''); } })
      .catch(e => { if (activo) { setDetalle(null); setError(e.message); } });
    api.mapa(periodo).then(info => { if (activo) setMapaDatos(info); })
      .catch(e => { if (activo) { setMapaDatos(null); setError(e.message); } });
    return () => { activo = false; };
  }, [periodo]);

  useEffect(() => {
    if (!id) return undefined;
    let activo = true;
    let socket;
    let intervalo;
    let restaurado = false;
    const actualizar = vista => {
      if (!activo) return;
      if (vista.ritmo) desfase.current = desfaseConServidor(vista.ritmo, Date.now());
      setEjecucion(vista);
      if (!restaurado && vista.inicio) {
        restaurado = true;
        setPeriodo(vista.inicio.slice(0, 4) + vista.inicio.slice(5, 7));
        setDia(vista.inicio.slice(0, 10));
        setHora(vista.inicio.slice(11, 16));
        setEscenario(vista.escenario);
        setAlgoritmo(vista.algoritmo);
      }
      if (esFinal(vista.estado) && intervalo) clearInterval(intervalo);
    };
    const consultar = () => api.consultar(id).then(actualizar)
      .catch(e => {
        if (!activo) return;
        if (e.status === 404) {
          activo = false;
          clearInterval(intervalo);
          guardarEjecucion(null);
          history.replaceState(null, '', location.pathname);
          setEjecucion(null);
          setId(null);
          setError('El servidor se reinició o la corrida ya no existe: las corridas viven en memoria. Inicia una nueva corrida.');
          return;
        }
        setError(`No se pudo recuperar la ejecución: ${e.message}`);
      });
    consultar();
    intervalo = setInterval(consultar, 5000);
    try {
      socket = new WebSocket(urlWebSocket(id));
      socket.onopen = () => { if (activo) setAviso('Conexión en vivo activa.'); };
      socket.onmessage = evento => {
        try {
          const mensaje = JSON.parse(evento.data);
          if (mensaje.ejecucion) actualizar(mensaje.ejecucion);
        } catch { if (activo) setAviso('Evento no reconocido; se mantiene la consulta periódica.'); }
      };
      socket.onclose = () => { if (activo) setAviso('Conexión en vivo interrumpida; la vista se actualiza periódicamente.'); };
      socket.onerror = () => { if (activo) setAviso('Sin conexión en vivo; la vista se actualiza periódicamente.'); };
    } catch { setAviso('Sin conexión en vivo; la vista se actualiza periódicamente.'); }
    return () => { activo = false; clearInterval(intervalo); socket?.close(); };
  }, [id]);

  useEffect(() => {
    setInventario(null);
    setAverias([]);
    if (!id) return undefined;
    let activo = true;
    const cargar = () => Promise.all([api.inventario(id), api.averias(id)])
      .then(([stock, eventos]) => { if (activo) { setInventario(stock); setAverias(eventos); } })
      .catch(() => {});
    cargar();
    const intervalo = setInterval(cargar, 1000);
    return () => { activo = false; clearInterval(intervalo); };
  }, [id]);

  useEffect(() => {
    relojAnclado.current = crearRelojAnclado();
    setEnVivo(true);
    setEventosOperacion([]);
    if (!id) return undefined;
    let activo = true;
    let siguiente = 0;
    let consultando = false;
    let completo = false;
    const cargar = async () => {
      if (!activo || consultando || completo) return;
      consultando = true;
      try {
        const pagina = await api.eventos(id, siguiente);
        if (!activo) return;
        if (pagina.primero > siguiente) setEventosOperacion(pagina.eventos);
        else if (pagina.eventos.length) setEventosOperacion(actual => [...actual, ...pagina.eventos]);
        siguiente = pagina.siguiente;
        completo = pagina.completo && pagina.eventos.length < 100;
      } catch { /* la consulta principal informa los errores */ }
      finally { consultando = false; }
    };
    cargar();
    const intervalo = setInterval(cargar, 1500);
    return () => { activo = false; clearInterval(intervalo); };
  }, [id]);

  const corriendo = !!ejecucion && (ejecucion.estado === 'EN_CURSO' || ejecucion.estado === 'PAUSADA');
  const modoVivo = corriendo && enVivo && !!ejecucion.ritmo;

  // Latido de pantalla (~10 por segundo); el instante sale del reloj anclado, no del latido.
  useEffect(() => {
    if (!corriendo) return undefined;
    let cuadro;
    let ultimo = 0;
    const latir = marca => {
      if (marca - ultimo >= 100) { ultimo = marca; setAhora(Date.now()); }
      cuadro = requestAnimationFrame(latir);
    };
    cuadro = requestAnimationFrame(latir);
    return () => cancelAnimationFrame(cuadro);
  }, [corriendo]);

  const instanteVivoMs = useMemo(() => modoVivo
    ? relojAnclado.current(ejecucion.ritmo, ahora, desfase.current) : null, [modoVivo, ejecucion, ahora]);

  useEffect(() => {
    setFotogramas([]);
    setIndiceFotograma(0);
    setReproduciendo(true);
    if (!id) return undefined;
    let activo = true;
    let siguiente = 0;
    let consultando = false;
    let completo = false;
    const cargar = async () => {
      if (!activo || consultando || completo) return;
      consultando = true;
      try {
        const pagina = await api.fotogramas(id, siguiente);
        if (!activo) return;
        if (pagina.primero > siguiente) {
          setFotogramas(pagina.fotogramas);
          setIndiceFotograma(0);
        } else if (pagina.fotogramas.length) {
          setFotogramas(actual => [...actual, ...pagina.fotogramas]);
        }
        siguiente = pagina.siguiente;
        completo = pagina.completo && pagina.fotogramas.length < 100;
      } catch (error) {
        if (error.status === 404) completo = true;
        if (activo) setAviso(error.status === 404
          ? 'El backend aún no ofrece reproducción; se muestra la última instantánea.'
          : 'Reproducción temporalmente no disponible; se muestra la última instantánea.');
      } finally { consultando = false; }
    };
    cargar();
    const intervalo = setInterval(cargar, 900);
    return () => { activo = false; clearInterval(intervalo); };
  }, [id]);

  useEffect(() => {
    setPlanes([]);
    if (!id) return undefined;
    let activo = true;
    let siguiente = 0;
    let consultando = false;
    let completo = false;
    const cargar = async () => {
      if (!activo || consultando || completo) return;
      consultando = true;
      try {
        const pagina = await api.planes(id, siguiente);
        if (!activo) return;
        if (pagina.primero > siguiente) setPlanes(pagina.planes);
        else if (pagina.planes.length) setPlanes(actual => [...actual, ...pagina.planes]);
        siguiente = pagina.siguiente;
        completo = pagina.completo && pagina.planes.length < 100;
      } catch (e) {
        if (activo) setAviso(`No se pudo recuperar el historial de planes: ${e.message}`);
      } finally { consultando = false; }
    };
    cargar();
    const intervalo = setInterval(cargar, 900);
    return () => { activo = false; clearInterval(intervalo); };
  }, [id]);

  useEffect(() => {
    if (!modoVivo) return;
    setReproduciendo(false);
    setIndiceFotograma(Math.max(0, fotogramas.length - 1));
  }, [modoVivo, fotogramas.length]);

  useEffect(() => {
    if (!reproduciendo || fotogramas.length < 2 || modoVivo) return undefined;
    const intervalo = setInterval(() => setIndiceFotograma(actual =>
      Math.min(actual + 1, fotogramas.length - 1)), pasoMs);
    return () => clearInterval(intervalo);
  }, [reproduciendo, fotogramas.length, pasoMs, modoVivo]);

  useEffect(() => {
    if (ejecucion && esFinal(ejecucion.estado) && fotogramas.length > 0
        && indiceFotograma >= fotogramas.length - 1) setReproduciendo(false);
  }, [ejecucion?.estado, fotogramas.length, indiceFotograma]);

  const vehiculosActivos = useMemo(() => ejecucion?.vehiculos?.length ?? null, [ejecucion]);
  const flotaParaAveria = fotogramas[0]?.vehiculos || ejecucion?.vehiculos || LISTA_VACIA;
  const planificados = useMemo(() => (ejecucion?.rutas || []).reduce((total, ruta) => total + ruta.paradas.length, 0), [ejecucion]);
  const instanteVivo = Number.isFinite(instanteVivoMs) ? aIso(instanteVivoMs) : null;
  const instanteReproducido = modoVivo ? instanteVivo || ejecucion.reloj
    : fotogramas[indiceFotograma]?.reloj || ejecucion?.reloj || null;
  const planReproducido = planVigenteEn(planes, instanteReproducido);
  const rutasReproducidas = modoVivo ? planReproducido?.rutas || ejecucion.rutas || LISTA_VACIA
    : planReproducido?.rutas || (!fotogramas.length ? ejecucion?.rutas : null) || LISTA_VACIA;
  const enInstanteActual = modoVivo || !fotogramas.length || indiceFotograma >= fotogramas.length - 1;
  const calculandoRutas = !!ejecucion && (ejecucion.estado === 'PENDIENTE'
    || (ejecucion.estado === 'EN_CURSO' && ejecucion.calculandoRutas && enInstanteActual));
  const rutasVisibles = calculandoRutas ? LISTA_VACIA : rutasReproducidas;

  function cambiarPeriodo(valor) {
    setPeriodo(valor);
    setDia('');
    setError('');
  }

  async function iniciar(evento) {
    evento.preventDefault();
    setError('');
    const diaIngresado = evento.currentTarget.elements.namedItem('dia').value;
    const horaIngresada = evento.currentTarget.elements.namedItem('hora').value;
    if (!periodo || !diaIngresado || diaIngresado.replaceAll('-', '').slice(0, 6) !== periodo) {
      setError('Selecciona una fecha perteneciente al periodo disponible.');
      return;
    }
    setDia(diaIngresado);
    setHora(horaIngresada);
    setOcupado(true);
    try {
      const nueva = await api.iniciar({
        escenario, algoritmo, inicio: `${diaIngresado}T${horaIngresada}:00`,
        ...(escenario === 'COLAPSO' ? { horizonteDias: Number(horizonte) } : {}),
        ...(escenario === 'PERIODO_5_DIAS' ? { duracionObjetivoMin: Number(duracionObjetivo) } : {}),
        ...(escenario === 'OPERACION_DIARIA' ? { velocidad: Number(velocidad) } : {})
      });
      setEjecucion(nueva);
      setRutaSeleccionada(null);
      guardarEjecucion(nueva.id);
      setId(nueva.id);
      setSeccion('operacion');
      history.replaceState(null, '', `?ejecucion=${encodeURIComponent(nueva.id)}`);
    } catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  async function cancelar() {
    if (!id) return;
    setOcupado(true);
    try { setEjecucion(await api.cancelar(id)); }
    catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  async function pausarOReanudar() {
    if (!id || !ejecucion) return;
    setOcupado(true);
    try {
      const vista = ejecucion.estado === 'PAUSADA' ? await api.reanudar(id) : await api.pausar(id);
      if (vista.ritmo) desfase.current = desfaseConServidor(vista.ritmo, Date.now());
      setEjecucion(vista);
    } catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  async function registrarAveria(evento) {
    evento.preventDefault();
    if (!id || !unidadAveriada) return;
    setOcupado(true);
    try {
      await api.registrarAveria(id, { unidadId: unidadAveriada, tipo: tipoAveria });
      setAverias(await api.averias(id));
      setAviso(`Avería registrada para ${unidadAveriada}; se aplica en el siguiente corte y sus pedidos se replanifican.`);
    } catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  return <div className="aplicacion">
    <aside className="barra-lateral">
      <div className="marca"><span className="marca-simbolo">P</span><div><b>PaqRap</b><small>Sistema de Entrega Rápida</small></div></div>
      <div className="beta-pill">BETA · DATOS DEL CURSO</div>
      <nav aria-label="Secciones principales" className="navegacion">
        {[['operacion', 'Operación'], ['pedidos', 'Pedidos'], ['incidencias', 'Alertas']].map(([clave, titulo]) =>
          <button key={clave} type="button" className={seccion === clave ? 'activo' : ''} onClick={() => setSeccion(clave)}>{titulo}</button>)}
      </nav>
      <div className="barra-pie"><strong>{fuente === 'MYSQL' ? 'Datos en MySQL' : 'Datos desde archivos'}</strong><span>Las corridas viven en memoria del servidor: al recargar se retoma la corrida, pero si el servidor se reinicia hay que iniciar otra.</span></div>
    </aside>

    <main className="contenido">
      <header className="cabecera"><div><span className="sobrelinea">PAQRAP / VISUALIZADOR</span><h1>{seccion === 'operacion' ? 'Operación y replanificación' : seccion === 'pedidos' ? 'Pedidos del plan' : 'Alertas del escenario'}</h1></div><div className="cabecera-estado"><span className={`estado-ejecucion ${ejecucion?.estado || ''}`}>{ejecucion ? ETIQUETAS[ejecucion.estado] : 'Sin ejecución'}</span><small>{ejecucion ? `Reloj: ${fecha(modoVivo ? instanteReproducido : ejecucion.reloj)}` : 'Esperando configuración'}</small></div></header>

      {error && <div role="alert" className="mensaje-error"><b>Atención</b><span>{error}</span><button type="button" onClick={() => setError('')} aria-label="Cerrar mensaje">×</button></div>}

      {seccion === 'operacion' && <>
        <div className="encabezado-seccion"><div><span className="sobrelinea">ESCENARIOS</span><h2>Configura una corrida</h2></div><p>Los resultados provienen del planificador y del simulador Java, no del prototipo estático.</p></div>
        <div className="escenarios">{ESCENARIOS.map(opcion => <button type="button" key={opcion.id} className={`escenario ${escenario === opcion.id ? 'escenario-activo' : ''}`} onClick={() => setEscenario(opcion.id)}><span>{opcion.nombre}</span><small>{opcion.ayuda}</small></button>)}</div>
        <div className="cuerpo-operacion"><div className="config-col"><form className="panel configuracion" onSubmit={iniciar}>
          <div className="panel-titulo"><div><h2>Datos y configuración</h2><p>Selecciona un periodo existente y la fecha de inicio.</p></div></div>
          <label>Periodo de datos<select value={periodo} onChange={e => cambiarPeriodo(e.target.value)} required><option value="">Seleccionar periodo</option>{periodos.map(valor => <option key={valor} value={valor}>{valor.slice(0, 4)}-{valor.slice(4)}</option>)}</select></label>
          {detalle && <p className="dato-periodo">{numero(detalle.pedidos)} pedidos · {numero(detalle.bloqueos)} bloqueos · {numero(detalle.jornadasMantenimiento)} jornadas de mantenimiento</p>}
          <div className="fila-campos"><label>Fecha inicial<input name="dia" type="date" value={dia} onChange={e => setDia(e.target.value)} required /></label><label>Hora inicial<input name="hora" type="time" value={hora} onChange={e => setHora(e.target.value)} required /></label></div>
          <label>Algoritmo<select value={algoritmo} onChange={e => setAlgoritmo(e.target.value)}><option value="GRASP">GRASP</option><option value="TABU">Vecino Más Cercano + Búsqueda Tabú</option></select></label>
          {escenario === 'COLAPSO' && <label>Horizonte máximo (días)<input type="number" min="1" max="60" value={horizonte} onChange={e => setHorizonte(e.target.value)} required /></label>}
          {escenario === 'PERIODO_5_DIAS' && <label>Duración objetivo (min reales)<input type="number" min="30" max="60" value={duracionObjetivo} onChange={e => setDuracionObjetivo(e.target.value)} required /></label>}
          {escenario === 'OPERACION_DIARIA' && <label>Velocidad<select value={velocidad} onChange={e => setVelocidad(Number(e.target.value))}><option value="1">1× · tiempo real</option><option value="10">10×</option><option value="60">60×</option><option value="240">240×</option></select></label>}
          <p className="nota-formulario">{ritmoConfigurado(escenario, duracionObjetivo, velocidad)}</p>
          <div className="acciones"><button className="boton-primario" type="submit" disabled={ocupado || !periodos.length || corriendo}>{ocupado ? 'Procesando…' : 'Iniciar escenario'}</button><button className="boton-secundario" type="button" disabled={ocupado || !corriendo} onClick={pausarOReanudar}>{ejecucion?.estado === 'PAUSADA' ? 'Reanudar' : 'Pausar'}</button><button className="boton-secundario" type="button" disabled={ocupado || !ejecucion || esFinal(ejecucion.estado)} onClick={cancelar}>Detener</button></div>
          {aviso && <p className="aviso-conexion" role="status">{aviso}</p>}
          <p className="nota-formulario">La ejecución es asincrónica. Puedes abrir el mismo enlace en otro dispositivo para consultar su estado.</p>
        </form>
        <form className="panel configuracion" onSubmit={registrarAveria}>
          <div className="panel-titulo"><div><h2>Avería de una unidad</h2><p>Retira temporalmente el vehículo y replantea los pedidos afectados.</p></div></div>
          <label>Vehículo<select value={unidadAveriada} onChange={e => setUnidadAveriada(e.target.value)} required><option value="">Seleccionar vehículo</option>{flotaParaAveria.map(v => <option key={v.id} value={v.id}>{v.id} · {v.tipo}</option>)}</select></label>
          <label>Tipo<select value={tipoAveria} onChange={e => setTipoAveria(e.target.value)}><option value="TIPO_1">Tipo 1 · reparación en sitio</option><option value="TIPO_2">Tipo 2 · vuelve al central</option><option value="TIPO_3">Tipo 3 · mantenimiento prolongado</option></select></label>
          <button className="boton-secundario" type="submit" disabled={ocupado || !unidadAveriada || !corriendo}>Registrar avería</button>
          {averias.length > 0 && <ul className="averias-lista">{averias.map(a => <li key={`${a.unidadId}-${a.instante}`}>{a.unidadId} · {a.tipo} · {a.estado} · {fecha(a.instante)} · {numero(a.pedidosAfectados || 0)} pedido(s) replanificado(s)</li>)}</ul>}
        </form></div>
        <MapaOperacion key={id || 'sin-ejecucion'} ejecucion={ejecucion} rutas={rutasVisibles} calculandoRutas={calculandoRutas}
          rutaSeleccionada={rutaSeleccionada} mapaDatos={mapaDatos} inventario={inventario} fotogramas={fotogramas}
          indiceFotograma={indiceFotograma} reproduciendo={reproduciendo} pasoMs={pasoMs}
          enVivo={modoVivo} trayectosEnVivo={modoVivo ? ejecucion.trayectos || LISTA_VACIA : null}
          alVolverEnVivo={corriendo && !enVivo ? () => setEnVivo(true) : null}
          alCambiarFotograma={indice => { setEnVivo(false); setReproduciendo(false); setIndiceFotograma(indice); }}
          alReproducir={() => {
            if (!reproduciendo && indiceFotograma >= fotogramas.length - 1) setIndiceFotograma(0);
            setReproduciendo(!reproduciendo);
          }} alCambiarPaso={setPasoMs}
          instante={instanteReproducido || (dia ? `${dia}T${hora}:00` : null)} /></div>
        <div className="metricas">
          <TarjetaMetrica etiqueta="Pedidos entregados" valor={numero(ejecucion?.avance?.pedidosEntregados)} nota="Entrega efectiva acumulada" />
          <TarjetaMetrica etiqueta="Productos entregados" valor={numero(ejecucion?.avance?.productosEntregados)} nota="Unidades acumuladas" />
          <TarjetaMetrica etiqueta="Vehículos" valor={numero(vehiculosActivos)} nota="Presentes en la instantánea" />
          <TarjetaMetrica etiqueta="Paradas planificadas" valor={ejecucion ? numero(planificados) : '—'} nota="No equivalen a entregas" />
          <TarjetaMetrica etiqueta="Pedidos sin asignar" valor={ejecucion ? numero(ejecucion.noAsignados?.length || 0) : '—'} nota="Última planificación" alerta={!!ejecucion?.noAsignados?.length} />
        </div>
        {ejecucion?.colapso && <PanelColapso ejecucion={ejecucion} />}
        <div className="dos-columnas"><ListaRutas rutas={rutasVisibles} seleccion={rutaSeleccionada} alSeleccionar={setRutaSeleccionada} calculandoRutas={calculandoRutas} /><section className="panel resumen"><div className="panel-titulo"><div><h2>Resumen de la corrida</h2><p>Disponible al terminar o detectar colapso.</p></div></div>{!ejecucion?.resumen ? <p className="vacio">Todavía no hay un resumen final.</p> : <dl><div><dt>Pedidos recibidos</dt><dd>{numero(ejecucion.resumen.pedidosRecibidos)}</dd></div><div><dt>Pedidos completos / en plazo</dt><dd>{numero(ejecucion.resumen.pedidosOriginalesCompletos)} / {numero(ejecucion.resumen.pedidosOriginalesEnPlazo)}</dd></div><div><dt>Pedidos pendientes</dt><dd>{numero(ejecucion.resumen.pedidosPendientes)}</dd></div><div><dt>Distancia total</dt><dd>{decimal(ejecucion.resumen.distanciaTotalKm, ' km')}</dd></div><div><dt>Costo total</dt><dd>S/ {decimal(ejecucion.resumen.costoTotal)}</dd></div><div><dt>Fin de simulación</dt><dd>{fecha(ejecucion.resumen.fin)}</dd></div>{ejecucion.ritmo && <div><dt>Ventanas · ritmo</dt><dd>{numero(ejecucion.ritmo.ventana)} · {textoRitmo(ejecucion.ritmo)}</dd></div>}</dl>}</section></div>
      </>}
      {seccion === 'pedidos' && <><div className="encabezado-seccion"><div><span className="sobrelinea">ESTADO ACTUAL</span><h2>Pedidos visibles</h2></div><p>No se pueden registrar pedidos desde esta beta; se leen de {fuente === 'MYSQL' ? 'MySQL' : 'los archivos del curso'}.</p></div><TablaPedidos ejecucion={ejecucion} /></>}
      {seccion === 'incidencias' && <><div className="encabezado-seccion"><div><span className="sobrelinea">MONITOREO</span><h2>Alertas y excepciones</h2></div><p>Se muestran únicamente hechos informados por la corrida activa.</p></div><PanelIncidencias ejecucion={ejecucion} averias={averias} eventosOperacion={eventosOperacion} /></>}
      <footer className="pie">PaqRap · Beta académica. No usar para decisiones logísticas reales.</footer>
    </main>
  </div>;
}
