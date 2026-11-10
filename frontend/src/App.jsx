import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api, esFinal, urlWebSocket } from './services/api.js';
import { corridaRecuperable, unicaCorridaActiva, decidirCorrida, esActiva } from './conexionCorridas.js';
import { planVigenteEn, estadosDePedido, vehiculosObservadosEn } from './mapa.js';
import { averiasEn } from './averias.js';
import { pedidosEnInstante } from './pedidosEnVivo.js';
import { aIso, aMs, crearRelojAnclado, desfaseConServidor, transcurridoObjetivo,
  tiempoRealTranscurrido } from './reloj.js';
import { fecha, fechaConSegundos, numero, duracion, VELOCIDAD_DIA_A_DIA, VELOCIDADES_DIA_A_DIA,
  DURACION_5_DIAS_MIN } from './formato.js';
import BarraSuperior from './components/BarraSuperior.jsx';
import BarraLateral, { VISTAS } from './components/BarraLateral.jsx';
import Riel from './components/Riel.jsx';
import Mapa from './components/Mapa.jsx';
import RielCorrida from './pages/RielCorrida.jsx';
import { controlesDeCorrida } from './controlesCorrida.js';
import HojaPedidos from './pages/HojaPedidos.jsx';
import HojaAverias from './pages/HojaAverias.jsx';
import HojaFlota from './pages/HojaFlota.jsx';
import ReportePlan from './pages/ReportePlan.jsx';
import { inventarioEn } from './stock.js';
import { enviosEn, rutasDeEnvioEnMapa } from './envios.js';
import { leerUmbrales, guardarUmbrales, olvidarUmbrales } from './umbrales.js';

const CLAVE_EJECUCION = 'paqrap.ejecucion';
const CLAVE_POR_VISTA = vista => `${CLAVE_EJECUCION}.${vista}`;
const claveDeEscenario = escenario => escenario === 'OPERACION_DIARIA'
  ? 'operacion' : `simulacion.${escenario.toLowerCase()}`;
const ID_VALIDO = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const LISTA_VACIA = [];
const ALGORITMO = 'TABU';          // el planificador del caso; ya no se elige desde la pantalla
const SIN_LIMITES = { desde: '', hasta: '' };

/** El periodo de datos (aaaamm) sale de la fecha elegida, no de un selector aparte. */
const periodoDe = dia => (dia && dia.length >= 7 ? dia.slice(0, 4) + dia.slice(5, 7) : '');
const TITULOS = Object.fromEntries(VISTAS);
const ES_MAPA = vista => vista === 'operacion' || vista === 'simulacion';

function guardarEjecucion(id) {
  try { id ? localStorage.setItem(CLAVE_EJECUCION, id) : localStorage.removeItem(CLAVE_EJECUCION); } catch { /* sin almacenamiento */ }
}

function leerEjecucionDeVista(vista) {
  try { return localStorage.getItem(CLAVE_POR_VISTA(vista)); } catch { return null; }
}

function guardarEjecucionDeVista(vista, id) {
  try { id ? localStorage.setItem(CLAVE_POR_VISTA(vista), id)
    : localStorage.removeItem(CLAVE_POR_VISTA(vista)); } catch { /* sin almacenamiento */ }
}

const vistaDeEscenario = escenario => escenario === 'OPERACION_DIARIA' ? 'operacion' : 'simulacion';

export default function App() {
  const [vista, setVista] = useState('operacion');
  const vistaActual = useRef('operacion');
  const [escenario, setEscenario] = useState('OPERACION_DIARIA');
  const [periodos, setPeriodos] = useState([]);
  // Sube al cargar o quitar un mes de ventas, para releer el catálogo y el periodo elegido.
  const [versionDatos, setVersionDatos] = useState(0);
  const [detalle, setDetalle] = useState(null);
  const [mapaDatos, setMapaDatos] = useState(null);
  const [fuente, setFuente] = useState('ARCHIVOS');
  const [dia, setDia] = useState('');
  const [hora, setHora] = useState('00:00');
  const [horizonte, setHorizonte] = useState(30);
  // Ritmo de la operación día a día: tiempo real salvo que se elija la demostración acelerada.
  const [velocidadDiaria, setVelocidadDiaria] = useState(() => {
    try {
      const guardada = Number(localStorage.getItem('paqrap.velocidad.diaria'));
      return VELOCIDADES_DIA_A_DIA.some(([valor]) => valor === guardada) ? guardada : VELOCIDAD_DIA_A_DIA;
    } catch { return VELOCIDAD_DIA_A_DIA; }
  });
  const [ejecucion, setEjecucion] = useState(null);
  const [id, setId] = useState(() => {
    const valor = new URLSearchParams(location.search).get('ejecucion');
    return ID_VALIDO.test(valor || '') ? valor : null;
  });
  const idActual = useRef(id);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [corridasActivas, setCorridasActivas] = useState([]);
  const [consultandoCorridas, setConsultandoCorridas] = useState(false);
  const [crearIndependiente, setCrearIndependiente] = useState(false);
  const accionCorrida = useRef(false);
  const [rutaSeleccionada, setRutaSeleccionada] = useState(null);
  const [seleccionEnvio, setSeleccionEnvio] = useState(null);
  const [fotogramas, setFotogramas] = useState([]);
  const cantidadFotogramas = useRef(0);
  const cargarFotogramas = useRef(null);
  const cargarPlanes = useRef(null);
  const [planes, setPlanes] = useState([]);
  const [indiceFotograma, setIndiceFotograma] = useState(0);
  const [reproduciendo, setReproduciendo] = useState(false);
  const [reproduccionIniciada, setReproduccionIniciada] = useState(false);
  const [pasoMs, setPasoMs] = useState(300);
  const [enVivo, setEnVivo] = useState(true);
  const [ahora, setAhora] = useState(() => Date.now());
  const ejecucionesPorVista = useRef({
    operacion: leerEjecucionDeVista('operacion'),
    'simulacion.periodo_5_dias': leerEjecucionDeVista('simulacion.periodo_5_dias'),
    'simulacion.colapso': leerEjecucionDeVista('simulacion.colapso')
  });
  const restaurarVistaInicial = useRef(true);
  const escenarioSimulacion = useRef((() => {
    try { return localStorage.getItem('paqrap.escenario.simulacion') === 'COLAPSO'
      ? 'COLAPSO' : 'PERIODO_5_DIAS'; } catch { return 'PERIODO_5_DIAS'; }
  })());
  const [eventosOperacion, setEventosOperacion] = useState([]);
  const relojAnclado = useRef(crearRelojAnclado());
  const desfase = useRef(0);
  const [inventario, setInventario] = useState(null);
  const [averias, setAverias] = useState([]);
  const [unidadAveriada, setUnidadAveriada] = useState('');
  // Unidad resaltada: la comparten el panel de unidades y el mapa, en los dos sentidos (F01).
  const [unidadSeleccionada, setUnidadSeleccionada] = useState(null);
  const [almacenSeleccionado, setAlmacenSeleccionado] = useState(null);
  // Cortes del semáforo configurables (B06); se recuerdan en este navegador.
  const [umbrales, setUmbrales] = useState(leerUmbrales);
  const [tipoAveria, setTipoAveria] = useState('TIPO_1');
  // Reporte de la última planificación estable (G08 a G10): se abre solo al finalizar.
  const [reporteAbierto, setReporteAbierto] = useState(false);
  // Latencia desde «Iniciar» medida en este navegador (C03 y C04).
  const [medicionInicio, setMedicionInicio] = useState(null);
  const estadoPrevio = useRef(null);
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
  }, [versionDatos]);

  // Sin fecha todavía: la fecha y hora actuales si el catálogo tiene datos de este mes —la
  // operación día a día empieza «ahora»—; si no, el primer día con datos.
  useEffect(() => {
    if (dia || !periodos.length) return undefined;
    const actual = ahoraLocal();
    if (periodos.includes(periodoDe(actual.dia))) {
      setDia(actual.dia);
      setHora(actual.hora);
      return undefined;
    }
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
  }, [periodo, periodos, versionDatos]);

  // El descubrimiento pertenece al servidor y al escenario, no al almacenamiento del navegador.
  // Una sola corrida se recupera sin crear otra; con varias se espera una elección explícita.
  // Se consultan las corridas de todos los escenarios: quien entra desde otro dispositivo no
  // sabe cuál se inició en el primero. Mientras se mira una corrida ya finalizada también se
  // consulta, pero solo para ofrecer la que esté activa, sin quitar la que se está viendo.
  const viendoFinalizada = !!id && !!ejecucion && ejecucion.id === id && esFinal(ejecucion.estado);
  useEffect(() => {
    if (!ES_MAPA(vista) || (id && !viendoFinalizada) || ocupado || crearIndependiente) return undefined;
    let activo = true;
    let consultando = false;
    const consultarActivas = async () => {
      if (consultando) return;
      consultando = true;
      setConsultandoCorridas(true);
      try {
        const lista = await api.activas();
        if (!activo || vistaActual.current !== vista) return;
        if (viendoFinalizada) {
          if (idActual.current === id) setCorridasActivas(lista.filter(esActiva));
          return;
        }
        if (idActual.current) return;
        const decision = decidirCorrida(lista, escenario, restaurarVistaInicial.current);
        setCorridasActivas(decision.ofrecer);
        if (decision.adoptar) {
          const recuperada = await api.consultar(decision.adoptar.id);
          if (activo && !idActual.current && vistaActual.current === vista && esActiva(recuperada)) {
            adoptarCorrida(recuperada);
          }
        }
      } catch (e) {
        if (activo) setError(`No se pudieron consultar las corridas activas: ${e.message}`);
      } finally {
        consultando = false;
        if (activo) setConsultandoCorridas(false);
      }
    };
    consultarActivas();
    const intervalo = setInterval(consultarActivas, 5000);
    return () => { activo = false; clearInterval(intervalo); };
  }, [vista, escenario, id, viendoFinalizada, ocupado, crearIndependiente]);

  useEffect(() => {
    if (!id) return undefined;
    let activo = true;
    let socket;
    let intervalo;
    let temporizadorSocket;
    let reintentosSocket = 0;
    let restaurado = false;
    const actualizar = datos => {
      if (!activo || idActual.current !== id) return;
      if (datos.ritmo) desfase.current = desfaseConServidor(datos.ritmo, Date.now());
      setEjecucion(datos);
      const vistaCorrida = vistaDeEscenario(datos.escenario);
      const claveCorrida = claveDeEscenario(datos.escenario);
      ejecucionesPorVista.current[claveCorrida] = id;
      guardarEjecucionDeVista(claveCorrida, id);
      if (!restaurado && datos.inicio) {
        restaurado = true;
        setDia(datos.inicio.slice(0, 10));
        setHora(datos.inicio.slice(11, 16));
        setEscenario(datos.escenario);
        if (datos.escenario !== 'OPERACION_DIARIA') escenarioSimulacion.current = datos.escenario;
        if (restaurarVistaInicial.current) {
          setVista(vistaCorrida);
          vistaActual.current = vistaCorrida;
          restaurarVistaInicial.current = false;
        }
      }
      if (esFinal(datos.estado) && intervalo) clearInterval(intervalo);
    };
    const consultar = () => api.consultar(id).then(actualizar)
      .catch(e => {
        if (!activo || idActual.current !== id) return;
        if (e.status === 404) {
          activo = false;
          clearInterval(intervalo);
          guardarEjecucion(null);
          for (const vistaCorrida of ['operacion', 'simulacion.periodo_5_dias', 'simulacion.colapso']) {
            if (ejecucionesPorVista.current[vistaCorrida] === id) {
              ejecucionesPorVista.current[vistaCorrida] = null;
              guardarEjecucionDeVista(vistaCorrida, null);
            }
          }
          history.replaceState(null, '', location.pathname);
          setEjecucion(null);
          idActual.current = null;
          setId(null);
          setError('El servidor se reinició o la corrida ya no existe: las corridas viven en memoria. Inicia una nueva corrida.');
          return;
        }
        setError(`No se pudo recuperar la ejecución: ${e.message}`);
      });
    consultar();
    intervalo = setInterval(consultar, 5000);
    const recuperarHistorial = () => {
      if (cargarFotogramas.current?.id === id) cargarFotogramas.current.cargar();
      if (cargarPlanes.current?.id === id) cargarPlanes.current.cargar();
    };
    const reintentarSocket = () => {
      if (!activo || temporizadorSocket) return;
      const espera = Math.min(10000, 1000 * 2 ** reintentosSocket++);
      temporizadorSocket = setTimeout(() => { temporizadorSocket = null; abrirSocket(); }, espera);
    };
    const abrirSocket = () => {
      if (!activo) return;
      try {
        socket = new WebSocket(urlWebSocket(id));
        socket.onopen = () => {
          if (!activo) return;
          reintentosSocket = 0;
          setAviso('Conexión en vivo activa.');
          consultar();
          recuperarHistorial();
        };
        socket.onmessage = evento => {
          try {
            const mensaje = JSON.parse(evento.data);
            if (mensaje.ejecucion) { actualizar(mensaje.ejecucion); recuperarHistorial(); }
          } catch { if (activo) setAviso('Evento no reconocido; se mantiene la consulta periódica.'); }
        };
        socket.onclose = () => {
          if (!activo) return;
          setAviso('Conexión en vivo interrumpida; la vista se actualiza periódicamente.');
          consultar();
          reintentarSocket();
        };
        socket.onerror = () => {
          if (!activo) return;
          setAviso('Sin conexión en vivo; la vista se actualiza periódicamente.');
          socket.close();
        };
      } catch {
        setAviso('Sin conexión en vivo; la vista se actualiza periódicamente.');
        reintentarSocket();
      }
    };
    abrirSocket();
    return () => {
      activo = false;
      clearInterval(intervalo);
      clearTimeout(temporizadorSocket);
      socket?.close();
    };
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

  useEffect(() => {
    if (corriendo) return undefined;
    setAhora(Date.now());
    const intervalo = setInterval(() => setAhora(Date.now()), 1000);
    return () => clearInterval(intervalo);
  }, [corriendo]);

  const instanteVivoMs = useMemo(() => modoVivo
    ? relojAnclado.current(ejecucion.ritmo, ahora, desfase.current) : null, [modoVivo, ejecucion, ahora]);

  useEffect(() => {
    setFotogramas([]);
    cantidadFotogramas.current = 0;
    setIndiceFotograma(0);
    setReproduciendo(false);
    setReproduccionIniciada(false);
    setPasoMs(300);
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
          cantidadFotogramas.current = pagina.fotogramas.length;
          setIndiceFotograma(0);
        } else if (pagina.fotogramas.length) {
          setFotogramas(actual => [...actual, ...pagina.fotogramas]);
          cantidadFotogramas.current += pagina.fotogramas.length;
        }
        siguiente = pagina.siguiente;
        completo = pagina.completo && pagina.fotogramas.length < 100;
      } catch (fallo) {
        if (fallo.status === 404) completo = true;
        if (activo) setAviso(fallo.status === 404
          ? 'El servidor aún no ofrece reproducción; se muestra la última instantánea.'
          : 'Reproducción temporalmente no disponible; se muestra la última instantánea.');
      } finally { consultando = false; }
    };
    cargarFotogramas.current = { id, cargar };
    cargar();
    const intervalo = setInterval(cargar, 900);
    return () => {
      activo = false;
      clearInterval(intervalo);
      if (cargarFotogramas.current?.id === id) cargarFotogramas.current = null;
    };
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
    cargarPlanes.current = { id, cargar };
    cargar();
    const intervalo = setInterval(cargar, 900);
    return () => {
      activo = false;
      clearInterval(intervalo);
      if (cargarPlanes.current?.id === id) cargarPlanes.current = null;
    };
  }, [id]);

  useEffect(() => {
    if (!modoVivo) return;
    setReproduciendo(false);
    setIndiceFotograma(Math.max(0, fotogramas.length - 1));
  }, [modoVivo, fotogramas.length]);

  const haySecuencia = fotogramas.length >= 2;
  useEffect(() => {
    if (!reproduciendo || !haySecuencia || modoVivo) return undefined;
    const intervalo = setInterval(() => setIndiceFotograma(actual =>
      Math.min(actual + 1, cantidadFotogramas.current - 1)), pasoMs);
    return () => clearInterval(intervalo);
  }, [reproduciendo, haySecuencia, pasoMs, modoVivo]);

  useEffect(() => {
    if (ejecucion && esFinal(ejecucion.estado) && fotogramas.length > 0
        && indiceFotograma >= fotogramas.length - 1) setReproduciendo(false);
  }, [ejecucion?.estado, fotogramas.length, indiceFotograma]);

  const flotaParaAveria = fotogramas[0]?.vehiculos || ejecucion?.vehiculos || LISTA_VACIA;
  const instanteVivo = Number.isFinite(instanteVivoMs) ? aIso(instanteVivoMs) : null;
  const instanteReproducido = modoVivo ? instanteVivo || ejecucion.reloj
    : fotogramas[indiceFotograma]?.reloj || ejecucion?.reloj || null;
  // Las unidades del corte publicado que corresponde al instante mostrado: la misma fuente que
  // usa el mapa, para que el panel y la ficha del mapa no digan cifras distintas.
  const vehiculosDelInstante = useMemo(() => vehiculosObservadosEn(
    fotogramas, indiceFotograma, ejecucion, modoVivo, instanteReproducido, aMs),
  [fotogramas, indiceFotograma, ejecucion, modoVivo, instanteReproducido]);
  const averiasDelInstante = useMemo(() => averiasEn(averias, instanteReproducido),
    [averias, instanteReproducido]);
  const inventarioDelInstante = useMemo(() => ejecucion
    ? inventarioEn(fotogramas, inventario, instanteReproducido) : null,
  [ejecucion?.id, fotogramas, inventario, instanteReproducido]);
  const planReproducido = planVigenteEn(planes, instanteReproducido);
  const rutasReproducidas = modoVivo ? planReproducido?.rutas || ejecucion.rutas || LISTA_VACIA
    : planReproducido?.rutas || (!fotogramas.length ? ejecucion?.rutas : null) || LISTA_VACIA;
  const enInstanteActual = modoVivo || !fotogramas.length || indiceFotograma >= fotogramas.length - 1;
  const calculandoRutas = !!ejecucion && (ejecucion.estado === 'PENDIENTE'
    || (ejecucion.estado === 'EN_CURSO' && ejecucion.calculandoRutas && enInstanteActual));
  const rutasVisibles = calculandoRutas ? LISTA_VACIA : rutasReproducidas.map(ruta => ({
    ...ruta, paradas: ruta.paradas.filter(p => !p.fechaRegistro
      || aMs(p.fechaRegistro) <= aMs(instanteReproducido))
  })).filter(ruta => ruta.paradas.length > 0);
  const enviosDelInstante = useMemo(() => enviosEn(planes, instanteReproducido),
    [planes, instanteReproducido]);
  const rutasDelEnvio = useMemo(() => rutasDeEnvioEnMapa(
    enviosDelInstante, seleccionEnvio, rutasVisibles),
  [enviosDelInstante, seleccionEnvio, rutasVisibles]);

  // Primer plan dibujado y primera unidad en movimiento, contados desde que se pulsó «Iniciar».
  const hayRutasDibujadas = rutasVisibles.length > 0;
  const hayMovimiento = modoVivo && Number.isFinite(instanteVivoMs)
    && (ejecucion.trayectos || LISTA_VACIA).some(trayecto => {
      const pasos = trayecto.pasos || LISTA_VACIA;
      return pasos.length > 1 && aMs(pasos[0].instante) < instanteVivoMs
        && pasos.some(paso => paso.ubicacion.x !== pasos[0].ubicacion.x
          || paso.ubicacion.y !== pasos[0].ubicacion.y);
    });
  useEffect(() => {
    if (!medicionInicio || medicionInicio.id !== id) return;
    if (hayRutasDibujadas && medicionInicio.planMs === null) {
      setMedicionInicio(m => ({ ...m, planMs: performance.now() - m.t0 }));
    } else if (hayMovimiento && medicionInicio.movimientoMs === null) {
      setMedicionInicio(m => ({ ...m, movimientoMs: performance.now() - m.t0 }));
    }
  }, [medicionInicio, id, hayRutasDibujadas, hayMovimiento]);

  useEffect(() => { setSeleccionEnvio(null); setReporteAbierto(false); estadoPrevio.current = null; }, [id]);

  // Solo al presenciar el final: abrir una corrida que ya había terminado no interrumpe.
  useEffect(() => {
    const estado = ejecucion?.estado || null;
    const previo = estadoPrevio.current;
    estadoPrevio.current = estado;
    if ((previo === 'EN_CURSO' || previo === 'PAUSADA')
        && (estado === 'TERMINADA' || estado === 'COLAPSADA')) setReporteAbierto(true);
  }, [ejecucion?.estado]);

  // «En ruta» y «entregado» no existen en la API: se deducen de los hitos de los trayectos.
  const estadosPedido = useMemo(() => estadosDePedido(planes, aMs(instanteReproducido), aMs),
    [planes, instanteReproducido]);
  const pedidosDelInstante = useMemo(() => pedidosEnInstante(
    planes, aMs(instanteReproducido), aMs, estadosPedido),
  [planes, instanteReproducido, estadosPedido]);
  // Las cuatro situaciones usan la misma colección temporal que el mapa y la tabla.
  // Cada ID de fracción se conserva: no equivale al número de originales completos.
  const resumenPedidos = useMemo(() => {
    if (!ejecucion) return { entregados: null, enRuta: null, planificados: null, porPlanificar: null };
    let enRuta = 0, planificados = 0;
    for (const pedido of pedidosDelInstante) {
      if (pedido.situacion === 'EN_RUTA') enRuta += 1;
      else if (pedido.situacion === 'PLANIFICADO') planificados += 1;
    }
    return {
      entregados: pedidosDelInstante.filter(p => p.situacion === 'ENTREGADO').length,
      enRuta, planificados,
      porPlanificar: pedidosDelInstante.filter(p => p.situacion === 'NO_ASIGNADO').length
    };
  }, [ejecucion, pedidosDelInstante]);

  // Duración de reproducción de Simulación: ventanas publicadas más la ventana actual.
  const ritmo = ejecucion?.ritmo;
  const transcurridoMs = ritmo
    ? Math.max(0, ritmo.ventana - 1) * ritmo.saMs + transcurridoObjetivo(ritmo, ahora, desfase.current)
    : null;
  const tiempoRealMs = tiempoRealTranscurrido(ritmo, ahora, desfase.current);
  const progreso = ritmo?.ventanas ? Math.min(1, ritmo.ventana / ritmo.ventanas) : 0;

  function cambiarVista(destino) {
    restaurarVistaInicial.current = false;
    vistaActual.current = destino;
    if (ES_MAPA(destino) && destino !== vista) {
      setCorridasActivas([]);
      setCrearIndependiente(false);
      const conservar = ejecucion && vistaDeEscenario(ejecucion.escenario) === destino
        && (destino !== 'simulacion' || ejecucion.escenario === escenarioSimulacion.current);
      if (id && !conservar) {
        setEjecucion(null);
        setFotogramas([]);
        setPlanes([]);
        setInventario(null);
        setAverias([]);
        setUnidadSeleccionada(null);
        setAlmacenSeleccionado(null);
        setRutaSeleccionada(null);
        idActual.current = null;
        setId(null);
        guardarEjecucion(null);
        history.replaceState(null, '', location.pathname);
      }
    }
    setVista(destino);
    if (destino === 'operacion') setEscenario('OPERACION_DIARIA');
    else if (destino === 'simulacion') setEscenario(escenarioSimulacion.current);
    if (esPantallaAngosta()) setMenuAbierto(false);
  }

  function cambiarEscenario(valor) {
    restaurarVistaInicial.current = false;
    escenarioSimulacion.current = valor;
    try { localStorage.setItem('paqrap.escenario.simulacion', valor); } catch { /* sin almacenamiento */ }
    setEscenario(valor);
    setCorridasActivas([]);
    if (id && ejecucion?.escenario !== valor) {
      setEjecucion(null);
      setFotogramas([]);
      setPlanes([]);
      setInventario(null);
      setAverias([]);
      setUnidadSeleccionada(null);
      setAlmacenSeleccionado(null);
      setRutaSeleccionada(null);
      idActual.current = null;
      setId(null);
      guardarEjecucion(null);
      history.replaceState(null, '', location.pathname);
    }
  }

  function prepararNuevaCorrida() {
    setCrearIndependiente(true);
    if (!id) return;
    setEjecucion(null);
    setFotogramas([]);
    setPlanes([]);
    setInventario(null);
    setAverias([]);
    setUnidadSeleccionada(null);
    setAlmacenSeleccionado(null);
    setRutaSeleccionada(null);
    idActual.current = null;
    setId(null);
    guardarEjecucion(null);
    history.replaceState(null, '', location.pathname);
  }

  async function iniciar(evento) {
    evento.preventDefault();
    if (ocupado || consultandoCorridas || accionCorrida.current
        || (controlesDeCorrida(ejecucion?.estado).activa && !crearIndependiente)
        || (!id && corridasActivas.some(c => c.escenario === escenario) && !crearIndependiente)) return;
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
    const pulsado = performance.now();
    try {
      if (!crearIndependiente) {
        const activas = await api.activas(escenario);
        if (activas.length) {
          setCorridasActivas(activas);
          const unica = unicaCorridaActiva(activas, escenario);
          if (unica) {
            const recuperada = await api.consultar(unica.id);
            if (corridaRecuperable(recuperada, escenario)) {
              adoptarCorrida(recuperada);
            }
          }
          return;
        }
      }
      const nueva = await api.iniciar({
        escenario, algoritmo: ALGORITMO, inicio: `${diaIngresado}T${horaIngresada}:00`,
        ...(escenario === 'COLAPSO' ? { horizonteDias: Number(horizonte) } : {}),
        ...(escenario === 'PERIODO_5_DIAS' ? { duracionObjetivoMin: DURACION_5_DIAS_MIN } : {}),
        ...(escenario === 'OPERACION_DIARIA' ? { velocidad: velocidadDiaria } : {})
      });
      setMedicionInicio({ id: nueva.id, t0: pulsado, planMs: null, movimientoMs: null });
      const claveCorrida = claveDeEscenario(nueva.escenario);
      setCrearIndependiente(false);
      setCorridasActivas([]);
      ejecucionesPorVista.current[claveCorrida] = nueva.id;
      guardarEjecucionDeVista(claveCorrida, nueva.id);
      if (vistaActual.current === vistaDeEscenario(nueva.escenario)
          && (nueva.escenario === 'OPERACION_DIARIA'
            || nueva.escenario === escenarioSimulacion.current)) {
        setEjecucion(nueva);
        setRutaSeleccionada(null);
        guardarEjecucion(nueva.id);
        idActual.current = nueva.id;
        setId(nueva.id);
        history.replaceState(null, '', `?ejecucion=${encodeURIComponent(nueva.id)}`);
      }
    } catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  function adoptarCorrida(recuperada) {
      setError('');
      setCorridasActivas([]);
      const destino = vistaDeEscenario(recuperada.escenario);
      const clave = claveDeEscenario(recuperada.escenario);
      ejecucionesPorVista.current[clave] = recuperada.id;
      guardarEjecucionDeVista(clave, recuperada.id);
      guardarEjecucion(recuperada.id);
      setEjecucion(recuperada);
      setFotogramas([]);
      setPlanes([]);
      setInventario(null);
      setAverias([]);
      setRutaSeleccionada(null);
      setUnidadSeleccionada(null);
      setAlmacenSeleccionado(null);
      setEscenario(recuperada.escenario);
      if (recuperada.escenario !== 'OPERACION_DIARIA') {
        escenarioSimulacion.current = recuperada.escenario;
        try { localStorage.setItem('paqrap.escenario.simulacion', recuperada.escenario); } catch { /* sin almacenamiento */ }
      }
      vistaActual.current = destino;
      setVista(destino);
      idActual.current = recuperada.id;
      setId(recuperada.id);
      setCrearIndependiente(false);
      history.replaceState(null, '', `?ejecucion=${encodeURIComponent(recuperada.id)}`);
  }

  async function elegirCorrida(uuid) {
    if (ocupado || !corridasActivas.some(corrida => corrida.id === uuid)) return;
    setOcupado(true);
    setError('');
    try {
      const recuperada = await api.consultar(uuid);
      if (!esActiva(recuperada)) {
        setCorridasActivas(actuales => actuales.filter(corrida => corrida.id !== uuid));
        setError('Esta corrida ya no está activa. Elige otra corrida disponible.');
        return;
      }
      adoptarCorrida(recuperada);
    } catch (e) { setError(`No se pudo recuperar la corrida: ${e.message}`); }
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

  async function controlarCorrida(accion) {
    const corridaId = idActual.current;
    if (!corridaId || ocupado || accionCorrida.current
      || !controlesDeCorrida(ejecucion?.estado)[accion]) return;
    accionCorrida.current = true;
    setOcupado(true);
    setError('');
    try {
      const resultado = await ({ pausar: api.pausar, reanudar: api.reanudar, cancelar: api.cancelar })[accion](corridaId);
      if (idActual.current !== corridaId) return;
      setEjecucion(resultado);
      if (accion === 'reanudar' && resultado.estado === 'EN_CURSO') setEnVivo(true);
      setAviso(resultado.estado === 'PAUSADA' ? 'Corrida pausada para todos los dispositivos.'
        : resultado.estado === 'CANCELADA' ? 'Corrida detenida. Puedes iniciar una nueva.'
          : resultado.estado === 'EN_CURSO' ? 'Corrida reanudada.' : 'La corrida ya finalizó.');
    } catch (e) {
      if (idActual.current === corridaId) setError(e.message);
    } finally { accionCorrida.current = false; setOcupado(false); }
  }

  // La franja superior solo reparte los pedidos entre sus cuatro situaciones; el resto de
  // cifras de la corrida vive en el riel y en el mapa.
  const numeroMetrica = valor => numero(valor ?? 0);
  const chips = ES_MAPA(vista) ? [
    { etiqueta: 'ENTREGADOS', valor: numeroMetrica(resumenPedidos.entregados),
      titulo: 'Pedidos/fracciones conocidos con entrega observada hasta el reloj visible.' },
    { etiqueta: 'EN RUTA', valor: numeroMetrica(resumenPedidos.enRuta),
      titulo: 'Su unidad ya salió y el pedido sigue pendiente. Deducido de los hitos del simulador.' },
    { etiqueta: 'PLANIFICADOS', valor: numeroMetrica(resumenPedidos.planificados),
      titulo: 'Con ruta en el plan vigente, pero su unidad todavía no sale.' },
    { etiqueta: 'POR PLANIFICAR', valor: numeroMetrica(resumenPedidos.porPlanificar),
      alerta: !!resumenPedidos.porPlanificar,
      titulo: 'Pedidos/fracciones conocidos sin ruta en el instante visible.' }
  ] : [];

  const meta = vista === 'simulacion' ? [
    { etiqueta: 'Fecha y hora de simulación', valor: fechaConSegundos(instanteReproducido) },
    { etiqueta: 'Duración', valor: duracion(transcurridoMs ?? 0) }
  ] : vista === 'operacion' ? [
    { etiqueta: modoVivo ? 'En vivo' : 'Reloj', valor: fecha(instanteReproducido) }
  ] : [];

  const configuracion = {
    escenario, setEscenario: cambiarEscenario, detalle, limites,
    dia, setDia, hora, setHora, horizonte, setHorizonte,
    velocidadDiaria,
    setVelocidadDiaria: valor => {
      setVelocidadDiaria(valor);
      try { localStorage.setItem('paqrap.velocidad.diaria', String(valor)); } catch { /* sin almacenamiento */ }
    },
    ahoraDisponible: periodos.includes(periodoDe(ahoraLocal().dia)),
    alUsarAhora: () => { const actual = ahoraLocal(); setDia(actual.dia); setHora(actual.hora); },
    unidadAveriada, setUnidadAveriada, tipoAveria, setTipoAveria,
    flota: flotaParaAveria, alIniciar: iniciar, alElegirCorrida: elegirCorrida,
    corridasActivas, consultandoCorridas, crearIndependiente,
    alPrepararNueva: prepararNuevaCorrida,
    alCancelarNueva: () => setCrearIndependiente(false),
    alRegistrarAveria: registrarAveria,
    alControlar: controlarCorrida
  };

  const enMapa = ES_MAPA(vista);

  return <div className={`app ${menuAbierto ? '' : 'mini'} ${enMapa ? 'mapview' : ''} ${vista === 'operacion' ? 'vista-operacion' : ''} ${enMapa && rielAbierto ? 'con-riel' : ''}`}>
    <BarraSuperior titulo={TITULOS[vista]} menuAbierto={menuAbierto} ejecucion={ejecucion}
      alAlternarMenu={() => setMenuAbierto(!menuAbierto)} chips={chips} meta={meta}
      modoOperacion={enMapa} />

    <BarraLateral vista={vista} alCambiar={cambiarVista} fuente={fuente} />
    {menuAbierto && <div className="scrim" onClick={() => setMenuAbierto(false)} />}

    {error && <div role="alert" className="mensaje-error">
      <b>Atención</b><span>{error}</span>
      <button type="button" onClick={() => setError('')} aria-label="Cerrar mensaje">×</button>
    </div>}

    {enMapa && <>
      <section className="mapsec">
        <Mapa key={id || 'sin-ejecucion'} ejecucion={ejecucion} rutas={rutasVisibles}
          calculandoRutas={calculandoRutas} rutaSeleccionada={rutaSeleccionada}
          rutasEnvioSeleccionadas={rutasDelEnvio.rutas} mapaDatos={mapaDatos}
          inventario={inventarioDelInstante} fotogramas={fotogramas} indiceFotograma={indiceFotograma}
          reproduciendo={reproduciendo} pasoMs={pasoMs}
          velocidadHabilitada={reproduccionIniciada && !modoVivo}
          estadosPedido={estadosPedido}
          pedidosDelInstante={pedidosDelInstante}
          modoOperacion={vista === 'operacion'} transcurridoMs={tiempoRealMs}
          ahoraReal={ahora} stockDelInstante={!!inventarioDelInstante} averias={averias}
          unidadSeleccionada={unidadSeleccionada} alSeleccionarUnidad={setUnidadSeleccionada}
          almacenSeleccionado={almacenSeleccionado} alSeleccionarAlmacen={setAlmacenSeleccionado}
          umbrales={umbrales} enVivo={modoVivo} trayectosEnVivo={modoVivo ? ejecucion.trayectos || LISTA_VACIA : null}
          trayectosDelInstante={planReproducido?.trayectos || LISTA_VACIA}
          alVolverEnVivo={corriendo && !enVivo ? () => setEnVivo(true) : null}
          noAsignadosDelInstante={planReproducido?.noAsignados || null}
          alCambiarFotograma={indice => { setEnVivo(false); setReproduciendo(false); setIndiceFotograma(indice); }}
          alReproducir={() => {
            if (!reproduciendo && indiceFotograma >= fotogramas.length - 1) setIndiceFotograma(0);
            setReproduccionIniciada(true);
            setReproduciendo(!reproduciendo);
          }} alCambiarPaso={setPasoMs}
          instante={instanteReproducido || (dia ? `${dia}T${hora}:00` : null)} />
        {seleccionEnvio && <div className="envio-en-mapa" role="status">
          <span><b>{seleccionEnvio.tipo === 'original' ? 'Envío' : 'Fracción'} {seleccionEnvio.id}</b>
            {rutasDelEnvio.rutas.length
              ? ` · ${rutasDelEnvio.rutas.length} ruta(s) resaltada(s): ${rutasDelEnvio.rutas.join(', ')}`
              : ' · sin ruta visible en este instante'}
            {rutasDelEnvio.sinRutaVisible.length > 0 && rutasDelEnvio.rutas.length > 0
              ? ` · ${rutasDelEnvio.sinRutaVisible.length} fracción(es) sin ruta visible` : ''}</span>
          <button type="button" onClick={() => setSeleccionEnvio(null)}>Quitar resaltado</button>
        </div>}
      </section>

      <Riel titulo={vista === 'simulacion' ? 'Simulación' : 'Operación'} abierto={rielAbierto}
        alAlternar={() => setRielAbierto(!rielAbierto)}>
        <RielCorrida vista={vista} cfg={configuracion} ejecucion={ejecucion} corriendo={corriendo}
          ocupado={ocupado} rutas={rutasVisibles} calculandoRutas={calculandoRutas}
          rutaSeleccionada={rutaSeleccionada} alSeleccionarRuta={rutaId => {
            setSeleccionEnvio(null);
            setRutaSeleccionada(rutaId);
          }}
          seleccionEnvio={seleccionEnvio} rutasDelEnvio={rutasDelEnvio}
          alSeleccionarEnvio={seleccion => {
            setRutaSeleccionada(null);
            setSeleccionEnvio(seleccion);
          }}
          inventario={inventarioDelInstante} mapaDatos={mapaDatos} averias={averias} aviso={aviso}
          progreso={progreso} vehiculos={vehiculosDelInstante} planes={planes}
          averiasDelInstante={averiasDelInstante} instante={instanteReproducido}
          trayectos={modoVivo ? ejecucion?.trayectos || LISTA_VACIA
            : planReproducido?.trayectos || ejecucion?.trayectos || LISTA_VACIA}
          unidadSeleccionada={unidadSeleccionada} alSeleccionarUnidad={setUnidadSeleccionada}
          almacenSeleccionado={almacenSeleccionado} alSeleccionarAlmacen={setAlmacenSeleccionado}
          umbrales={umbrales} estadosPedido={estadosPedido}
          alGuardarUmbrales={valores => setUmbrales(guardarUmbrales(valores))}
          alRestablecerUmbrales={() => setUmbrales(olvidarUmbrales())}
          alVerReporte={() => setReporteAbierto(true)}
          medicionInicio={medicionInicio?.id === id ? medicionInicio : null} />
      </Riel>
      {reporteAbierto && ejecucion && <ReportePlan ejecucion={ejecucion} planes={planes}
        alCerrar={() => setReporteAbierto(false)} />}
    </>}

    {vista === 'pedidos' && <div className="sheet sheet-pedidos">
      <HojaPedidos ejecucion={ejecucion} estadosPedido={estadosPedido} planes={planes}
        instante={instanteReproducido}
        pedidosDelInstante={pedidosDelInstante}
        periodo={periodo} mapaDatos={mapaDatos} fuente={fuente}
        alActualizarDatos={() => setVersionDatos(v => v + 1)}
        alVerEnMapa={seleccion => {
          setRutaSeleccionada(null);
          setSeleccionEnvio(seleccion);
          if (ejecucion) cambiarVista(vistaDeEscenario(ejecucion.escenario));
        }} />
    </div>}
    {vista === 'averias' && <div className="sheet sheet-averias">
      <HojaAverias ejecucion={ejecucion} averias={averias} eventosOperacion={eventosOperacion} />
    </div>}
    {vista === 'flota' && <div className="sheet sheet-flota">
      <HojaFlota almacenes={mapaDatos?.almacenes || LISTA_VACIA} />
    </div>}
  </div>;
}

/** Fecha y hora del dispositivo en el formato de los campos del formulario. */
function ahoraLocal() {
  const d = new Date();
  const dos = n => String(n).padStart(2, '0');
  return { dia: `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`,
    hora: `${dos(d.getHours())}:${dos(d.getMinutes())}` };
}

function esPantallaAngosta() {
  return typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches;
}
