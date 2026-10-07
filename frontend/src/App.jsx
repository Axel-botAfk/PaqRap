import React, { useEffect, useMemo, useState } from 'react';
import { api, esFinal, urlWebSocket } from './services/api.js';

const ESCENARIOS = [
  { id: 'OPERACION_DIARIA', nombre: 'Operación día a día', ayuda: 'Planificación y avance de un día.' },
  { id: 'PERIODO_5_DIAS', nombre: 'Simulación de 5 días', ayuda: 'Comparación temporal de cinco jornadas.' },
  { id: 'COLAPSO', nombre: 'Colapso logístico', ayuda: 'Detecta el primer incumplimiento dentro del horizonte.' }
];

const ETIQUETAS = {
  PENDIENTE: 'En espera', EN_CURSO: 'En ejecución', TERMINADA: 'Terminada',
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

function MapaOperacion({ ejecucion, rutaSeleccionada, mapaDatos, instante }) {
  const rutas = ejecucion?.rutas || [];
  const vehiculos = ejecucion?.vehiculos || [];
  const noAsignados = ejecucion?.noAsignados || [];
  const almacenes = mapaDatos?.almacenes || [];
  const bloqueosActivos = (mapaDatos?.bloqueos || []).filter(b => instante && b.inicio <= instante && b.fin > instante);
  const puntos = rutas.flatMap(ruta => ruta.paradas.map(parada => parada.destino));
  const vacio = rutas.length === 0 && vehiculos.length === 0 && noAsignados.length === 0 && !mapaDatos;
  const x = valor => Math.max(0, Math.min(690, (valor ?? 0) * 10));
  const y = valor => Math.max(0, Math.min(490, (valor ?? 0) * 10));

  return <div className="mapa-wrap">
    <div className="mapa-cabecera"><b>Visualizador de la operación</b><span>Grilla del modelo · 70 × 50 nodos</span></div>
    <svg className="mapa" viewBox="0 0 700 500" role="img" aria-label="Mapa esquemático de rutas, pedidos y vehículos">
      <rect width="700" height="500" fill="#f4f8f8" />
      {Array.from({ length: 8 }, (_, i) => <line key={`v${i}`} x1={i * 100} x2={i * 100} y1="0" y2="500" className="grid-line" />)}
      {Array.from({ length: 6 }, (_, i) => <line key={`h${i}`} x1="0" x2="700" y1={i * 100} y2={i * 100} className="grid-line" />)}
      {bloqueosActivos.flatMap((bloqueo, indice) => bloqueo.nodos.map((nodo, n) =>
        <rect key={`b${indice}-${n}`} x={x(nodo.x) - 4} y={y(nodo.y) - 4} width="8" height="8"
          fill="#ce5647" fillOpacity=".75"><title>{`Bloqueo activo hasta ${fecha(bloqueo.fin)}`}</title></rect>))}
      {rutas.map((ruta, indice) => {
        const coordenadas = [ruta.origen, ...ruta.paradas.map(p => p.destino)]
          .filter(Boolean).map(p => `${x(p.x)},${y(p.y)}`).join(' ');
        return <polyline key={`${ruta.id}-${indice}`} points={coordenadas} fill="none"
          stroke={ruta.id === rutaSeleccionada ? '#c64d39' : '#2a8494'}
          strokeWidth={ruta.id === rutaSeleccionada ? 4 : 1.8} strokeOpacity={rutaSeleccionada && ruta.id !== rutaSeleccionada ? .25 : .72} />;
      })}
      {puntos.map((p, indice) => <circle key={`p${indice}`} cx={x(p.x)} cy={y(p.y)} r="4" fill="#d89039" stroke="white" strokeWidth="1.5" />)}
      {noAsignados.map((pedido, indice) => <circle key={`n${indice}`} cx={x(pedido.destino?.x)} cy={y(pedido.destino?.y)} r="5" fill="#c64d39" stroke="white" strokeWidth="1.5" />)}
      {almacenes.map(almacen => <g key={almacen.id} transform={`translate(${x(almacen.ubicacion.x)},${y(almacen.ubicacion.y)})`}>
        <title>{`${almacen.id} · ${almacen.tipo} · stock inicial ${almacen.stockInicial}`}</title>
        {almacen.tipo === 'CENTRAL' ? <path d="M0 -11 11 0 0 11 -11 0Z" fill="#733e91" stroke="white" strokeWidth="2" />
          : <rect x="-9" y="-9" width="18" height="18" rx="2" fill="#a366ae" stroke="white" strokeWidth="2" />}
        <text x="0" y="4" textAnchor="middle" fontSize="10" fontWeight="800" fill="white">{almacen.tipo === 'CENTRAL' ? 'C' : 'I'}</text>
      </g>)}
      {vehiculos.map(vehiculo => <svg key={vehiculo.id}
        x={x(vehiculo.ubicacion?.x) - 13} y={y(vehiculo.ubicacion?.y) - 13}
        width="26" height="26" viewBox="0 0 32 32">
        <title>{`${vehiculo.id} · ${vehiculo.tipo} · ${vehiculo.estado}`}</title>
        <circle cx="16" cy="16" r="15" fill="white" stroke="#c8dce0" strokeWidth="1.5" />
        <DibujoVehiculo tipo={vehiculo.tipo} />
      </svg>)}
    </svg>
    {vacio && <div className="mapa-vacio"><b>Sin operación cargada</b><span>Selecciona un periodo, configura una corrida e iníciala para ver el mapa.</span></div>}
    <div className="leyenda"><span><i className="punto punto-ruta" /> Ruta planificada</span><span><i className="punto punto-pedido" /> Parada prevista</span><span><i className="punto punto-alerta" /> Sin asignar</span><span><i className="punto punto-bloqueo" /> Bloqueo activo ({bloqueosActivos.length})</span><span><i className="punto punto-central" /> Central</span><span><i className="punto punto-intermedio" /> Intermedio</span><IconoLeyenda tipo="AUTO" texto="Auto" /><IconoLeyenda tipo="MOTO" texto="Moto" /><IconoLeyenda tipo="BICICLETA" texto="Bicicleta" /></div>
    <p className="mapa-aviso">Las líneas muestran el plan vigente de forma esquemática; no son calles recorridas ni entregas confirmadas.</p>
  </div>;
}

function ListaRutas({ ejecucion, seleccion, alSeleccionar }) {
  const rutas = ejecucion?.rutas || [];
  return <section className="panel">
    <div className="panel-titulo"><div><h2>Rutas planificadas</h2><p>Se actualizan en cada replanificación.</p></div><span className="contador">{rutas.length}</span></div>
    {rutas.length === 0 ? <p className="vacio">Todavía no hay rutas para mostrar.</p> :
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

function PanelIncidencias({ ejecucion }) {
  const sinAsignar = ejecucion?.noAsignados || [];
  const colapso = ejecucion?.resumen?.huboColapso;
  const fallo = ejecucion?.estado === 'FALLIDA';
  const hayAlertas = sinAsignar.length > 0 || colapso || fallo;
  return <section className="panel">
    <div className="panel-titulo"><div><h2>Alertas observadas</h2><p>No hay registro persistente de incidencias en esta beta.</p></div><span className="contador">{sinAsignar.length + (colapso ? 1 : 0) + (fallo ? 1 : 0)}</span></div>
    {!hayAlertas && <p className="vacio">Sin alertas observadas en la instantánea actual.</p>}
    {colapso && <div className="alerta-lista"><b>Colapso logístico</b><span>Primer incumplimiento: {fecha(ejecucion.resumen.instanteColapso)}. Pedido: {ejecucion.resumen.pedidoQueColapso || 'no disponible'}.</span></div>}
    {fallo && <div className="alerta-lista"><b>Ejecución fallida</b><span>{ejecucion.mensajeError || 'La simulación no pudo completarse.'}</span></div>}
    {sinAsignar.slice(0, 20).map((pedido, indice) => <div className="alerta-lista" key={`${pedido.id}-${indice}`}><b>{pedido.id} · sin asignar</b><span>{pedido.motivo || 'Sin motivo específico disponible'} · plazo {fecha(pedido.fechaLimite)}</span></div>)}
    {sinAsignar.length > 20 && <p className="tabla-nota">Hay {numero(sinAsignar.length - 20)} pedidos adicionales sin asignar.</p>}
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
    const valor = new URLSearchParams(location.search).get('ejecucion');
    return /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i.test(valor || '') ? valor : null;
  });
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [rutaSeleccionada, setRutaSeleccionada] = useState(null);
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
      .catch(e => { if (activo) setError(`No se pudo recuperar la ejecución: ${e.message}`); });
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

  const vehiculosActivos = useMemo(() => ejecucion?.vehiculos?.length ?? null, [ejecucion]);
  const planificados = useMemo(() => (ejecucion?.rutas || []).reduce((total, ruta) => total + ruta.paradas.length, 0), [ejecucion]);

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
        ...(escenario === 'COLAPSO' ? { horizonteDias: Number(horizonte) } : {})
      });
      setEjecucion(nueva);
      setRutaSeleccionada(null);
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

  return <div className="aplicacion">
    <aside className="barra-lateral">
      <div className="marca"><span className="marca-simbolo">P</span><div><b>PaqRap</b><small>Sistema de Entrega Rápida</small></div></div>
      <div className="beta-pill">BETA · DATOS DEL CURSO</div>
      <nav aria-label="Secciones principales" className="navegacion">
        {[['operacion', 'Operación'], ['pedidos', 'Pedidos'], ['incidencias', 'Alertas']].map(([clave, titulo]) =>
          <button key={clave} type="button" className={seccion === clave ? 'activo' : ''} onClick={() => setSeccion(clave)}>{titulo}</button>)}
      </nav>
      <div className="barra-pie"><strong>{fuente === 'MYSQL' ? 'Datos en MySQL' : 'Datos desde archivos'}</strong><span>Las corridas viven temporalmente en memoria; el historial aún no es persistente.</span></div>
    </aside>

    <main className="contenido">
      <header className="cabecera"><div><span className="sobrelinea">PAQRAP / VISUALIZADOR</span><h1>{seccion === 'operacion' ? 'Operación y replanificación' : seccion === 'pedidos' ? 'Pedidos del plan' : 'Alertas del escenario'}</h1></div><div className="cabecera-estado"><span className={`estado-ejecucion ${ejecucion?.estado || ''}`}>{ejecucion ? ETIQUETAS[ejecucion.estado] : 'Sin ejecución'}</span><small>{ejecucion ? `Reloj: ${fecha(ejecucion.reloj)}` : 'Esperando configuración'}</small></div></header>

      {error && <div role="alert" className="mensaje-error"><b>Atención</b><span>{error}</span><button type="button" onClick={() => setError('')} aria-label="Cerrar mensaje">×</button></div>}

      {seccion === 'operacion' && <>
        <div className="encabezado-seccion"><div><span className="sobrelinea">ESCENARIOS</span><h2>Configura una corrida</h2></div><p>Los resultados provienen del planificador y del simulador Java, no del prototipo estático.</p></div>
        <div className="escenarios">{ESCENARIOS.map(opcion => <button type="button" key={opcion.id} className={`escenario ${escenario === opcion.id ? 'escenario-activo' : ''}`} onClick={() => setEscenario(opcion.id)}><span>{opcion.nombre}</span><small>{opcion.ayuda}</small></button>)}</div>
        <div className="cuerpo-operacion"><form className="panel configuracion" onSubmit={iniciar}>
          <div className="panel-titulo"><div><h2>Datos y configuración</h2><p>Selecciona un periodo existente y la fecha de inicio.</p></div></div>
          <label>Periodo de datos<select value={periodo} onChange={e => cambiarPeriodo(e.target.value)} required><option value="">Seleccionar periodo</option>{periodos.map(valor => <option key={valor} value={valor}>{valor.slice(0, 4)}-{valor.slice(4)}</option>)}</select></label>
          {detalle && <p className="dato-periodo">{numero(detalle.pedidos)} pedidos · {numero(detalle.bloqueos)} bloqueos · {numero(detalle.jornadasMantenimiento)} jornadas de mantenimiento</p>}
          <div className="fila-campos"><label>Fecha inicial<input name="dia" type="date" value={dia} onChange={e => setDia(e.target.value)} required /></label><label>Hora inicial<input name="hora" type="time" value={hora} onChange={e => setHora(e.target.value)} required /></label></div>
          <label>Algoritmo<select value={algoritmo} onChange={e => setAlgoritmo(e.target.value)}><option value="GRASP">GRASP</option><option value="TABU">Vecino Más Cercano + Búsqueda Tabú</option></select></label>
          {escenario === 'COLAPSO' && <label>Horizonte máximo (días)<input type="number" min="1" max="60" value={horizonte} onChange={e => setHorizonte(e.target.value)} required /></label>}
          <div className="acciones"><button className="boton-primario" type="submit" disabled={ocupado || !periodos.length}>{ocupado ? 'Procesando…' : 'Iniciar escenario'}</button><button className="boton-secundario" type="button" disabled={ocupado || !ejecucion || esFinal(ejecucion.estado)} onClick={cancelar}>Detener</button></div>
          {aviso && <p className="aviso-conexion" role="status">{aviso}</p>}
          <p className="nota-formulario">La ejecución es asincrónica. Puedes abrir el mismo enlace en otro dispositivo para consultar su estado.</p>
        </form>
        <MapaOperacion ejecucion={ejecucion} rutaSeleccionada={rutaSeleccionada} mapaDatos={mapaDatos}
          instante={ejecucion?.reloj || (dia ? `${dia}T${hora}:00` : null)} /></div>
        <div className="metricas">
          <TarjetaMetrica etiqueta="Pedidos entregados" valor={numero(ejecucion?.avance?.pedidosEntregados)} nota="Entrega efectiva acumulada" />
          <TarjetaMetrica etiqueta="Productos entregados" valor={numero(ejecucion?.avance?.productosEntregados)} nota="Unidades acumuladas" />
          <TarjetaMetrica etiqueta="Vehículos" valor={numero(vehiculosActivos)} nota="Presentes en la instantánea" />
          <TarjetaMetrica etiqueta="Paradas planificadas" valor={ejecucion ? numero(planificados) : '—'} nota="No equivalen a entregas" />
          <TarjetaMetrica etiqueta="Pedidos sin asignar" valor={ejecucion ? numero(ejecucion.noAsignados?.length || 0) : '—'} nota="Última planificación" alerta={!!ejecucion?.noAsignados?.length} />
        </div>
        <div className="dos-columnas"><ListaRutas ejecucion={ejecucion} seleccion={rutaSeleccionada} alSeleccionar={setRutaSeleccionada} /><section className="panel resumen"><div className="panel-titulo"><div><h2>Resumen de la corrida</h2><p>Disponible al terminar o detectar colapso.</p></div></div>{!ejecucion?.resumen ? <p className="vacio">Todavía no hay un resumen final.</p> : <dl><div><dt>Pedidos recibidos</dt><dd>{numero(ejecucion.resumen.pedidosRecibidos)}</dd></div><div><dt>Pedidos pendientes</dt><dd>{numero(ejecucion.resumen.pedidosPendientes)}</dd></div><div><dt>Distancia total</dt><dd>{decimal(ejecucion.resumen.distanciaTotalKm, ' km')}</dd></div><div><dt>Costo total</dt><dd>S/ {decimal(ejecucion.resumen.costoTotal)}</dd></div><div><dt>Fin de simulación</dt><dd>{fecha(ejecucion.resumen.fin)}</dd></div></dl>}</section></div>
      </>}
      {seccion === 'pedidos' && <><div className="encabezado-seccion"><div><span className="sobrelinea">ESTADO ACTUAL</span><h2>Pedidos visibles</h2></div><p>No se pueden registrar pedidos desde esta beta; se leen de {fuente === 'MYSQL' ? 'MySQL' : 'los archivos del curso'}.</p></div><TablaPedidos ejecucion={ejecucion} /></>}
      {seccion === 'incidencias' && <><div className="encabezado-seccion"><div><span className="sobrelinea">MONITOREO</span><h2>Alertas y excepciones</h2></div><p>Se muestran únicamente hechos informados por la corrida activa.</p></div><PanelIncidencias ejecucion={ejecucion} /></>}
      <footer className="pie">PaqRap · Beta académica. No usar para decisiones logísticas reales.</footer>
    </main>
  </div>;
}
