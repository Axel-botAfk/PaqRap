import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api, esFinal, urlWebSocket } from './services/api.js';
import { planVigenteEn, estadosDePedido } from './mapa.js';
import { aIso, aMs, crearRelojAnclado, desfaseConServidor, transcurridoObjetivo } from './reloj.js';
import { fecha, fechaConSegundos, numero, duracion, VELOCIDAD_DIA_A_DIA,
  DURACION_5_DIAS_MIN } from './formato.js';
import BarraSuperior from './components/BarraSuperior.jsx';
import BarraLateral, { VISTAS } from './components/BarraLateral.jsx';
import Riel from './components/Riel.jsx';
import Mapa from './components/Mapa.jsx';
import RielCorrida from './pages/RielCorrida.jsx';
import HojaPedidos from './pages/HojaPedidos.jsx';
import HojaAverias from './pages/HojaAverias.jsx';

const CLAVE_EJECUCION = 'paqrap.ejecucion';
const ID_VALIDO = /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i;
const LISTA_VACIA = [];
const ALGORITMO = 'TABU';          // el planificador del caso; ya no se elige desde la pantalla
const SIN_LIMITES = { desde: '', hasta: '' };

/** El periodo de datos (aaaamm) sale de la fecha elegida, no de un selector aparte. */
const periodoDe = dia => (dia && dia.length >= 7 ? dia.slice(0, 4) + dia.slice(5, 7) : '');
const TITULOS = Object.fromEntries(VISTAS);
const ES_MAPA = vista => vista === 'operacion' || vista === 'simulacion';

function leerEjecucionGuardada() {
  try { return localStorage.getItem(CLAVE_EJECUCION); } catch { return null; }
}

function guardarEjecucion(id) {
  try { id ? localStorage.setItem(CLAVE_EJECUCION, id) : localStorage.removeItem(CLAVE_EJECUCION); } catch { /* sin almacenamiento */ }
}

export default function App() {
  const [vista, setVista] = useState('operacion');
  const [escenario, setEscenario] = useState('OPERACION_DIARIA');
  const [periodos, setPeriodos] = useState([]);
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
  const [enVivo, setEnVivo] = useState(true);
  const [ahora, setAhora] = useState(() => Date.now());
  const [eventosOperacion, setEventosOperacion] = useState([]);
  const relojAnclado = useRef(crearRelojAnclado());
  const desfase = useRef(0);
  const [inventario, setInventario] = useState(null);
  const [averias, setAverias] = useState([]);
  const [unidadAveriada, setUnidadAveriada] = useState('');
  const [tipoAveria, setTipoAveria] = useState('TIPO_1');
  const [menuAbierto, setMenuAbierto] = useState(() => !esPantallaAngosta());
  // En pantallas angostas el riel ocupa todo el ancho: arranca cerrado para no tapar el mapa.
  const [rielAbierto, setRielAbierto] = useState(() => !esPantallaAngosta());

  const periodo = periodoDe(dia);
  // El calendario se acota al catálogo: así no se puede pedir un mes sin datos.
  const limites = useMemo(() => {
    if (!periodos.length) return SIN_LIMITES;
    const primero = periodos[0];
    const ultimo = periodos[periodos.length - 1];
    const finDeMes = new Date(Date.UTC(Number(ultimo.slice(0, 4)), Number(ultimo.slice(4)), 0));
    return { desde: `${primero.slice(0, 4)}-${primero.slice(4)}-01`,
      hasta: finDeMes.toISOString().slice(0, 10) };
  }, [periodos]);


  useEffect(() => {
    let activo = true;
    api.salud().then(info => { if (activo) setFuente(info.fuente || 'ARCHIVOS'); }).catch(() => {});
    api.periodos().then(lista => { if (activo) setPeriodos(lista); })
      .catch(e => { if (activo) setError(`No se pudo cargar el catálogo: ${e.message}`); });
    return () => { activo = false; };
  }, []);

  // Sin fecha todavía, se arranca en el primer día con datos del catálogo.
  useEffect(() => {
    if (dia || !periodos.length) return undefined;
    let activo = true;
    api.periodo(periodos[0])
      .then(info => { if (activo) setDia(info.primeraFechaPedido || `${periodos[0].slice(0, 4)}-${periodos[0].slice(4)}-01`); })
      .catch(() => {});
    return () => { activo = false; };
  }, [periodos, dia]);

  useEffect(() => {
    if (!periodo) { setDetalle(null); setMapaDatos(null); return undefined; }
    if (!periodos.includes(periodo)) {
      setDetalle(null);
      setMapaDatos(null);
      setError(`No hay datos para ${periodo.slice(0, 4)}-${periodo.slice(4)}. Elige una fecha dentro del catálogo.`);
      return undefined;
    }
    let activo = true;
    setError('');
    api.periodo(periodo).then(info => { if (activo) setDetalle(info); })
      .catch(e => { if (activo) { setDetalle(null); setError(e.message); } });
    api.mapa(periodo).then(info => { if (activo) setMapaDatos(info); })
      .catch(e => { if (activo) { setMapaDatos(null); setError(e.message); } });
    return () => { activo = false; };
  }, [periodo, periodos]);

  useEffect(() => {
    if (!id) return undefined;
    let activo = true;
    let socket;
    let intervalo;
    let restaurado = false;
    const actualizar = datos => {
      if (!activo) return;
      if (datos.ritmo) desfase.current = desfaseConServidor(datos.ritmo, Date.now());
      setEjecucion(datos);
      if (!restaurado && datos.inicio) {
        restaurado = true;
        setDia(datos.inicio.slice(0, 10));
        setHora(datos.inicio.slice(11, 16));
        setEscenario(datos.escenario);
        // La corrida recuperada manda: su escenario decide en qué vista de mapa se retoma.
        setVista(datos.escenario === 'OPERACION_DIARIA' ? 'operacion' : 'simulacion');
      }
      if (esFinal(datos.estado) && intervalo) clearInterval(intervalo);
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
      } catch (fallo) {
        if (fallo.status === 404) completo = true;
        if (activo) setAviso(fallo.status === 404
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

  const flotaParaAveria = fotogramas[0]?.vehiculos || ejecucion?.vehiculos || LISTA_VACIA;
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

  // «En ruta» y «entregado» no existen en la API: se deducen de los hitos de los trayectos.
  const estadosPedido = useMemo(() => estadosDePedido(planes, aMs(instanteReproducido), aMs),
    [planes, instanteReproducido]);
  // Las cuatro situaciones de la barra superior. En ruta, planificados y por planificar se
  // cuentan sobre el plan vigente, igual que la hoja de Pedidos. Los entregados no: al
  // entregarse el pedido sale del plan, así que el único conteo que no se pierde al
  // replanificar es el acumulado de `avance`.
  const resumenPedidos = useMemo(() => {
    if (!ejecucion) return { entregados: null, enRuta: null, planificados: null, porPlanificar: null };
    let enRuta = 0, planificados = 0;
    for (const ruta of ejecucion.rutas || LISTA_VACIA) {
      for (const parada of ruta.paradas || LISTA_VACIA) {
        const estado = estadosPedido.get(parada.id);
        if (estado === 'EN_RUTA') enRuta += 1;
        else if (!estado) planificados += 1;
      }
    }
    return {
      entregados: ejecucion.avance?.pedidosEntregados ?? 0,
      enRuta, planificados,
      porPlanificar: ejecucion.noAsignados?.length || 0
    };
  }, [ejecucion, estadosPedido]);

  // Tiempo real de la corrida: ventanas ya publicadas más lo reproducido de la actual.
  const ritmo = ejecucion?.ritmo;
  const transcurridoMs = ritmo
    ? Math.max(0, ritmo.ventana - 1) * ritmo.saMs + transcurridoObjetivo(ritmo, ahora, desfase.current)
    : null;
  const progreso = ritmo?.ventanas ? Math.min(1, ritmo.ventana / ritmo.ventanas) : 0;

  function cambiarVista(destino) {
    setVista(destino);
    if (destino === 'operacion') setEscenario('OPERACION_DIARIA');
    else if (destino === 'simulacion' && escenario === 'OPERACION_DIARIA') setEscenario('PERIODO_5_DIAS');
    if (esPantallaAngosta()) setMenuAbierto(false);
  }

  async function iniciar(evento) {
    evento.preventDefault();
    setError('');
    const diaIngresado = evento.currentTarget.elements.namedItem('dia').value;
    const horaIngresada = evento.currentTarget.elements.namedItem('hora').value;
    const periodoElegido = periodoDe(diaIngresado);
    if (!periodoElegido || !periodos.includes(periodoElegido)) {
      setError('Elige una fecha dentro de un mes con datos cargados.');
      return;
    }
    setDia(diaIngresado);
    setHora(horaIngresada);
    setOcupado(true);
    try {
      const nueva = await api.iniciar({
        escenario, algoritmo: ALGORITMO, inicio: `${diaIngresado}T${horaIngresada}:00`,
        ...(escenario === 'COLAPSO' ? { horizonteDias: Number(horizonte) } : {}),
        ...(escenario === 'PERIODO_5_DIAS' ? { duracionObjetivoMin: DURACION_5_DIAS_MIN } : {}),
        ...(escenario === 'OPERACION_DIARIA' ? { velocidad: VELOCIDAD_DIA_A_DIA } : {})
      });
      setEjecucion(nueva);
      setRutaSeleccionada(null);
      guardarEjecucion(nueva.id);
      setId(nueva.id);
      history.replaceState(null, '', `?ejecucion=${encodeURIComponent(nueva.id)}`);
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

  // La franja superior solo reparte los pedidos entre sus cuatro situaciones; el resto de
  // cifras de la corrida vive en el riel y en el mapa.
  const chips = ES_MAPA(vista) ? [
    { etiqueta: 'ENTREGADOS', valor: numero(resumenPedidos.entregados),
      titulo: 'Acumulado de la corrida: incluye los que ya salieron del plan vigente.' },
    { etiqueta: 'EN RUTA', valor: numero(resumenPedidos.enRuta),
      titulo: 'Su unidad ya salió y el pedido sigue pendiente. Deducido de los hitos del simulador.' },
    { etiqueta: 'PLANIFICADOS', valor: numero(resumenPedidos.planificados),
      titulo: 'Con ruta en el plan vigente, pero su unidad todavía no sale.' },
    { etiqueta: 'POR PLANIFICAR', valor: numero(resumenPedidos.porPlanificar),
      alerta: !!resumenPedidos.porPlanificar,
      titulo: 'Sin ruta asignada en el plan vigente.' }
  ] : [];

  const meta = vista === 'simulacion' ? [
    { etiqueta: 'Fecha y hora de simulación', valor: fechaConSegundos(instanteReproducido) },
    { etiqueta: 'Duración', valor: duracion(transcurridoMs) }
  ] : vista === 'operacion' ? [
    { etiqueta: modoVivo ? 'En vivo' : 'Reloj', valor: fecha(instanteReproducido) }
  ] : [];

  const configuracion = {
    escenario, setEscenario, detalle, limites,
    dia, setDia, hora, setHora, horizonte, setHorizonte,
    unidadAveriada, setUnidadAveriada, tipoAveria, setTipoAveria,
    flota: flotaParaAveria, alIniciar: iniciar, alRegistrarAveria: registrarAveria
  };

  const enMapa = ES_MAPA(vista);

  return <div className={`app ${menuAbierto ? '' : 'mini'} ${enMapa ? 'mapview' : ''} ${enMapa && rielAbierto ? 'con-riel' : ''}`}>
    <BarraSuperior titulo={TITULOS[vista]} menuAbierto={menuAbierto} ejecucion={ejecucion}
      alAlternarMenu={() => setMenuAbierto(!menuAbierto)} chips={chips} meta={meta} />

    <BarraLateral vista={vista} alCambiar={cambiarVista} fuente={fuente} />
    {menuAbierto && <div className="scrim" onClick={() => setMenuAbierto(false)} />}

    {error && <div role="alert" className="mensaje-error">
      <b>Atención</b><span>{error}</span>
      <button type="button" onClick={() => setError('')} aria-label="Cerrar mensaje">×</button>
    </div>}

    {enMapa && <>
      <section className="mapsec">
        <Mapa key={id || 'sin-ejecucion'} ejecucion={ejecucion} rutas={rutasVisibles}
          calculandoRutas={calculandoRutas} rutaSeleccionada={rutaSeleccionada} mapaDatos={mapaDatos}
          inventario={inventario} fotogramas={fotogramas} indiceFotograma={indiceFotograma}
          reproduciendo={reproduciendo} pasoMs={pasoMs} estadosPedido={estadosPedido}
          enVivo={modoVivo} trayectosEnVivo={modoVivo ? ejecucion.trayectos || LISTA_VACIA : null}
          alVolverEnVivo={corriendo && !enVivo ? () => setEnVivo(true) : null}
          noAsignadosDelInstante={planReproducido?.noAsignados || null}
          alCambiarFotograma={indice => { setEnVivo(false); setReproduciendo(false); setIndiceFotograma(indice); }}
          alReproducir={() => {
            if (!reproduciendo && indiceFotograma >= fotogramas.length - 1) setIndiceFotograma(0);
            setReproduciendo(!reproduciendo);
          }} alCambiarPaso={setPasoMs}
          instante={instanteReproducido || (dia ? `${dia}T${hora}:00` : null)} />
      </section>

      <Riel titulo={vista === 'simulacion' ? 'Simulación' : 'Operación'} abierto={rielAbierto}
        alAlternar={() => setRielAbierto(!rielAbierto)}>
        <RielCorrida vista={vista} cfg={configuracion} ejecucion={ejecucion} corriendo={corriendo}
          ocupado={ocupado} rutas={rutasVisibles} calculandoRutas={calculandoRutas}
          rutaSeleccionada={rutaSeleccionada} alSeleccionarRuta={setRutaSeleccionada}
          inventario={inventario} mapaDatos={mapaDatos} averias={averias} aviso={aviso}
          progreso={progreso} />
      </Riel>
    </>}

    {vista === 'pedidos' && <div className="sheet sheet-pedidos">
      <HojaPedidos ejecucion={ejecucion} estadosPedido={estadosPedido} planes={planes}
        periodo={periodo} mapaDatos={mapaDatos} fuente={fuente} />
    </div>}
    {vista === 'averias' && <div className="sheet sheet-averias">
      <HojaAverias ejecucion={ejecucion} averias={averias} eventosOperacion={eventosOperacion} />
    </div>}
  </div>;
}

function esPantallaAngosta() {
  return typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches;
}
