import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ANCHO, ALTO, xEnMapa, yEnMapa, fotogramaObservadoEn, nodosBloqueados,
  prepararTrayectos, posicionEnTrayecto, posicionContinuaEnTrayecto, cargaEnTrayecto, trazosConRecorrido,
  segmentosEnInstante, rutaFinalizadaEn, orientacionEnTrayecto, orientacionEnFotogramas,
  giroMasCorto } from '../mapa.js';
import { aMs } from '../reloj.js';
import { bloqueosEn } from '../bloqueos.js';
import { ocupacionDeAlmacen, COLORES_OCUPACION, ETIQUETAS_OCUPACION, NIVELES_OCUPACION } from '../ocupacion.js';
import { esFinal } from '../services/api.js';
import { fecha, numero, duracion, colorDeRuta, textoRitmo, estadoVehiculo } from '../formato.js';
import DibujoVehiculo, { IconoLeyenda } from './DibujoVehiculo.jsx';
import FondoUrbano from './FondoUrbano.jsx';
import IconoUI from './IconoUI.jsx';
import PanelFlotante from './PanelFlotante.jsx';
import { estadoDeStock, ETIQUETAS_STOCK, NIVELES_STOCK } from '../stock.js';
import { estadoDeCarga as nivelDeCarga, estadoDeGrupo as nivelDeGrupo, etiquetasDeCarga,
  NIVELES_CARGA } from '../carga.js';
import { UMBRALES_POR_OMISION } from '../umbrales.js';
import { averiasEn, averiaDeGrupo, ETIQUETAS_AVERIA } from '../averias.js';
import { ocupacionDeFlota, ocupacionDeAlmacenes } from '../indicadores.js';

const LISTA_VACIA = [];

// La retícula de 70 × 50 km se dibuja a 10 unidades por km.
const LIENZO_ANCHO = ANCHO * 10;
const LIENZO_ALTO = ALTO * 10;
const ZOOM_MIN = 1;
const ZOOM_MAX = 8;
const DURACION_ZOOM_MS = 250;
const VISTA_INICIAL = { cx: LIENZO_ANCHO / 2, cy: LIENZO_ALTO / 2, z: 1 };

const TIPOS_FLOTA = [['AUTO', 'Auto'], ['MOTO', 'Moto'], ['BICICLETA', 'Bicicleta'], ['CAMION', 'Camión']];
const TIPOS_CON_GIRO = new Set(['AUTO', 'MOTO', 'BICICLETA']);
const TIPOS_CENITALES = new Set(['AUTO', 'MOTO', 'BICICLETA']);
/**
 * Medidas del ícono de cada unidad, en px de pantalla antes de `escalaIcono`: lado del dibujo, radio
 * del resalte y posición de la luz de carga y del triángulo de avería. Son relativas al centro del
 * ícono, que es donde gira; cambiar el lado no altera ni la posición ni la orientación.
 */
const ICONO_CENITAL = { lado: 30, halo: 17, carga: { x: 8, y: -12 }, averia: { x: -8, y: -12 } };
const ICONO_CIRCULAR = { lado: 26, halo: 18, carga: { x: 9, y: -9 }, averia: { x: -10, y: -10 } };
/**
 * Bloqueos: puntos pequeños y de tono claro sobre cada nodo cerrado, para que se lean como una línea
 * punteada que contrasta con las vías sin ocultar la cuadrícula. El color coincide con la leyenda.
 */
const BLOQUEO = { lado: 4, color: '#e8806f', opacidad: .75 };
/** Medidas del ícono de almacén (centrado en su nodo) y de su resalte; mismas reglas que ICONO_*. */
const ICONO_ALMACEN = { CENTRAL: { ancho: 30, alto: 33 }, INTERMEDIO: { ancho: 26, alto: 26 }, halo: 17 };
/** Posición con la que se dibuja la unidad: deslizante en vivo, el nodo publicado en los demás casos. */
const posicionDibujada = vehiculo => vehiculo.ubicacionVisual || vehiculo.ubicacion;
const medidasDeIcono = tipo => TIPOS_CENITALES.has(tipo) ? ICONO_CENITAL : ICONO_CIRCULAR;
const ESTADOS_PEDIDO = [['PLANIFICADO', 'Planificados'], ['EN_RUTA', 'En ruta'],
  ['ENTREGADO', 'Entregados'], ['NO_ASIGNADO', 'Sin asignar']];

const limitar = (valor, minimo, maximo) => Math.max(minimo, Math.min(maximo, valor));

/** Encuadre centrado en (cx, cy) con el zoom z, siempre dentro de la retícula. */
function encuadre({ cx, cy, z }) {
  const ancho = LIENZO_ANCHO / z;
  const alto = LIENZO_ALTO / z;
  const centroX = limitar(cx, ancho / 2, LIENZO_ANCHO - ancho / 2);
  const centroY = limitar(cy, alto / 2, LIENZO_ALTO - alto / 2);
  return { ancho, alto, cx: centroX, cy: centroY,
    x: centroX - ancho / 2, y: centroY - alto / 2 };
}

function IndicadoresMapa({ flotaGlobal, almacenesGlobal, etiquetasCarga }) {
  return <>
    <div className="mapa-indicador">
      <span className="mapa-indicador-titulo">Flota</span>
      <b>{flotaGlobal.porcentaje === null ? '—' : `${flotaGlobal.porcentaje} %`}</b>
      <span className={`mapa-indicador-luz mapa-carga-${flotaGlobal.estado}`}
        title={etiquetasCarga[flotaGlobal.estado]} />
      <small>{flotaGlobal.porcentaje === null ? 'Sin datos del instante'
        : `${numero(flotaGlobal.carga)} / ${numero(flotaGlobal.capacidad)} en ${numero(flotaGlobal.unidades)} unidad(es)`}</small>
    </div>
    <div className="mapa-indicador">
      <span className="mapa-indicador-titulo">Almacenes</span>
      <b>{almacenesGlobal.porcentaje === null ? '—' : `${almacenesGlobal.porcentaje} %`}</b>
      <span className={`mapa-indicador-luz mapa-stock-${almacenesGlobal.estado}`}
        title={ETIQUETAS_STOCK[almacenesGlobal.estado]} />
      <small>{almacenesGlobal.porcentaje === null ? 'Sin corte de inventario'
        : `${numero(almacenesGlobal.carga)} / ${numero(almacenesGlobal.capacidad)} disponibles${almacenesGlobal.ilimitados ? ' · central ilimitado' : ''}`}</small>
    </div>
  </>;
}

export default function Mapa({ ejecucion, rutas, calculandoRutas, rutaSeleccionada,
  rutasEnvioSeleccionadas = LISTA_VACIA, mapaDatos,
  inventario, instante, fotogramas, indiceFotograma, alCambiarFotograma, reproduciendo,
  alReproducir, pasoMs, alCambiarPaso, velocidadHabilitada = false,
  enVivo = false, trayectosEnVivo = null,
  trayectosDelInstante = null,
  alVolverEnVivo = null, noAsignadosDelInstante = null, estadosPedido = null,
  modoOperacion = false, transcurridoMs = null, stockDelInstante = false,
  pedidosDelInstante = [], averias = LISTA_VACIA,
  unidadSeleccionada = null, alSeleccionarUnidad = null,
  almacenSeleccionado = null, alSeleccionarAlmacen = null,
  umbrales = UMBRALES_POR_OMISION, indicadoresPedidos = LISTA_VACIA, mostrarIndicadores = true,
  ranuraControles = null }) {
  // Los cortes del semáforo salen de la configuración del sistema (B06).
  const estadoDeCarga = vehiculo => nivelDeCarga(vehiculo, umbrales.carga);
  const estadoDeGrupo = grupo => nivelDeGrupo(grupo, umbrales.carga);
  const estadoDeStockCon = (stock, inicial) => estadoDeStock(stock, inicial, umbrales.stock);
  const ocupacionCon = (stock, inicial) => ocupacionDeAlmacen(stock, inicial, umbrales.stock);
  const ETIQUETAS_CARGA = etiquetasDeCarga(umbrales.carga);
  const [rutaEnFoco, setRutaEnFoco] = useState(null);
  const rutasDelEnvio = new Set(rutasEnvioSeleccionadas);
  const [vista, setVista] = useState(VISTA_INICIAL);
  const [tamanoLienzo, setTamanoLienzo] = useState({ ancho: 0, alto: 0 });
  const [arrastrando, setArrastrando] = useState(false);
  const [filtros, setFiltros] = useState(false);
  const [lugarFiltros, setLugarFiltros] = useState(null);
  const botonFiltros = useRef(null);
  const [flota, setFlota] = useState('');
  const [estadoPedido, setEstadoPedido] = useState('');
  const [nivelCarga, setNivelCarga] = useState('');
  const [nivelStock, setNivelStock] = useState('');
  const [reproductorAbierto, setReproductorAbierto] = useState(false);
  const reproductorId = useId();
  const svgRef = useRef(null);
  const lienzoRef = useRef(null);
  const tipRef = useRef(null);
  const [tip, setTip] = useState(null);
  const arrastre = useRef(null);
  const animacionZoom = useRef(null);
  const zoomObjetivo = useRef(null);
  const animacionGiro = useRef(null);
  const iconosVehiculos = useRef(new Map());
  const angulosMostrados = useRef(new Map());
  const vistaActual = useRef(vista);
  vistaActual.current = vista;

  useEffect(() => { setReproductorAbierto(false); }, [modoOperacion]);

  useEffect(() => {
    const lienzo = lienzoRef.current;
    if (!lienzo) return undefined;
    const medir = () => {
      const { width, height } = lienzo.getBoundingClientRect();
      setTamanoLienzo({ ancho: width, alto: height });
    };
    const observador = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(medir);
    observador?.observe(lienzo);
    window.addEventListener('resize', medir);
    medir();
    return () => { observador?.disconnect(); window.removeEventListener('resize', medir); };
  }, []);

  // El panel de filtros cuelga del botón de la cabecera, sin salirse del mapa.
  function alternarFiltros() {
    if (!filtros) {
      const boton = botonFiltros.current?.getBoundingClientRect();
      const lienzo = lienzoRef.current?.getBoundingClientRect();
      setLugarFiltros(boton && lienzo ? {
        left: limitar(boton.left - lienzo.left, 8, Math.max(8, lienzo.width - 286)),
        top: Math.max(8, boton.bottom - lienzo.top + 6), right: 'auto'
      } : null);
    }
    setFiltros(!filtros);
  }

  const marco = encuadre(vista);
  // Extender solo la retícula decorativa hasta los bordes sin estirar sus cuadrados.
  const escalaFondo = tamanoLienzo.ancho && tamanoLienzo.alto
    ? Math.min(tamanoLienzo.ancho / marco.ancho, tamanoLienzo.alto / marco.alto) : 1;
  const anchoFondo = tamanoLienzo.ancho ? tamanoLienzo.ancho / escalaFondo : marco.ancho;
  const altoFondo = tamanoLienzo.alto ? tamanoLienzo.alto / escalaFondo : marco.alto;

  // Píxeles de pantalla por unidad del lienzo. Con preserveAspectRatio por omisión el
  // contenido se centra y sobra margen en el lado largo, así que manda el lado que ajusta.
  function escalaEnPantalla() {
    const caja = svgRef.current?.getBoundingClientRect();
    if (!caja?.width) return null;
    const actual = encuadre(vistaActual.current);
    return { caja, px: Math.min(caja.width / actual.ancho, caja.height / actual.alto), actual };
  }

  function cancelarZoom(limpiarObjetivo = true) {
    if (animacionZoom.current !== null) cancelAnimationFrame(animacionZoom.current);
    animacionZoom.current = null;
    if (limpiarObjetivo) zoomObjetivo.current = null;
  }

  function aplicarVista(siguiente) {
    vistaActual.current = siguiente;
    setVista(siguiente);
  }

  function animarVista(destino, hacia = null) {
    cancelarZoom(false);
    const actual = vistaActual.current;
    zoomObjetivo.current = destino;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      aplicarVista(destino);
      zoomObjetivo.current = null;
      return;
    }
    const inicio = performance.now();
    const paso = ahora => {
      const avance = limitar((ahora - inicio) / DURACION_ZOOM_MS, 0, 1);
      const suave = avance * avance * (3 - 2 * avance);
      const z = actual.z + (destino.z - actual.z) * suave;
      const centro = encuadre({
        z,
        cx: hacia ? hacia.x - (hacia.x - actual.cx) * actual.z / z
          : actual.cx + (destino.cx - actual.cx) * suave,
        cy: hacia ? hacia.y - (hacia.y - actual.cy) * actual.z / z
          : actual.cy + (destino.cy - actual.cy) * suave
      });
      aplicarVista({ z, cx: centro.cx, cy: centro.cy });
      if (avance < 1) animacionZoom.current = requestAnimationFrame(paso);
      else { animacionZoom.current = null; zoomObjetivo.current = null; }
    };
    animacionZoom.current = requestAnimationFrame(paso);
  }

  function acercar(factor, hacia = null) {
    const actual = vistaActual.current;
    const z = limitar((zoomObjetivo.current?.z ?? actual.z) * factor, ZOOM_MIN, ZOOM_MAX);
    if (z === actual.z && !animacionZoom.current) return;
    const destino = encuadre(hacia ? {
      z, cx: hacia.x - (hacia.x - actual.cx) * actual.z / z,
      cy: hacia.y - (hacia.y - actual.cy) * actual.z / z
    } : { ...actual, z });
    animarVista({ z, cx: destino.cx, cy: destino.cy }, hacia);
  }

  useEffect(() => () => cancelarZoom(), []);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return undefined;
    // La rueda se escucha a mano: React la registra como pasiva y no dejaría frenar el scroll.
    const rodar = evento => {
      evento.preventDefault();
      const medida = escalaEnPantalla();
      if (!medida) return;
      const { caja, px, actual } = medida;
      const hacia = {
        x: actual.cx + (evento.clientX - (caja.left + caja.width / 2)) / px,
        y: actual.cy + (evento.clientY - (caja.top + caja.height / 2)) / px
      };
      acercar(evento.deltaY < 0 ? 1.2 : 1 / 1.2, hacia);
    };
    svg.addEventListener('wheel', rodar, { passive: false });
    return () => svg.removeEventListener('wheel', rodar);
  }, []);

  function empezarArrastre(evento) {
    cancelarZoom();
    const medida = escalaEnPantalla();
    if (!medida) return;
    arrastre.current = { clientX: evento.clientX, clientY: evento.clientY,
      cx: medida.actual.cx, cy: medida.actual.cy, px: medida.px };
    setArrastrando(true);
    evento.currentTarget.setPointerCapture?.(evento.pointerId);
  }

  function mover(evento) {
    const inicio = arrastre.current;
    if (!inicio) return;
    aplicarVista({ ...vistaActual.current,
      cx: inicio.cx - (evento.clientX - inicio.clientX) / inicio.px,
      cy: inicio.cy - (evento.clientY - inicio.clientY) / inicio.px });
  }

  function soltar(evento) {
    arrastre.current = null;
    setArrastrando(false);
    evento.currentTarget.releasePointerCapture?.(evento.pointerId);
  }

  const fotograma = fotogramas[indiceFotograma];
  const fotoObservada = fotogramaObservadoEn(
    fotogramas, indiceFotograma, ejecucion, enVivo, instante, aMs);
  const posicionesObservadas = fotoObservada?.vehiculos ?? LISTA_VACIA;
  // Los sin asignar del plan vigente en el instante mostrado; sin historial, los más recientes.
  const noAsignados = noAsignadosDelInstante || ejecucion?.noAsignados || LISTA_VACIA;
  const almacenes = mapaDatos?.almacenes || [];
  const stockPorId = new Map((inventario?.almacenes || []).map(a => [a.id, a]));
  const stockDe = almacen => !ejecucion
    ? almacen.tipo === 'CENTRAL' ? { ilimitado: true } : { disponible: almacen.stockInicial }
    : stockDelInstante ? stockPorId.get(almacen.id) : null;
  const bloqueosActivos = bloqueosEn(mapaDatos?.bloqueos, instante);
  const claveBloqueos = bloqueosActivos.map(b => `${b.inicio}/${b.fin}`).join('|');
  const bloqueados = useMemo(() => nodosBloqueados(bloqueosActivos), [mapaDatos, claveBloqueos]);
  // En vivo cada unidad sigue los hitos que el simulador informó para la ventana; las que no
  // tienen recorrido se quedaron quietas y se muestran donde las deja el corte.
  const trayectosPreparados = useMemo(() => trayectosEnVivo
    ? prepararTrayectos(trayectosEnVivo, aMs, bloqueados) : null, [trayectosEnVivo, bloqueados]);
  const trayectosParaGiro = useMemo(() => prepararTrayectos(
    enVivo ? trayectosEnVivo : trayectosDelInstante, aMs, bloqueados),
  [enVivo, trayectosEnVivo, trayectosDelInstante, bloqueados]);
  const instanteMs = instante ? aMs(instante) : NaN;
  const enCortePublicado = enVivo && (ejecucion?.reloj === instante
    || fotogramas.some(corte => corte.reloj === instante));
  const vehiculosTodos = trayectosPreparados && !enCortePublicado ? posicionesObservadas.map(vehiculo => {
    const preparado = trayectosPreparados.get(vehiculo.id);
    const ubicacion = posicionEnTrayecto(preparado, instanteMs);
    // `ubicacion` es el nodo (textos y filtros); `ubicacionVisual` desliza el ícono entre nodos.
    return { ...vehiculo, ubicacion: ubicacion || vehiculo.ubicacion,
      ubicacionVisual: posicionContinuaEnTrayecto(preparado, instanteMs) || vehiculo.ubicacion,
      cargaAbordo: cargaEnTrayecto(preparado, instanteMs, vehiculo.cargaAbordo) };
  }) : posicionesObservadas;

  const trazosTodos = useMemo(() => trazosConRecorrido(rutas, trayectosParaGiro, bloqueados),
    [rutas, bloqueados, trayectosParaGiro]);

  // ── filtros del mapa ───────────────────────────────────────────────────────────────
  const estadoDe = pedido => estadosPedido?.get(pedido.id) || 'PLANIFICADO';
  const enFlota = tipo => !flota || tipo === flota;
  const enEstado = valor => !estadoPedido || valor === estadoPedido;
  // El filtro de semáforo (D06) se aplica a la unidad; una ruta se oculta con su unidad para no
  // dejar trazos huérfanos en el mapa, igual que ya ocurre al filtrar por tipo de flota.
  const enNivelCarga = vehiculo => !nivelCarga || estadoDeCarga(vehiculo) === nivelCarga;
  const enNivelCargaDeRuta = ruta => !nivelCarga
    || enNivelCarga(vehiculosTodos.find(v => v.id === ruta.vehiculoId));
  const enNivelStock = almacen => !nivelStock
    || estadoDeStockCon(stockDe(almacen), almacen.stockInicial) === nivelStock;

  const vehiculos = vehiculosTodos.filter(v => enFlota(v.tipo) && enNivelCarga(v));
  const trazos = trazosTodos.filter(({ ruta }) => rutasDelEnvio.has(ruta.id)
    || (estadoPedido !== 'NO_ASIGNADO' && enFlota(ruta.tipoVehiculo)
      && enNivelCargaDeRuta(ruta)));
  const segmentosDibujados = trazos.flatMap(({ ruta, detalles }) => {
    const terminada = rutaFinalizadaEn(ruta, trayectosParaGiro.get(ruta.vehiculoId), instanteMs);
    return detalles.flatMap((detalle, indice) => segmentosEnInstante(detalle, instanteMs)
      .map((segmento, parte) => ({ ...segmento, ruta, terminada,
        clave: `${ruta.id}-${indice}-${parte}` })));
  }).sort((a, b) => Number(b.recorrido) - Number(a.recorrido));
  const puntos = rutas.filter(ruta => enFlota(ruta.tipoVehiculo) && enNivelCargaDeRuta(ruta))
    .flatMap(ruta => ruta.paradas.map((parada, indice) => ({
      ruta, parada, estado: estadoDe(parada), orden: indice + 1,
      ultima: indice === ruta.paradas.length - 1
    })))
    .filter(punto => enEstado(punto.estado));
  const idsDelPlan = new Set(rutas.flatMap(r => r.paradas.map(p => p.id)));
  // Los cortes pueden omitir pedidos todavía activos de planes anteriores. Conservar
  // sus destinos, sin reconstruir ni afirmar que su ruta anterior continúa vigente.
  const adicionales = pedidosDelInstante.filter(p => !idsDelPlan.has(p.id)
    && p.situacion !== 'NO_ASIGNADO' && p.destino
    && enFlota(p.tipoVehiculo) && enEstado(p.situacion)
    && (p.situacion !== 'ENTREGADO' || estadoPedido === 'ENTREGADO'));
  const coincidentes = new Map();
  for (const pedido of [...puntos.map(p => p.parada), ...adicionales]) {
    const clave = `${pedido.destino.x},${pedido.destino.y}`;
    if (!coincidentes.has(clave)) coincidentes.set(clave, new Map());
    coincidentes.get(clave).set(pedido.id, pedido);
  }
  const sinAsignar = enEstado('NO_ASIGNADO') && !flota
    ? pedidosDelInstante.length ? pedidosDelInstante.filter(p => p.situacion === 'NO_ASIGNADO') : noAsignados
    : LISTA_VACIA;
  const almacenesVisibles = almacenes.filter(enNivelStock);
  const hayFiltro = !!flota || !!estadoPedido || !!nivelCarga || !!nivelStock;

  // Averías del instante mostrado: el tipo lo da el registro y la permanencia, el caso.
  const averiasVigentes = useMemo(() => averiasEn(averias, instante), [averias, instante]);
  const inicialPorAlmacen = useMemo(() => new Map(almacenes
    .filter(a => a.stockInicial > 0).map(a => [a.id, a.stockInicial])), [mapaDatos]);
  const flotaGlobal = ocupacionDeFlota(vehiculosTodos, umbrales.carga);
  // Con una corrida en marcha y sin corte publicado para el instante no se estima la ocupación:
  // el stock inicial declararía almacenes llenos que nadie ha medido. El caso sin corrida sí lo
  // usa, igual que los marcadores del mapa, porque ahí el inicial es el dato del caso.
  const almacenesGlobal = ocupacionDeAlmacenes(
    stockDelInstante ? inventario?.almacenes
      : ejecucion ? null : almacenes.map(almacen => ({
        id: almacen.id, disponible: almacen.stockInicial, ilimitado: almacen.tipo === 'CENTRAL' })),
    inicialPorAlmacen, umbrales.stock);

  const finalizada = ejecucion && esFinal(ejecucion.estado);
  const hayReproduccion = fotogramas.length > 0 || enVivo;
  const contadorOperacion = `Reproducción ${fotogramas.length ? (enVivo ? fotogramas.length : indiceFotograma + 1) : 0}/${fotogramas.length}`;
  const gruposVehiculos = [...vehiculos.reduce((grupos, vehiculo) => {
    if (!vehiculo.ubicacion) return grupos;
    // Se agrupa por lugar dibujado: las unidades detenidas en un nodo se juntan, las que van por
    // la calle (aunque pasen por el mismo nodo) se ven cada una en su sitio.
    const { x, y } = posicionDibujada(vehiculo);
    const clave = `${Math.round(x * 100)},${Math.round(y * 100)}`;
    if (!grupos.has(clave)) grupos.set(clave, []);
    grupos.get(clave).push(vehiculo);
    return grupos;
  }, new Map()).values()];
  const historialOrientacion = fotoObservada && !fotogramas.some(f => f.reloj === fotoObservada.reloj)
    ? [...fotogramas, fotoObservada] : fotogramas;
  const indiceOrientacion = historialOrientacion.findLastIndex(f => f.reloj === fotoObservada?.reloj);
  const objetivosGiro = gruposVehiculos.map(grupo => {
    const id = grupo[0].id;
    if (!TIPOS_CON_GIRO.has(grupo[0].tipo)) return { id, angulo: 0 };
    const enTrayecto = orientacionEnTrayecto(trayectosParaGiro.get(id), instanteMs);
    const enCortes = orientacionEnFotogramas(historialOrientacion, indiceOrientacion, id);
    const direccion = enTrayecto ?? enCortes;
    return { id, angulo: direccion === null
      ? ((enVivo ? angulosMostrados.current.get(id) : null) ?? 0)
      : direccion + (TIPOS_CENITALES.has(grupo[0].tipo) ? 90 : 0) };
  });
  const firmaGiro = objetivosGiro.map(({ id, angulo }) => `${id}:${angulo.toFixed(2)}`).join('|');

  useEffect(() => {
    if (animacionGiro.current !== null) cancelAnimationFrame(animacionGiro.current);
    animacionGiro.current = null;
    const cambios = [];
    for (const { id, angulo } of objetivosGiro) {
      const nodo = iconosVehiculos.current.get(id);
      if (!nodo) continue;
      const anterior = angulosMostrados.current.get(id);
      if (!Number.isFinite(anterior)) {
        nodo.setAttribute('transform', `rotate(${angulo})`);
        angulosMostrados.current.set(id, angulo);
        continue;
      }
      const delta = giroMasCorto(anterior, angulo);
      if (Math.abs(delta) < .01) continue;
      cambios.push({ id, nodo, anterior, delta });
    }
    if (!cambios.length) return undefined;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      for (const { id, nodo, anterior, delta } of cambios) {
        const angulo = anterior + delta;
        nodo.setAttribute('transform', `rotate(${angulo})`);
        angulosMostrados.current.set(id, angulo);
      }
      return undefined;
    }
    let inicio = null;
    const girar = ahora => {
      if (inicio === null) inicio = ahora;
      const avance = Math.min(1, (ahora - inicio) / 220);
      const suave = avance * avance * (3 - 2 * avance);
      for (const { id, nodo, anterior, delta } of cambios) {
        const angulo = anterior + delta * suave;
        nodo.setAttribute('transform', `rotate(${angulo})`);
        angulosMostrados.current.set(id, angulo);
      }
      animacionGiro.current = avance < 1 ? requestAnimationFrame(girar) : null;
    };
    animacionGiro.current = requestAnimationFrame(girar);
    return () => {
      if (animacionGiro.current !== null) cancelAnimationFrame(animacionGiro.current);
      animacionGiro.current = null;
    };
  }, [firmaGiro]);
  // Unidades detenidas sobre un almacén: se dibujan como una pastilla al costado del almacén para
  // no montar el ícono, el semáforo y el contador encima de la casa.
  const nodosAlmacen = new Set(almacenes.map(a => `${a.ubicacion.x},${a.ubicacion.y}`));
  const enAlmacen = lugar => nodosAlmacen.has(`${lugar.x},${lugar.y}`);
  const salidas = [...new Map(rutas.filter(ruta => ruta.origen && enFlota(ruta.tipoVehiculo))
    .map(ruta => [`${ruta.origen.x},${ruta.origen.y}`, ruta.origen])).values()];
  const vacio = rutas.length === 0 && vehiculosTodos.length === 0 && noAsignados.length === 0 && !mapaDatos;

  const x = xEnMapa;
  const y = yEnMapa;
  // Los marcadores logísticos crecen con el zoom sin llegar a tapar el mapa.
  const escala = 1 / vista.z;
  // Con muchas paradas o unidades a la vez (25-30 rutas simultáneas) los marcadores se encogen un
  // poco mientras se mira la ciudad entera; al acercarse recuperan su tamaño.
  const marcadoresVisibles = puntos.length + adicionales.length;
  const densidadParadas = vista.z < 2 && marcadoresVisibles > 40 ? .72 : 1;
  const densidadUnidades = vista.z < 2 && gruposVehiculos.length > 20 ? .82 : 1;
  const fijo = (px, py) => `translate(${x(px)},${y(py)}) scale(${escala * densidadParadas})`;
  const escalaIcono = Math.min(1.8, Math.sqrt(vista.z)) / vista.z;
  const icono = (px, py) => `translate(${x(px)},${y(py)}) scale(${escalaIcono})`;
  const iconoUnidad = (px, py) => `translate(${x(px)},${y(py)}) scale(${escalaIcono * densidadUnidades})`;

  // Ficha al pasar el cursor: el <title> nativo tarda en salir y no admite varias líneas.
  function mostrarTip(evento, titulo, lineas, vehiculoId = null, almacenId = null) {
    const caja = lienzoRef.current?.getBoundingClientRect();
    if (!caja) return;
    const xPuntero = evento.clientX - caja.left;
    const yPuntero = evento.clientY - caja.top;
    if (vehiculoId || almacenId) {
      const claseTip = almacenId ? 'mapa-tip-almacen' : 'mapa-tip-vehiculo';
      const tipMedida = tipRef.current?.classList.contains(claseTip) ? tipRef.current : null;
      const anchoInicial = almacenId ? (window.innerWidth <= 600 ? 300 : 330)
        : (window.innerWidth <= 600 ? 280 : 300);
      const ancho = Math.min(tipMedida?.offsetWidth || anchoInicial,
        Math.max(0, caja.width - 16));
      const alto = tipMedida?.offsetHeight || (almacenId ? 120 : 140);
      const aLaIzquierda = xPuntero + ancho + 22 > caja.width;
      const x = aLaIzquierda ? xPuntero - ancho - 14 : xPuntero + 14;
      const y = limitar(yPuntero - 18, 8, Math.max(8, caja.height - alto - 8));
      setTip(actual => actual?.fijo ? actual : {
        titulo, lineas, vehiculoId, almacenId,
        x: limitar(x, 8, Math.max(8, caja.width - ancho - 8)), y,
        aLaIzquierda, puntaY: limitar(yPuntero - y - 5, 10, Math.max(10, alto - 20))
      });
      return;
    }
    setTip(actual => actual?.fijo ? actual
      : { titulo, lineas, x: xPuntero, y: yPuntero });
  }
  const ocultarTip = () => setTip(actual => actual?.fijo ? actual : null);

  function fijarDetalle(evento, grupo) {
    const caja = lienzoRef.current?.getBoundingClientRect();
    if (!caja) return;
    const origen = evento.currentTarget.getBoundingClientRect();
    const clientX = Number.isFinite(evento.clientX) ? evento.clientX : origen.left + origen.width / 2;
    const clientY = Number.isFinite(evento.clientY) ? evento.clientY : origen.top + origen.height / 2;
    setTip({ fijo: true, ids: grupo.map(v => v.id),
      x: Math.max(10, Math.min(clientX - caja.left, caja.width - 310)),
      y: Math.max(10, Math.min(clientY - caja.top, caja.height - 350)) });
  }

  function fichaDeAlmacen(almacen) {
    const stock = stockDe(almacen);
    const inicial = stock?.capacidad ?? (almacen.stockInicial > 0 ? almacen.stockInicial : null);
    const ocupacion = ocupacionCon(stock, almacen.stockInicial);
    const nodo = `Nodo (${almacen.ubicacion.x}, ${almacen.ubicacion.y})`;
    const estadoTexto = ocupacion.porcentaje === null ? ETIQUETAS_OCUPACION[ocupacion.nivel]
      : `${ETIQUETAS_OCUPACION[ocupacion.nivel]} · ${ocupacion.porcentaje} %`;
    const lineas = [nodo, estadoTexto];
    if (!stock?.ilimitado && Number.isFinite(stock?.disponible)) {
      lineas.push(`Stock ${numero(stock.disponible)}${inicial ? ` / ${numero(inicial)}` : ''}`);
    }
    return {
      titulo: `${almacen.id} · ${almacen.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}`,
      lineas, nodo, nivel: ocupacion.nivel,
      estadoTexto,
      stockActual: stock?.ilimitado ? 'Ilimitado'
        : Number.isFinite(stock?.disponible)
          ? `${numero(stock.disponible)}${inicial ? ` / ${numero(inicial)}` : ''}` : '—'
    };
  }

  const DETALLE_MAXIMO = 6;
  function fichaDeUnidades(grupo, detalleIndividual = false) {
    if (detalleIndividual) return {
      titulo: `${grupo.length} unidades seleccionadas`,
      lineas: grupo.length ? grupo.map(v =>
        `${v.id} · ${ETIQUETA_TIPO[v.tipo] || v.tipo} · nodo (${v.ubicacion.x}, ${v.ubicacion.y}) · carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)} · ${estadoVehiculo(v.estado)}`)
        .concat(`Carga al instante ${fecha(instante)}`) : ['Sin datos para este instante.']
    };
    if (grupo.length === 1) {
      const v = grupo[0];
      const averiaDeLaUnidad = averiasVigentes.get(v.id);
      return { titulo: `${v.id} · ${ETIQUETA_TIPO[v.tipo] || v.tipo}`,
        lineas: [`Nodo (${v.ubicacion.x}, ${v.ubicacion.y})`,
          `Carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)}`,
          ETIQUETAS_CARGA[estadoDeCarga(v)], estadoVehiculo(v.estado),
          ...(averiaDeLaUnidad ? [`${ETIQUETAS_AVERIA[averiaDeLaUnidad.tipo]} · ${averiaDeLaUnidad.fase === 'en-sitio' ? 'en el lugar hasta ' + fecha(new Date(averiaDeLaUnidad.fin)) : 'permanencia cumplida'}`] : []),
          `Última publicación ${fecha(fotoObservada?.reloj)}`],
        nodo: `Nodo (${v.ubicacion.x}, ${v.ubicacion.y})`,
        estado: estadoVehiculo(v.estado), estadoClave: v.estado,
        carga: `${numero(v.cargaAbordo)} / ${numero(v.capacidad)}`,
        estadoCarga: ETIQUETAS_CARGA[estadoDeCarga(v)],
        publicacion: fecha(fotoObservada?.reloj) };
    }
    const titulo = `${grupo.length} unidades en (${grupo[0].ubicacion.x}, ${grupo[0].ubicacion.y})`;
    if (grupo.length <= DETALLE_MAXIMO) {
      return { titulo, lineas: grupo.map(v =>
        `${v.id} · carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)} · ${ETIQUETAS_CARGA[estadoDeCarga(v)]} · ${estadoVehiculo(v.estado)}`)
        .concat(`Carga publicada hasta ${fecha(instante)}`) };
    }
    // Aparcadas en el almacén pueden ser decenas: se resume por tipo en vez de listarlas.
    const porTipo = new Map();
    for (const v of grupo) {
      const actual = porTipo.get(v.tipo) || { n: 0, carga: 0, capacidad: 0 };
      porTipo.set(v.tipo, { n: actual.n + 1, carga: actual.carga + v.cargaAbordo,
        capacidad: actual.capacidad + v.capacidad });
    }
    const porNivel = new Map();
    for (const v of grupo) {
      const nivel = estadoDeCarga(v);
      porNivel.set(nivel, (porNivel.get(nivel) || 0) + 1);
    }
    return { titulo, lineas: [...porTipo].map(([tipo, t]) =>
      `${t.n} × ${ETIQUETA_TIPO[tipo] || tipo} · carga ${numero(t.carga)} / ${numero(t.capacidad)}`)
      .concat([...porNivel].map(([nivel, cantidad]) =>
        `${cantidad} unidad(es): ${ETIQUETAS_CARGA[nivel]}`))
      .concat('Clic para consultar cada unidad.', `Carga publicada hasta ${fecha(instante)}`) };
  }

  const fichaFija = tip?.fijo ? fichaDeUnidades(tip.ids.map(id =>
    vehiculosTodos.find(v => v.id === id)).filter(Boolean), true) : null;
  const vehiculoTip = tip?.vehiculoId ? vehiculosTodos.find(v => v.id === tip.vehiculoId) : null;
  const fichaVehiculo = vehiculoTip ? fichaDeUnidades([vehiculoTip]) : null;
  const almacenTip = tip?.almacenId ? almacenes.find(a => a.id === tip.almacenId) : null;
  const fichaAlmacen = almacenTip ? fichaDeAlmacen(almacenTip) : null;

  // En el teléfono los indicadores empiezan plegados: abiertos taparían medio mapa.
  const pantallaAngosta = typeof window !== 'undefined' && window.matchMedia('(max-width: 600px)').matches;
  const posicionDeslizador = enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma;
  const contadorSimulacion = enVivo
    ? `${ejecucion?.estado === 'PAUSADA' ? 'En pausa' : 'En vivo'} · ${ejecucion?.ritmo?.ventana ?? 0}/${ejecucion?.ritmo?.ventanas ?? '—'}`
    : hayReproduccion ? `Reproducción ${indiceFotograma + 1}/${fotogramas.length}` : 'Reproducción 0/0';
  const tituloContador = modoOperacion ? contadorOperacion : enVivo
    ? `${contadorSimulacion} · ${ejecucion?.ritmo ? textoRitmo(ejecucion.ritmo) : ''}`
    : `${contadorSimulacion}${finalizada && reproduciendo ? ' · cálculo terminado' : ''}`;
  // El momento simulado ya está en la cabecera (SIM): aquí solo quedan las duraciones.
  const reproductor = <div id={reproductorId}
    className={`mapa-reproductor ${reproductorAbierto ? '' : 'mapa-reproductor-cerrado'}`}
    aria-hidden={!reproductorAbierto} inert={!reproductorAbierto}>
    <div className="reproduccion-controles">
      {enVivo
        ? <button type="button" className="reproduccion-principal" disabled={fotogramas.length < 2}
            onClick={() => alCambiarFotograma(Math.max(0, fotogramas.length - 2))}>Historial</button>
        : <button type="button" className="reproduccion-principal" disabled={!hayReproduccion} onClick={alReproducir}>
            <span className="reproduccion-play" aria-hidden="true">{hayReproduccion && reproduciendo ? 'Ⅱ' : '▶'}</span>
            {!hayReproduccion ? 'Reproducir' : reproduciendo ? 'Pausar' : indiceFotograma >= fotogramas.length - 1 ? 'Repetir' : 'Reproducir'}
          </button>}
      <input type="range" min="0" max={Math.max(0, fotogramas.length - 1)}
        disabled={!hayReproduccion} value={posicionDeslizador}
        style={{ '--progreso': `${Math.round(100 * posicionDeslizador / Math.max(1, fotogramas.length - 1))}%` }}
        onChange={evento => alCambiarFotograma(Number(evento.target.value))}
        aria-label="Seleccionar instante de la reproducción" />
      <span className="reproduccion-contador" title={tituloContador}>
        {modoOperacion ? contadorOperacion : contadorSimulacion}</span>
      <button type="button" className="reproduccion-flecha" aria-label="Retroceder un instante"
        disabled={indiceFotograma <= 0} onClick={() => alCambiarFotograma(indiceFotograma - 1)}>‹</button>
      <button type="button" className="reproduccion-flecha" aria-label="Avanzar un instante"
        disabled={indiceFotograma >= fotogramas.length - 1}
        onClick={() => alCambiarFotograma(indiceFotograma + 1)}>›</button>
      {!enVivo && alVolverEnVivo && <button type="button" className="reproduccion-vivo" onClick={alVolverEnVivo}>En vivo</button>}
    </div>
    <div className="reproduccion-tiempos">
      {!modoOperacion && <div className="reproduccion-tiempo reproduccion-simulado"
        title="Tiempo simulado desde el inicio de la corrida hasta el instante mostrado">
        <IconoUI nombre="reloj" size={22} />
        <span><small>SIM transcurrido</small><b>{hayReproduccion ? duracion(aMs(instante) - aMs(ejecucion?.inicio)) : '—'}</b></span>
      </div>}
      <div className="reproduccion-tiempo reproduccion-real" title="Tiempo real desde que se inició la corrida">
        <IconoUI nombre="reloj" size={22} />
        <span><small>REAL transcurrido</small><b>{hayReproduccion ? duracion(transcurridoMs) : '—'}</b></span>
      </div>
      <label className="reproduccion-velocidad">Velocidad <select value={pasoMs} disabled={!velocidadHabilitada}
        onChange={evento => alCambiarPaso(Number(evento.target.value))}>
        <option value="800">Lenta</option><option value="300">Normal</option><option value="200">Rápida</option>
      </select></label>
    </div>
  </div>;

  // Controles del mapa: viven en la cabecera para que el lienzo quede libre.
  const controles = <div className="barra-mapa" role="toolbar" aria-label="Controles del mapa">
    <div className="barra-mapa-zoom">
      <button type="button" onClick={() => acercar(1 / 1.5)} disabled={vista.z <= ZOOM_MIN} title="Alejar" aria-label="Alejar">−</button>
      <button type="button" onClick={() => acercar(1.5)} disabled={vista.z >= ZOOM_MAX} title="Acercar" aria-label="Acercar">+</button>
    </div>
    <button type="button" className="barra-mapa-boton" onClick={() => animarVista(VISTA_INICIAL)} title="Ver toda la ciudad" aria-label="Ver toda la ciudad">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
        <path d="M4 9V4h5M4 4l5 5M15 4h5v5M20 4l-5 5M4 15v5h5M4 20l5-5M15 20h5v-5M20 20l-5-5" />
      </svg>
      <span>Todo</span>
    </button>
    <button type="button" ref={botonFiltros} className={`barra-mapa-boton ${filtros || hayFiltro ? 'activo' : ''}`}
      aria-expanded={filtros} onClick={alternarFiltros} title="Filtrar el mapa"
      aria-label={hayFiltro ? 'Filtrar (hay filtros activos)' : 'Filtrar'}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
        <path d="M3 5h18l-7 8v5l-4 2v-7L3 5Z" />
      </svg>
      <span>Filtrar{hayFiltro ? ' ·' : ''}</span>
    </button>
    {mostrarIndicadores && <button type="button" className={`barra-mapa-boton ${reproductorAbierto ? 'activo' : ''}`}
      aria-expanded={reproductorAbierto} aria-controls={reproductorId} title="Mostrar u ocultar la reproducción" aria-label="Reproducción"
      onClick={() => setReproductorAbierto(abierto => !abierto)}>
      <IconoUI nombre={reproductorAbierto ? 'pausa' : 'reproducir'} size={14} />
      <span>Reproducción</span>
    </button>}
  </div>;

  return <div className="mapa-lienzo" ref={lienzoRef}>
    <svg className="mapa-fondo-urbano"
      viewBox={`${marco.cx - anchoFondo / 2} ${marco.cy - altoFondo / 2} ${anchoFondo} ${altoFondo}`}
      aria-hidden="true" focusable="false" pointerEvents="none">
      <FondoUrbano />
    </svg>
    <svg ref={svgRef} className={`mapa ${arrastrando ? 'mapa-arrastrando' : ''}`}
      viewBox={`${marco.x} ${marco.y} ${marco.ancho} ${marco.alto}`}
      role="img" aria-label="Mapa de rutas, pedidos y vehículos sobre la retícula de la ciudad"
      onPointerDown={empezarArrastre} onPointerMove={mover}
      onPointerUp={soltar} onPointerCancel={soltar}>
      {bloqueosActivos.flatMap((bloqueo, indice) => bloqueo.nodos.map((nodo, n) =>
        <rect key={`b${indice}-${n}`} x={x(nodo.x) - BLOQUEO.lado / 2} y={y(nodo.y) - BLOQUEO.lado / 2}
          width={BLOQUEO.lado} height={BLOQUEO.lado} rx="1" fill={BLOQUEO.color} fillOpacity={BLOQUEO.opacidad}><title>{`Bloqueo activo hasta ${fecha(bloqueo.fin)}`}</title></rect>))}
      {segmentosDibujados.map(({ ruta, camino, recorrido, terminada, clave, desfase }) => {
        const coordenadas = camino.map(p => `${x(p.x)},${y(p.y)}`).join(' ');
        const resaltada = ruta.id === rutaSeleccionada || ruta.id === rutaEnFoco;
        const seleccionadaPorEnvio = rutasDelEnvio.has(ruta.id);
        return <g key={clave} style={{ '--ruta-color': colorDeRuta(ruta) }}
          className={`trazo-ruta ${terminada ? 'trazo-final' : ''} ${resaltada ? 'trazo-resaltado' : ''} ${seleccionadaPorEnvio ? 'trazo-envio-resaltado' : rutasDelEnvio.size ? 'trazo-envio-atenuado' : ''}`}>
          <polyline className={`trazo-visible ${recorrido ? 'trazo-recorrido' : 'trazo-pendiente'}`}
            points={coordenadas} fill="none"
            style={desfase ? { strokeDashoffset: desfase * 10 * escalaFondo } : undefined} />
          <polyline className="trazo-interaccion" points={coordenadas} fill="none"
            tabIndex="0" role="button" aria-label={`Resaltar ruta ${ruta.id} del vehículo ${ruta.vehiculoId}`}
            onMouseEnter={() => setRutaEnFoco(ruta.id)} onMouseLeave={() => setRutaEnFoco(null)}
            onFocus={() => setRutaEnFoco(ruta.id)} onBlur={() => setRutaEnFoco(null)}>
            <title>{`${ruta.id} · ${ruta.vehiculoId} · ${ruta.paradas.length} parada(s)`}</title>
          </polyline>
        </g>;
      })}
      {puntos.map(({ ruta, parada, estado, orden, ultima }, indice) => <g key={`p${ruta.id}-${indice}`}
        className={`mapa-parada-orden parada-${estado.toLowerCase()} ${ultima ? 'mapa-parada-final' : ''}`}
        transform={fijo(parada.destino.x, parada.destino.y)}
        style={{ '--ruta-color': colorDeRuta(ruta) }}
        onMouseEnter={() => setRutaEnFoco(ruta.id)} onMouseLeave={() => setRutaEnFoco(null)}>
        <title>{`${ruta.vehiculoId} · parada ${orden} de ${ruta.paradas.length} · pedido ${parada.id} · ${ETIQUETA_ESTADO[estado]}${ultima ? ' · última parada' : ''}`}</title>
        {ultima && <circle className="aro-final" r="13" />}
        {estado === 'ENTREGADO' && <circle className="aro-entregado" r="12.5" />}
        <circle r="9" /><text textAnchor="middle" y="4">{orden}</text>
      </g>)}
      {sinAsignar.map((pedido, indice) => <circle key={`n${indice}`} className="mapa-sin-asignar"
        cx={x(pedido.destino?.x)} cy={y(pedido.destino?.y)} r={5 * escala} strokeWidth={1.5 * escala}>
        <title>{`${pedido.id} · sin asignar`}</title>
      </circle>)}
      {adicionales.map(pedido => <g key={`activo-${pedido.id}`}
        transform={fijo(pedido.destino.x, pedido.destino.y)} className="mapa-pedido-activo"
        role="img" aria-label={`Pedido ${pedido.id}, ${pedido.situacion}, unidad ${pedido.vehiculo || 'sin asignar'}`}
        onPointerEnter={e => mostrarTip(e, `Pedido ${pedido.id}`,
          [`${pedido.cantidad} productos · ${pedido.situacion}`, `Unidad ${pedido.vehiculo || 'sin asignar'}`])}
        onPointerLeave={ocultarTip}>
        <title>{`${pedido.id} · ${pedido.situacion} · ${pedido.vehiculo || 'sin asignar'}`}</title>
        <circle r="9" fill="#ffffff" stroke="#087f8c" strokeWidth="2" />
        <text y="4" textAnchor="middle" fontSize="10" fill="#07515d">P</text>
      </g>)}
      {[...coincidentes.entries()].filter(([, grupo]) => grupo.size > 1).map(([clave, grupo]) => {
        const pedidos = [...grupo.values()];
        const destino = pedidos[0].destino;
        return <g key={`coincidentes-${clave}`} transform={fijo(destino.x, destino.y)}
          role="img" aria-label={`${pedidos.length} pedidos en este destino: ${pedidos.map(p => p.id).join(', ')}`}
          onPointerEnter={e => mostrarTip(e, `${pedidos.length} pedidos en el mismo destino`,
            pedidos.map(p => `${p.id} · ${p.cantidad} productos`))}
          onPointerLeave={ocultarTip}>
          <circle cx="13" cy="-12" r="8" fill="#073b4c" stroke="white" strokeWidth="1.5" />
          <text x="13" y="-9" textAnchor="middle" fill="white" fontSize="9">{pedidos.length}</text>
        </g>;
      })}
      {almacenesVisibles.map(almacen => {
        const activo = almacen.id === almacenSeleccionado;
        const { nivel } = ocupacionCon(stockDe(almacen), almacen.stockInicial);
        return <g key={almacen.id} className={`mapa-almacen ${activo ? 'almacen-activo' : ''}`}
        transform={icono(almacen.ubicacion.x, almacen.ubicacion.y)}
        role={alSeleccionarAlmacen ? 'button' : 'img'}
        tabIndex={alSeleccionarAlmacen ? 0 : undefined}
        aria-pressed={alSeleccionarAlmacen ? activo : undefined}
        aria-label={`${almacen.id}. ${fichaDeAlmacen(almacen).lineas.join('. ')}`}
        onPointerEnter={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas, null, almacen.id); }}
        onPointerMove={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas, null, almacen.id); }}
        onPointerLeave={ocultarTip}
        onPointerDown={e => { if (alSeleccionarAlmacen) e.stopPropagation(); }}
        onClick={() => alSeleccionarAlmacen?.(activo ? null : almacen.id)}
        onKeyDown={e => {
          if (!alSeleccionarAlmacen || (e.key !== 'Enter' && e.key !== ' ')) return;
          e.preventDefault();
          alSeleccionarAlmacen(activo ? null : almacen.id);
        }}>
        {/* Resalte del almacén elegido, en el mapa o en la lista de inventario (F05). */}
        {activo && <circle className="almacen-halo" r={ICONO_ALMACEN.halo} />}
        {/* El nivel de ocupación pinta todo el ícono: ya no hace falta la luz aparte. */}
        <DibujoAlmacen tipo={almacen.tipo} nivel={nivel} />
      </g>;
      })}
      {salidas.map(punto => <g key={`salida-${punto.x}-${punto.y}`} className="mapa-salida"
        transform={fijo(punto.x, punto.y)}>
        <title>{`Salida de ruta desde el nodo (${punto.x}, ${punto.y})`}</title>
        <path d="M0 -10 V-25" /><circle cx="0" cy="-29" r="10" />
        <text x="0" y="-25" textAnchor="middle">S</text>
      </g>)}
      {gruposVehiculos.map(grupo => {
        const elegible = grupo.length === 1 && !!alSeleccionarUnidad;
        const activa = grupo.some(v => v.id === unidadSeleccionada);
        const medidas = medidasDeIcono(grupo[0].tipo);
        const lugar = posicionDibujada(grupo[0]);
        const aparcadas = enAlmacen(lugar);
        const ficha = fichaDeUnidades(grupo);
        return <g key={grupo[0].id}
        className={`mapa-unidad ${grupo.length > 1 ? 'grupo-vehiculos' : ''} ${activa ? 'unidad-activa' : ''} ${aparcadas ? 'unidades-aparcadas' : ''}`}
        transform={iconoUnidad(lugar.x, lugar.y)}
        role={grupo.length > DETALLE_MAXIMO || elegible ? 'button' : 'img'}
        tabIndex={grupo.length > DETALLE_MAXIMO || elegible ? 0 : undefined}
        aria-label={`${ficha.titulo}. ${ficha.lineas.join('. ')}`}
        onPointerEnter={e => mostrarTip(e, ficha.titulo, ficha.lineas, grupo.length === 1 ? grupo[0].id : null)}
        onPointerMove={e => mostrarTip(e, ficha.titulo, ficha.lineas, grupo.length === 1 ? grupo[0].id : null)}
        onPointerLeave={ocultarTip}
        onPointerDown={e => { if (grupo.length > DETALLE_MAXIMO || elegible) e.stopPropagation(); }}
        onClick={e => {
          if (grupo.length > DETALLE_MAXIMO) fijarDetalle(e, grupo);
          else if (elegible) alSeleccionarUnidad(activa ? null : grupo[0].id);
        }}
        onKeyDown={e => {
          if (e.key !== 'Enter' && e.key !== ' ') return;
          if (grupo.length > DETALLE_MAXIMO) { e.preventDefault(); fijarDetalle(e, grupo); }
          else if (elegible) { e.preventDefault(); alSeleccionarUnidad(activa ? null : grupo[0].id); }
        }}>
        {aparcadas ? <PastillaAparcadas grupo={grupo} activa={activa}
          nivel={estadoDeGrupo(grupo)} averia={averiaDeGrupo(grupo, averiasVigentes)} /> : <>
        {/* Resalte de la unidad elegida, en el mapa o en la lista del riel (F01). */}
          {activa && <circle className="unidad-halo" r={medidas.halo} />}
          {/* Ícono de vehículo con la cantidad en un globito: no debe confundirse con una parada. */}
          <g ref={nodo => {
            if (nodo) {
              iconosVehiculos.current.set(grupo[0].id, nodo);
              nodo.setAttribute('transform', `rotate(${angulosMostrados.current.get(grupo[0].id) ?? 0})`);
            } else iconosVehiculos.current.delete(grupo[0].id);
          }}>
            <svg x={-medidas.lado / 2} y={-medidas.lado / 2} width={medidas.lado} height={medidas.lado} viewBox="0 0 32 32">
              {!TIPOS_CENITALES.has(grupo[0].tipo) && <circle cx="16" cy="16" r="15" fill="white" stroke="#c8dce0" strokeWidth="1.5" />}
              <DibujoVehiculo tipo={grupo[0].tipo} />
            </svg>
          </g>
          <circle className={`mapa-carga mapa-carga-${estadoDeGrupo(grupo)}`}
            cx={medidas.carga.x} cy={medidas.carga.y} r="4.5" pointerEvents="none" />
          {/* Avería por tipo (D10-D12): triángulo en el hombro izquierdo, para no confundirse con
              el semáforo de carga —que es un círculo, al otro lado— ni con el color de la ruta. */}
          {(() => {
            const averia = averiaDeGrupo(grupo, averiasVigentes);
            if (!averia) return null;
            return <g className={`mapa-averia mapa-averia-${averia.tipo.toLowerCase()}`}
              transform={`translate(${medidas.averia.x}, ${medidas.averia.y})`}
              pointerEvents="none">
              <title>{ETIQUETAS_AVERIA[averia.tipo]}</title>
              <path d="M0 -6.2 L5.6 4 H-5.6 Z" />
              <text y="3" textAnchor="middle">{averia.tipo.slice(-1)}</text>
            </g>;
          })()}
          {grupo.length > 1 && <>
            <circle className="grupo-contador" cx="12" cy="11" r="8" />
            <text x="12" y="14.5" textAnchor="middle">{grupo.length}</text>
          </>}
        </>}
      </g>;
      })}
    </svg>

    {ranuraControles ? createPortal(controles, ranuraControles) : controles}

    {filtros && <div className="mapa-filtros" style={lugarFiltros || undefined}>
      <div><span className="filtro-titulo"><IconoUI nombre="camion" size={19} className="filtro-encabezado-icono" />Flota</span><div className="filtro-opciones">
        <button type="button" className={flota === '' ? 'activo' : ''} onClick={() => setFlota('')}>Todas</button>
        {TIPOS_FLOTA.map(([valor, texto]) => <button key={valor} type="button"
          className={flota === valor ? 'activo' : ''} onClick={() => setFlota(valor)}>{texto}</button>)}
      </div></div>
      <div><span className="filtro-titulo"><IconoUI nombre="pedidos" size={19} className="filtro-encabezado-icono" />Pedidos</span><div className="filtro-opciones">
        <button type="button" className={estadoPedido === '' ? 'activo' : ''} onClick={() => setEstadoPedido('')}>Todos</button>
        {ESTADOS_PEDIDO.map(([valor, texto]) => <button key={valor} type="button"
          className={estadoPedido === valor ? 'activo' : ''} onClick={() => setEstadoPedido(valor)}>{texto}</button>)}
      </div></div>
      <div><span className="filtro-titulo"><IconoUI nombre="camion" size={19} className="filtro-encabezado-icono" />Carga de la unidad</span><div className="filtro-opciones">
        <button type="button" className={nivelCarga === '' ? 'activo' : ''} onClick={() => setNivelCarga('')}>Todas</button>
        {NIVELES_CARGA.map(([valor, texto]) => <button key={valor} type="button"
          className={nivelCarga === valor ? 'activo' : ''} onClick={() => setNivelCarga(valor)}
          title={ETIQUETAS_CARGA[valor]}>
          <i className={`filtro-luz mapa-carga-${valor}`} aria-hidden="true" />{texto}</button>)}
      </div></div>
      <div><span className="filtro-titulo"><IconoUI nombre="caja" size={19} className="filtro-encabezado-icono" />Stock del almacén</span><div className="filtro-opciones">
        <button type="button" className={nivelStock === '' ? 'activo' : ''} onClick={() => setNivelStock('')}>Todos</button>
        {NIVELES_STOCK.map(([valor, texto]) => <button key={valor} type="button"
          className={nivelStock === valor ? 'activo' : ''} onClick={() => setNivelStock(valor)}
          title={ETIQUETAS_STOCK[valor]}>
          <i className={`filtro-luz mapa-stock-${valor}`} aria-hidden="true" />{texto}</button>)}
      </div></div>
      <div className="filtro-pie">
        <span>{numero(vehiculos.length)} unidad(es) · {numero(almacenesVisibles.length)} almacén(es) · {numero(puntos.length + adicionales.length + sinAsignar.length)} pedido(s) a la vista</span>
        <button type="button" disabled={!hayFiltro} onClick={() => {
          setFlota(''); setEstadoPedido(''); setNivelCarga(''); setNivelStock('');
        }}>Limpiar filtros</button>
      </div>
    </div>}

    {mostrarIndicadores && <>
      {indicadoresPedidos.length > 0 && <PanelFlotante id="productos" className="panel-productos"
        abiertoInicial={!pantallaAngosta}
        titulo="Productos" icono={<IconoUI nombre="caja" size={15} />}
        resumen={`${numero(indicadoresPedidos[0].productos)} entregados`}>
        <div className="productos-rejilla">
          {indicadoresPedidos.map(dato => <div key={dato.clave} className="productos-celda"
            data-alerta={dato.alerta ? '' : undefined} title={dato.titulo}>
            <span className="productos-etiqueta"><IconoUI nombre={dato.icono} size={13} />{dato.etiqueta}</span>
            <b>{numero(dato.productos)}</b>
            <small>{numero(dato.pedidos)} pedido(s)</small>
          </div>)}
        </div>
      </PanelFlotante>}
      <PanelFlotante id="estado" className="panel-estado" titulo="Estado" abiertoInicial={!pantallaAngosta}
        icono={<IconoUI nombre="metricas" size={15} />}
        resumen={`Flota ${flotaGlobal.porcentaje ?? '—'} % · Alm. ${almacenesGlobal.porcentaje ?? '—'} %`}>
        <div className="mapa-estado-datos"><IndicadoresMapa flotaGlobal={flotaGlobal}
          almacenesGlobal={almacenesGlobal} etiquetasCarga={ETIQUETAS_CARGA} /></div>
      </PanelFlotante>
      {/* Plegada por omisión: con 25 o 30 rutas simultáneas la leyenda tapa una franja del mapa. */}
      <PanelFlotante id="leyenda" className="panel-leyenda" titulo="Leyenda" abiertoInicial={false}>
        <div className="leyenda">
          <span><svg className="leyenda-trazo" viewBox="0 0 28 8" aria-hidden="true" focusable="false"><path d="M1 4h26" /></svg> Ruta pendiente (color de su unidad)</span>
          <span><svg className="leyenda-trazo leyenda-trazo-recorrido" viewBox="0 0 28 8" aria-hidden="true" focusable="false"><path d="M1 4h26" /></svg> Tramo ya recorrido</span>
          <span><i className="punto punto-salida" /> S: salida</span>
          <span><i className="punto punto-llegada" /> Números: orden de entrega (aro: última)</span>
          <span><i className="punto punto-alerta" /> Sin asignar</span>
          <span><i className="punto punto-bloqueo" /> Bloqueo activo ({bloqueosActivos.length})</span>
          {['TIPO_1', 'TIPO_2', 'TIPO_3'].map(tipo => <span key={tipo} title={ETIQUETAS_AVERIA[tipo]}>
            <svg className={`leyenda-averia mapa-averia mapa-averia-${tipo.toLowerCase()}`}
              viewBox="-8 -8 16 16" aria-hidden="true" focusable="false">
              <path d="M0 -6.2 L5.6 4 H-5.6 Z" /><text y="3" textAnchor="middle">{tipo.slice(-1)}</text>
            </svg> Avería tipo {tipo.slice(-1)}</span>)}
          <span className="leyenda-almacen"><DibujoAlmacen tipo="CENTRAL" leyenda /> Central</span>
          <span className="leyenda-almacen"><DibujoAlmacen tipo="INTERMEDIO" leyenda /> Intermedio</span>
          {NIVELES_OCUPACION.map(nivel => <span key={nivel} title="Ocupación del almacén intermedio">
            <i className="punto" style={{ background: COLORES_OCUPACION[nivel].relleno,
              boxShadow: `inset 0 0 0 1.5px ${COLORES_OCUPACION[nivel].contorno}` }} /> {ETIQUETAS_OCUPACION[nivel]}</span>)}
          <IconoLeyenda tipo="AUTO" texto="Auto" />
          <IconoLeyenda tipo="MOTO" texto="Moto" />
          <IconoLeyenda tipo="BICICLETA" texto="Bicicleta" />
          <span><i className="punto punto-vehiculo" /> Ícono con número: varias unidades en el mismo nodo</span>
          <span><span className="leyenda-aparcadas">▪ 12</span> Unidades detenidas en un almacén</span>
        </div>
      </PanelFlotante>
    </>}

    {mostrarIndicadores && <div className="mapa-abajo">{reproductor}</div>}

    {calculandoRutas && <div className="mapa-cartel mapa-calculando" role="status" aria-live="polite">
      <span className="indicador-calculo" aria-hidden="true" />
      <b>{ejecucion?.estado === 'PENDIENTE' ? 'Preparando la corrida…' : 'Calculando rutas…'}</b>
      <span>El planificador está trabajando. Mostraremos las rutas nuevas cuando estén listas.</span>
    </div>}
    {tip && <div ref={tipRef} className={`mapa-tip ${tip.fijo ? 'mapa-tip-fija' : ''} ${fichaVehiculo ? 'mapa-tip-vehiculo' : ''} ${fichaAlmacen ? 'mapa-tip-almacen' : ''} ${tip.aLaIzquierda ? 'mapa-tip-izquierda' : ''}`}
      style={{ left: tip.x, top: tip.y, '--punta-y': `${tip.puntaY ?? 18}px` }} role="tooltip">
      {fichaVehiculo ? <>
        <div className="mapa-tip-cabecera">
          <div><strong>{fichaVehiculo.titulo}</strong><span className="mapa-tip-nodo">{fichaVehiculo.nodo}</span></div>
          <span className={`mapa-tip-estado mapa-tip-estado-${({ DISPONIBLE: 'disponible', EN_RUTA: 'en-ruta', AVERIADO: 'averiado', EN_MANTENIMIENTO: 'mantenimiento' })[fichaVehiculo.estadoClave] || 'otro'}`}>
            <i aria-hidden="true" />{fichaVehiculo.estado}
          </span>
        </div>
        <div className="mapa-tip-filas">
          <div className="mapa-tip-fila"><IconoUI nombre="caja" size={17} /><span>Carga actual</span><strong>{fichaVehiculo.carga}</strong></div>
          <div className="mapa-tip-fila"><IconoUI nombre="pedidos" size={17} /><span>Estado de carga</span><strong>{fichaVehiculo.estadoCarga}</strong></div>
          <div className="mapa-tip-fila"><span className="mapa-tip-calendario"><IconoUI nombre="calendario" size={17} /><IconoUI nombre="reloj" size={9} /></span><span>Última publicación</span><strong>{fichaVehiculo.publicacion}</strong></div>
        </div>
      </> : fichaAlmacen ? <>
        <div className="mapa-tip-cabecera">
          <div><strong>{fichaAlmacen.titulo}</strong><span className="mapa-tip-nodo">{fichaAlmacen.nodo}</span></div>
          <span className={`mapa-tip-estado mapa-tip-ocupacion-${fichaAlmacen.nivel}`}>
            <i aria-hidden="true" />{fichaAlmacen.estadoTexto}
          </span>
        </div>
        <div className="mapa-tip-filas">
          <div className="mapa-tip-fila"><IconoUI nombre="caja" size={17} /><span>Stock</span><strong>{fichaAlmacen.stockActual}</strong></div>
        </div>
      </> : <>
        {tip.fijo && <button type="button" aria-label="Cerrar detalle de unidades"
          onClick={() => setTip(null)}>×</button>}
        <b>{fichaFija?.titulo || tip.titulo}</b>
        {(fichaFija?.lineas || tip.lineas).map((linea, indice) => <span key={`${indice}-${linea}`}>{linea}</span>)}
      </>}
    </div>}

    {vacio && <div className="mapa-cartel"><b>Sin operación cargada</b>
      <span>Elige un periodo en el panel de la derecha e inicia una corrida para ver el mapa.</span></div>}
  </div>;
}

/**
 * Unidades detenidas en un almacén: una pastilla al lado derecho de la casa con la silueta del tipo
 * más frecuente y la cantidad. El borde de la pastilla toma el semáforo de carga del grupo.
 */
function PastillaAparcadas({ grupo, activa, nivel, averia }) {
  const porTipo = new Map();
  for (const v of grupo) porTipo.set(v.tipo, (porTipo.get(v.tipo) || 0) + 1);
  const tipo = [...porTipo].sort((a, b) => b[1] - a[1])[0][0];
  const texto = String(grupo.length);
  const ancho = 25 + texto.length * 6.5;
  return <g transform="translate(17, 9)">
    {activa && <rect className="unidad-halo" x="-4" y="-13" width={ancho + 8} height="26" rx="13" />}
    <rect className={`aparcadas-fondo mapa-carga-${nivel}`} x="0" y="-9" width={ancho} height="18" rx="9" />
    <svg x="3" y="-7" width="14" height="14" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <DibujoVehiculo tipo={tipo} />
    </svg>
    <text className="aparcadas-cantidad" x={19} y="3.6">{texto}</text>
    {averia && <g className={`mapa-averia mapa-averia-${averia.tipo.toLowerCase()}`}
      transform={`translate(${ancho + 3}, -8) scale(.8)`} pointerEvents="none">
      <path d="M0 -6.2 L5.6 4 H-5.6 Z" /><text y="3" textAnchor="middle">{averia.tipo.slice(-1)}</text>
    </g>}
  </g>;
}

/**
 * Casa del almacén. Con `nivel` de ocupación (vacío, bajo, intermedio o alto) todo el ícono toma el
 * color de la codificación; sin nivel conserva el morado propio de la leyenda, del almacén central
 * (stock ilimitado) y del almacén sin corte de inventario.
 */
function DibujoAlmacen({ tipo, nivel = null, leyenda = false }) {
  const gradiente = useId();
  const paleta = COLORES_OCUPACION[nivel] || null;
  const detalle = paleta ? paleta.detalle : 'white';
  const contorno = paleta ? paleta.contorno : 'none';
  if (tipo === 'CENTRAL') {
    const { ancho, alto } = ICONO_ALMACEN.CENTRAL;
    const cuerpo = paleta ? paleta.relleno : '#8E24AA';
    return <svg x={leyenda ? undefined : -ancho / 2}
      y={leyenda ? undefined : -alto / 2} width={leyenda ? 20 : ancho} height={leyenda ? 20 : alto}
      viewBox="0 0 36 40" overflow="visible" aria-hidden="true" focusable="false">
      <defs><linearGradient id={gradiente} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#B667D0" /><stop offset="1" stopColor="#8E24AA" />
      </linearGradient></defs>
      <path d="M18 1 Q19 1 20 2 L34.5 17 Q36 19 34.5 21 L20 37 Q18 39 16 37 L1.5 21 Q0 19 1.5 17 L16 2 Q17 1 18 1Z"
        fill={paleta ? paleta.relleno : `url(#${gradiente})`} stroke={contorno} strokeWidth="1.4" />
      <path d="M6 19 18 9 30 19 V34 H6Z" fill={cuerpo} />
      <path d="M5.5 19 18 8 30.5 19 M5.5 19v15 M30.5 19v15"
        fill="none" stroke={detalle} strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <text x="18" y="25.5" textAnchor="middle" fontFamily="Archivo, sans-serif" fontSize="9.5"
        fontWeight="900" fill={detalle}>C</text>
      <rect x="10" y="27" width="16" height="8" rx="1.2" fill={detalle} />
      <rect x="12" y="30" width="9" height="4" rx=".6" fill={paleta ? paleta.relleno : '#6A1B9A'} />
      <path d="M21 31h2l2 2v1h-4Z" fill={cuerpo} />
      <circle cx="14.5" cy="34" r="1.15" fill={paleta ? paleta.relleno : '#6A1B9A'} stroke={detalle} strokeWidth=".5" />
      <circle cx="23" cy="34" r="1.15" fill={paleta ? paleta.relleno : '#6A1B9A'} stroke={detalle} strokeWidth=".5" />
    </svg>;
  }
  const { ancho, alto } = ICONO_ALMACEN.INTERMEDIO;
  const cuerpo = paleta ? paleta.relleno : '#9222B4';
  return <svg x={leyenda ? undefined : -ancho / 2} y={leyenda ? undefined : -alto / 2}
    width={leyenda ? 20 : ancho} height={leyenda ? 20 : alto} viewBox="0 0 20 20"
    overflow="visible" aria-hidden="true" focusable="false">
    {paleta && <path d="M1.7 7.9 9.5 2.3q.5-.4 1 0l7.8 5.6" fill="none"
      stroke={contorno} strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round" />}
    <path d="M2.7 10.5 10 5.2l7.3 5.3V18q0 .6-.6.6H3.3q-.6 0-.6-.6Z" fill={cuerpo}
      stroke={contorno} strokeWidth=".7" strokeLinejoin="round" />
    <path d="M1.7 7.9 9.5 2.3q.5-.4 1 0l7.8 5.6" fill="none"
      stroke={cuerpo} strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M2.4 9.2 10 4l7.6 5.2" fill="none" stroke={detalle}
      strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M8.5 10.5h3M10 10.5v5.2M8.5 15.7h3" fill="none"
      stroke={detalle} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}

const ETIQUETA_TIPO = {
  AUTO: 'Auto', MOTO: 'Moto', BICICLETA: 'Bicicleta', CAMION: 'Camión'
};

const ETIQUETA_ESTADO = {
  PLANIFICADO: 'planificado', EN_RUTA: 'en ruta', ENTREGADO: 'entregado', NO_ASIGNADO: 'sin asignar'
};
