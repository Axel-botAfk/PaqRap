import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ANCHO, ALTO, xEnMapa, yEnMapa, fotogramaObservadoEn, nodosBloqueados,
  prepararTrayectos, posicionEnTrayecto, cargaEnTrayecto, trazosConRecorrido,
  segmentosEnInstante, rutaFinalizadaEn, orientacionEnTrayecto, orientacionEnFotogramas,
  giroMasCorto } from '../mapa.js';
import { aMs } from '../reloj.js';
import { esFinal } from '../services/api.js';
import { fecha, numero, duracion, colorDeRuta, textoRitmo, estadoVehiculo } from '../formato.js';
import DibujoVehiculo, { IconoLeyenda } from './DibujoVehiculo.jsx';
import FondoUrbano from './FondoUrbano.jsx';
import IconoUI from './IconoUI.jsx';
import { estadoDeStock, ETIQUETAS_STOCK } from '../stock.js';
import { estadoDeCarga, estadoDeGrupo, ETIQUETAS_CARGA } from '../carga.js';

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

export default function Mapa({ ejecucion, rutas, calculandoRutas, rutaSeleccionada, mapaDatos,
  inventario, instante, fotogramas, indiceFotograma, alCambiarFotograma, reproduciendo,
  alReproducir, pasoMs, alCambiarPaso, velocidadHabilitada = false,
  enVivo = false, trayectosEnVivo = null,
  trayectosDelInstante = null,
  alVolverEnVivo = null, noAsignadosDelInstante = null, estadosPedido = null,
  modoOperacion = false, transcurridoMs = null, stockDelInstante = false, ahoraReal = null,
  pedidosDelInstante = [] }) {
  const [rutaEnFoco, setRutaEnFoco] = useState(null);
  const [vista, setVista] = useState(VISTA_INICIAL);
  const [arrastrando, setArrastrando] = useState(false);
  const [filtros, setFiltros] = useState(false);
  const [flota, setFlota] = useState('');
  const [estadoPedido, setEstadoPedido] = useState('');
  // Cerrada por omisión: con 25 o 30 rutas simultáneas la leyenda tapa una franja del mapa.
  const [pieAbierto, setPieAbierto] = useState(false);
  const svgRef = useRef(null);
  const lienzoRef = useRef(null);
  const tipRef = useRef(null);
  const [tip, setTip] = useState(null);
  const arrastre = useRef(null);
  const animacionZoom = useRef(null);
  const animacionGiro = useRef(null);
  const iconosVehiculos = useRef(new Map());
  const angulosMostrados = useRef(new Map());
  const vistaActual = useRef(vista);
  vistaActual.current = vista;

  const marco = encuadre(vista);

  // Píxeles de pantalla por unidad del lienzo. Con preserveAspectRatio por omisión el
  // contenido se centra y sobra margen en el lado largo, así que manda el lado que ajusta.
  function escalaEnPantalla() {
    const caja = svgRef.current?.getBoundingClientRect();
    if (!caja?.width) return null;
    return { caja, px: Math.min(caja.width / marco.ancho, caja.height / marco.alto) };
  }

  function cancelarZoom() {
    if (animacionZoom.current !== null) cancelAnimationFrame(animacionZoom.current);
    animacionZoom.current = null;
  }

  function aplicarVista(siguiente) {
    vistaActual.current = siguiente;
    setVista(siguiente);
  }

  function acercar(factor, hacia = null, animar = false) {
    cancelarZoom();
    const actual = vistaActual.current;
    const previo = encuadre(actual);
    const z = limitar(actual.z * factor, ZOOM_MIN, ZOOM_MAX);
    if (z === actual.z) return;
    // El punto bajo el cursor se queda donde está: el resto se abre a su alrededor.
    const proporcion = actual.z / z;
    const destino = hacia ? {
      z, cx: hacia.x - (hacia.x - previo.cx) * proporcion,
      cy: hacia.y - (hacia.y - previo.cy) * proporcion
    } : { ...actual, z };
    const final = encuadre(destino);
    if (!animar || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      aplicarVista({ cx: final.cx, cy: final.cy, z });
      return;
    }
    const inicio = performance.now();
    const paso = ahora => {
      const avance = limitar((ahora - inicio) / DURACION_ZOOM_MS, 0, 1);
      const suave = avance * avance * (3 - 2 * avance);
      const centro = encuadre({
        z: actual.z + (z - actual.z) * suave,
        cx: actual.cx + (final.cx - actual.cx) * suave,
        cy: actual.cy + (final.cy - actual.cy) * suave
      });
      aplicarVista({ z: LIENZO_ANCHO / centro.ancho, cx: centro.cx, cy: centro.cy });
      if (avance < 1) animacionZoom.current = requestAnimationFrame(paso);
      else animacionZoom.current = null;
    };
    animacionZoom.current = requestAnimationFrame(paso);
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
      const { caja, px } = medida;
      const hacia = {
        x: marco.cx + (evento.clientX - (caja.left + caja.width / 2)) / px,
        y: marco.cy + (evento.clientY - (caja.top + caja.height / 2)) / px
      };
      acercar(evento.deltaY < 0 ? 1.2 : 1 / 1.2, hacia);
    };
    svg.addEventListener('wheel', rodar, { passive: false });
    return () => svg.removeEventListener('wheel', rodar);
  }, [marco.cx, marco.cy, marco.ancho, marco.alto]);

  function empezarArrastre(evento) {
    cancelarZoom();
    const medida = escalaEnPantalla();
    if (!medida) return;
    arrastre.current = { clientX: evento.clientX, clientY: evento.clientY,
      cx: marco.cx, cy: marco.cy, px: medida.px };
    setArrastrando(true);
    evento.currentTarget.setPointerCapture?.(evento.pointerId);
  }

  function mover(evento) {
    const inicio = arrastre.current;
    if (!inicio) return;
    setVista(actual => ({ ...actual,
      cx: inicio.cx - (evento.clientX - inicio.clientX) / inicio.px,
      cy: inicio.cy - (evento.clientY - inicio.clientY) / inicio.px }));
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
  const bloqueosActivos = (mapaDatos?.bloqueos || []).filter(b => instante && b.inicio <= instante && b.fin > instante);
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
    return { ...vehiculo, ubicacion: ubicacion || vehiculo.ubicacion,
      cargaAbordo: cargaEnTrayecto(preparado, instanteMs, vehiculo.cargaAbordo) };
  }) : posicionesObservadas;

  const trazosTodos = useMemo(() => trazosConRecorrido(rutas, trayectosParaGiro, bloqueados),
    [rutas, bloqueados, trayectosParaGiro]);

  // ── filtros del mapa ───────────────────────────────────────────────────────────────
  const estadoDe = pedido => estadosPedido?.get(pedido.id) || 'PLANIFICADO';
  const enFlota = tipo => !flota || tipo === flota;
  const enEstado = valor => !estadoPedido || valor === estadoPedido;

  const vehiculos = vehiculosTodos.filter(v => enFlota(v.tipo));
  const trazos = estadoPedido === 'NO_ASIGNADO' ? LISTA_VACIA
    : trazosTodos.filter(({ ruta }) => enFlota(ruta.tipoVehiculo));
  const segmentosDibujados = trazos.flatMap(({ ruta, detalles }) => {
    const terminada = rutaFinalizadaEn(ruta, trayectosParaGiro.get(ruta.vehiculoId), instanteMs);
    return detalles.flatMap((detalle, indice) => segmentosEnInstante(detalle, instanteMs)
      .map((segmento, parte) => ({ ...segmento, ruta, terminada,
        clave: `${ruta.id}-${indice}-${parte}` })));
  }).sort((a, b) => Number(b.recorrido) - Number(a.recorrido));
  const puntos = rutas.filter(ruta => enFlota(ruta.tipoVehiculo))
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
  const hayFiltro = !!flota || !!estadoPedido;

  const finalizada = ejecucion && esFinal(ejecucion.estado);
  const hayReproduccion = fotogramas.length > 0 || enVivo;
  const contadorOperacion = `Reproducción ${fotogramas.length ? (enVivo ? fotogramas.length : indiceFotograma + 1) : 0}/${fotogramas.length}`;
  const gruposVehiculos = [...vehiculos.reduce((grupos, vehiculo) => {
    if (!vehiculo.ubicacion) return grupos;
    const clave = `${vehiculo.ubicacion.x},${vehiculo.ubicacion.y}`;
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
  const salidas = [...new Map(rutas.filter(ruta => ruta.origen && enFlota(ruta.tipoVehiculo))
    .map(ruta => [`${ruta.origen.x},${ruta.origen.y}`, ruta.origen])).values()];
  const vacio = rutas.length === 0 && vehiculosTodos.length === 0 && noAsignados.length === 0 && !mapaDatos;

  const x = xEnMapa;
  const y = yEnMapa;
  // Los marcadores logísticos crecen con el zoom sin llegar a tapar el mapa.
  const escala = 1 / vista.z;
  const fijo = (px, py) => `translate(${x(px)},${y(py)}) scale(${escala})`;
  const escalaIcono = Math.min(1.8, Math.sqrt(vista.z)) / vista.z;
  const icono = (px, py) => `translate(${x(px)},${y(py)}) scale(${escalaIcono})`;

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
    const inicial = almacen.stockInicial > 0 ? almacen.stockInicial : null;
    const estado = estadoDeStock(stock, almacen.stockInicial);
    const nodo = `Nodo (${almacen.ubicacion.x}, ${almacen.ubicacion.y})`;
    const lineas = [nodo, ETIQUETAS_STOCK[estado]];
    if (stock?.ilimitado) lineas.push('Stock ilimitadas');
    else if (stock && Number.isFinite(stock.disponible)) {
      lineas.push(`Stock ${numero(stock.disponible)}${inicial ? ` / ${numero(inicial)} iniciales` : ''}`);
      if (inventario?.reloj) lineas.push(`Último corte disponible: ${fecha(inventario.reloj)}`);
    }
    return {
      titulo: `${almacen.id} · ${almacen.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}`,
      lineas, nodo, estado, estadoTexto: ETIQUETAS_STOCK[estado],
      stockActual: stock?.ilimitado ? 'Ilimitado'
        : Number.isFinite(stock?.disponible)
          ? `${numero(stock.disponible)}${inicial ? ` / ${numero(inicial)} iniciales` : ''}` : '—',
      ultimoCorte: inventario?.reloj && stock ? fecha(inventario.reloj) : '—'
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
      return { titulo: `${v.id} · ${ETIQUETA_TIPO[v.tipo] || v.tipo}`,
        lineas: [`Nodo (${v.ubicacion.x}, ${v.ubicacion.y})`,
          `Carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)}`,
          ETIQUETAS_CARGA[estadoDeCarga(v)], estadoVehiculo(v.estado),
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

  return <div className="mapa-lienzo" ref={lienzoRef}>
    <div className="mapa-reloj-actual">
      <time dateTime={ahoraReal ? new Date(ahoraReal).toISOString() : undefined}>
        Ahora: {ahoraReal ? fecha(new Date(ahoraReal)) : '—'}
      </time>
    </div>
    <svg className="mapa-fondo-urbano"
      viewBox={`${marco.x} ${marco.y} ${marco.ancho} ${marco.alto}`}
      aria-hidden="true" focusable="false" pointerEvents="none">
      <FondoUrbano />
    </svg>
    <svg ref={svgRef} className={`mapa ${arrastrando ? 'mapa-arrastrando' : ''}`}
      viewBox={`${marco.x} ${marco.y} ${marco.ancho} ${marco.alto}`}
      role="img" aria-label="Mapa de rutas, pedidos y vehículos sobre la retícula de la ciudad"
      onPointerDown={empezarArrastre} onPointerMove={mover}
      onPointerUp={soltar} onPointerCancel={soltar}>
      {bloqueosActivos.flatMap((bloqueo, indice) => bloqueo.nodos.map((nodo, n) =>
        <rect key={`b${indice}-${n}`} x={x(nodo.x) - 4} y={y(nodo.y) - 4} width="8" height="8"
          fill="#ce5647" fillOpacity=".75"><title>{`Bloqueo activo hasta ${fecha(bloqueo.fin)}`}</title></rect>))}
      {segmentosDibujados.map(({ ruta, camino, recorrido, terminada, clave }) => {
        const coordenadas = camino.map(p => `${x(p.x)},${y(p.y)}`).join(' ');
        const resaltada = ruta.id === rutaSeleccionada || ruta.id === rutaEnFoco;
        return <g key={clave} style={{ '--ruta-color': colorDeRuta(ruta) }} className={`trazo-ruta ${terminada ? 'trazo-final' : ''} ${resaltada ? 'trazo-resaltado' : ''}`}>
          <polyline className={`trazo-visible ${recorrido ? 'trazo-recorrido' : 'trazo-pendiente'}`}
            points={coordenadas} fill="none" />
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
      {almacenes.map(almacen => <g key={almacen.id} className="mapa-almacen"
        transform={icono(almacen.ubicacion.x, almacen.ubicacion.y)} role="img"
        aria-label={`${almacen.id}. ${fichaDeAlmacen(almacen).lineas.join('. ')}`}
        onPointerEnter={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas, null, almacen.id); }}
        onPointerMove={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas, null, almacen.id); }}
        onPointerLeave={ocultarTip}>
        <DibujoAlmacen tipo={almacen.tipo} />
        <circle className={`mapa-stock mapa-stock-${estadoDeStock(stockDe(almacen), almacen.stockInicial)}`}
          cx={almacen.tipo === 'CENTRAL' ? 17.5 : 15} cy={almacen.tipo === 'CENTRAL' ? -17.5 : -15}
          r="4.5" pointerEvents="none" />
      </g>)}
      {salidas.map(punto => <g key={`salida-${punto.x}-${punto.y}`} className="mapa-salida"
        transform={fijo(punto.x, punto.y)}>
        <title>{`Salida de ruta desde el nodo (${punto.x}, ${punto.y})`}</title>
        <path d="M0 -10 V-25" /><circle cx="0" cy="-29" r="10" />
        <text x="0" y="-25" textAnchor="middle">S</text>
      </g>)}
      {gruposVehiculos.map(grupo => <g key={grupo[0].id}
        className={`mapa-unidad ${grupo.length > 1 ? 'grupo-vehiculos' : ''}`}
        transform={icono(grupo[0].ubicacion.x, grupo[0].ubicacion.y)}
        role={grupo.length > DETALLE_MAXIMO ? 'button' : 'img'}
        tabIndex={grupo.length > DETALLE_MAXIMO ? 0 : undefined}
        aria-label={`${fichaDeUnidades(grupo).titulo}. ${fichaDeUnidades(grupo).lineas.join('. ')}`}
        onPointerEnter={e => { const f = fichaDeUnidades(grupo); mostrarTip(e, f.titulo, f.lineas, grupo.length === 1 ? grupo[0].id : null); }}
        onPointerMove={e => { const f = fichaDeUnidades(grupo); mostrarTip(e, f.titulo, f.lineas, grupo.length === 1 ? grupo[0].id : null); }}
        onPointerLeave={ocultarTip}
        onPointerDown={e => { if (grupo.length > DETALLE_MAXIMO) e.stopPropagation(); }}
        onClick={e => { if (grupo.length > DETALLE_MAXIMO) fijarDetalle(e, grupo); }}
        onKeyDown={e => {
          if (grupo.length > DETALLE_MAXIMO && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            fijarDetalle(e, grupo);
          }
        }}>
        {/* Ícono de vehículo con la cantidad en un globito: no debe confundirse con una parada. */}
        <g ref={nodo => {
          if (nodo) {
            iconosVehiculos.current.set(grupo[0].id, nodo);
            nodo.setAttribute('transform', `rotate(${angulosMostrados.current.get(grupo[0].id) ?? 0})`);
          } else iconosVehiculos.current.delete(grupo[0].id);
        }}>
          <svg x={TIPOS_CENITALES.has(grupo[0].tipo) ? -22 : -13} y={TIPOS_CENITALES.has(grupo[0].tipo) ? -22 : -13}
            width={TIPOS_CENITALES.has(grupo[0].tipo) ? 44 : 26} height={TIPOS_CENITALES.has(grupo[0].tipo) ? 44 : 26} viewBox="0 0 32 32">
            {!TIPOS_CENITALES.has(grupo[0].tipo) && <circle cx="16" cy="16" r="15" fill="white" stroke="#c8dce0" strokeWidth="1.5" />}
            <DibujoVehiculo tipo={grupo[0].tipo} />
          </svg>
        </g>
        <circle className={`mapa-carga mapa-carga-${estadoDeGrupo(grupo)}`}
          cx={TIPOS_CENITALES.has(grupo[0].tipo) ? 11 : 9} cy={TIPOS_CENITALES.has(grupo[0].tipo) ? -17 : -9}
          r="4.5" pointerEvents="none" />
        {grupo.length > 1 && <>
          <circle className="grupo-contador" cx="12" cy="11" r="8" />
          <text x="12" y="14.5" textAnchor="middle">{grupo.length}</text>
        </>}
      </g>)}
    </svg>

    <div className="mapa-herramientas">
      <button type="button" onClick={() => acercar(1.5, null, true)} disabled={vista.z >= ZOOM_MAX} title="Acercar" aria-label="Acercar">+</button>
      <button type="button" onClick={() => acercar(1 / 1.5, null, true)} disabled={vista.z <= ZOOM_MIN} title="Alejar" aria-label="Alejar">−</button>
      <button type="button" className="ancho" onClick={() => { cancelarZoom(); aplicarVista(VISTA_INICIAL); }} title="Ver toda la ciudad">
        <svg className="mapa-todo-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
          <path d="M4 9V4h5M4 4l5 5M15 4h5v5M20 4l-5 5M4 15v5h5M4 20l5-5M15 20h5v-5M20 20l-5-5" />
        </svg>
        Todo
      </button>
      <button type="button" className={`ancho ${filtros || hayFiltro ? 'activo' : ''}`}
        aria-expanded={filtros} onClick={() => setFiltros(!filtros)}>
        <svg className="mapa-filtrar-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
          <path d="M3 5h18l-7 8v5l-4 2v-7L3 5Z" />
        </svg>
        Filtrar{hayFiltro ? ' ·' : ''}
      </button>
    </div>

    {filtros && <div className="mapa-filtros">
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
      <div className="filtro-pie">
        <span>{numero(vehiculos.length)} unidad(es) · {numero(puntos.length + adicionales.length + sinAsignar.length)} pedido(s) a la vista</span>
        <button type="button" disabled={!hayFiltro} onClick={() => { setFlota(''); setEstadoPedido(''); }}>Limpiar filtros</button>
      </div>
    </div>}

    <div className="mapa-abajo">
    {!modoOperacion ? <div className="mapa-reproductor">
      <div className="reproduccion-controles">
        {enVivo
          ? <button type="button" className="reproduccion-principal" disabled={fotogramas.length < 2}
              onClick={() => alCambiarFotograma(Math.max(0, fotogramas.length - 2))}>Historial</button>
          : <button type="button" className="reproduccion-principal" disabled={!hayReproduccion} onClick={alReproducir}>
              <span className="reproduccion-play" aria-hidden="true">{hayReproduccion && reproduciendo ? 'Ⅱ' : '▶'}</span>
              {!hayReproduccion ? 'Reproducir' : reproduciendo ? 'Pausar' : indiceFotograma >= fotogramas.length - 1 ? 'Repetir' : 'Reproducir'}
            </button>}
        <input type="range" min="0" max={Math.max(0, fotogramas.length - 1)}
          disabled={!hayReproduccion}
          value={enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma}
          style={{ '--progreso': `${Math.round(100 * (enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma) / Math.max(1, fotogramas.length - 1))}%` }}
          onChange={evento => alCambiarFotograma(Number(evento.target.value))}
          aria-label="Seleccionar instante de la reproducción" />
        <span className="reproduccion-contador" title={enVivo
          ? `${ejecucion?.estado === 'PAUSADA' ? 'En pausa' : 'En vivo'} · ${fecha(instante)} · ventana ${ejecucion?.ritmo?.ventana ?? 0} de ${ejecucion?.ritmo?.ventanas ?? '—'} · ${ejecucion?.ritmo ? textoRitmo(ejecucion.ritmo) : ''}`
          : !hayReproduccion ? 'Sin reproducción disponible'
            : `Reproducción ${indiceFotograma + 1}/${fotogramas.length} · ${fecha(fotograma?.reloj)}${finalizada && reproduciendo ? ' · cálculo terminado' : ''}`}>
          {enVivo ? `En vivo · ${ejecucion?.ritmo?.ventana ?? 0}/${ejecucion?.ritmo?.ventanas ?? '—'}`
            : hayReproduccion ? `Reproducción ${indiceFotograma + 1}/${fotogramas.length}` : 'Reproducción 0/0'}
        </span>
        <button type="button" className="reproduccion-flecha" aria-label="Retroceder un instante"
          disabled={indiceFotograma <= 0} onClick={() => alCambiarFotograma(indiceFotograma - 1)}>‹</button>
        <button type="button" className="reproduccion-flecha" aria-label="Avanzar un instante"
          disabled={indiceFotograma >= fotogramas.length - 1}
          onClick={() => alCambiarFotograma(indiceFotograma + 1)}>›</button>
      </div>
      <div className="reproduccion-tiempos">
        <div className="reproduccion-tiempo reproduccion-momento">
          <IconoUI nombre="calendario" size={22} />
          <span><small>Momento simulado</small><b>{hayReproduccion ? fecha(instante) : '—'}</b></span>
        </div>
        <div className="reproduccion-tiempo reproduccion-simulado">
          <IconoUI nombre="reloj" size={22} />
          <span><small>Tiempo simulado transcurrido</small><b>{hayReproduccion ? duracion(aMs(instante) - aMs(ejecucion?.inicio)) : '—'}</b></span>
        </div>
        <div className="reproduccion-tiempo reproduccion-real">
          <IconoUI nombre="reloj" size={22} />
          <span><small>Tiempo real transcurrido</small><b>{hayReproduccion ? duracion(transcurridoMs) : '—'}</b></span>
        </div>
        <label className="reproduccion-velocidad">Velocidad <select value={pasoMs} disabled={!velocidadHabilitada}
          onChange={evento => alCambiarPaso(Number(evento.target.value))}>
          <option value="800">Lenta</option><option value="300">Normal</option><option value="200">Rápida</option>
        </select></label>
      </div>
    </div> : <div className="mapa-reproductor">
      <div className="reproduccion-controles">
        {enVivo
          ? <button type="button" className="reproduccion-principal" disabled={fotogramas.length < 2}
              onClick={() => alCambiarFotograma(Math.max(0, fotogramas.length - 2))}>Historial</button>
          : <button type="button" className="reproduccion-principal" disabled={!hayReproduccion} onClick={alReproducir}>
              <span className="reproduccion-play" aria-hidden="true">{hayReproduccion && reproduciendo ? 'Ⅱ' : '▶'}</span>
              {!hayReproduccion ? 'Reproducir' : reproduciendo ? 'Pausar' : indiceFotograma >= fotogramas.length - 1 ? 'Repetir' : 'Reproducir'}
            </button>}
        <input type="range" min="0" max={Math.max(0, fotogramas.length - 1)}
          disabled={!hayReproduccion} value={enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma}
          style={{ '--progreso': `${Math.round(100 * (enVivo ? Math.max(0, fotogramas.length - 1) : indiceFotograma) / Math.max(1, fotogramas.length - 1))}%` }}
          onChange={evento => alCambiarFotograma(Number(evento.target.value))}
          aria-label="Seleccionar instante de la reproducción" />
        <span className="reproduccion-contador" title={contadorOperacion}>{contadorOperacion}</span>
        <button type="button" className="reproduccion-flecha" aria-label="Retroceder un instante"
          disabled={indiceFotograma <= 0} onClick={() => alCambiarFotograma(indiceFotograma - 1)}>‹</button>
        <button type="button" className="reproduccion-flecha" aria-label="Avanzar un instante"
          disabled={indiceFotograma >= fotogramas.length - 1}
          onClick={() => alCambiarFotograma(indiceFotograma + 1)}>›</button>
        {!enVivo && alVolverEnVivo && <button type="button" className="reproduccion-vivo" onClick={alVolverEnVivo}>En vivo</button>}
      </div>
      <div className="reproduccion-tiempos">
        <div className="reproduccion-tiempo reproduccion-momento">
          <IconoUI nombre="calendario" size={22} />
          <span><small>Fecha y hora de simulación</small><b>{hayReproduccion ? fecha(instante) : '—'}</b></span>
        </div>
        <div className="reproduccion-tiempo reproduccion-real">
          <IconoUI nombre="reloj" size={22} />
          <span><small>Duración</small><b>{hayReproduccion ? duracion(transcurridoMs) : '—'}</b></span>
        </div>
        <label className="reproduccion-velocidad">Velocidad <select value={pasoMs} disabled={!velocidadHabilitada}
          onChange={evento => alCambiarPaso(Number(evento.target.value))}>
          <option value="800">Lenta</option><option value="300">Normal</option><option value="200">Rápida</option>
        </select></label>
      </div>
    </div>}

    <div className={`mapa-pie ${pieAbierto ? '' : 'mapa-pie-cerrado'}`}>
      <button type="button" className="mapa-pie-tirador" aria-expanded={pieAbierto}
        onClick={() => setPieAbierto(!pieAbierto)}>
        Leyenda <i aria-hidden="true" />
      </button>
      {pieAbierto && <div className="mapa-pie-cuerpo">
        <div className="leyenda">
          <span><i className="punto punto-salida" /> S: salida</span>
          <span><i className="punto punto-llegada" /> Números: orden de entrega (aro: última)</span>
          <span><i className="punto punto-ruta" /> Rutas simultáneas por color</span>
          <span><i className="punto punto-alerta" /> Sin asignar</span>
          <span><i className="punto punto-bloqueo" /> Bloqueo activo ({bloqueosActivos.length})</span>
          <span className="leyenda-almacen"><DibujoAlmacen tipo="CENTRAL" leyenda /> Central</span>
          <span className="leyenda-almacen"><DibujoAlmacen tipo="INTERMEDIO" leyenda /> Intermedio</span>
          <IconoLeyenda tipo="AUTO" texto="Auto" />
          <IconoLeyenda tipo="MOTO" texto="Moto" />
          <IconoLeyenda tipo="BICICLETA" texto="Bicicleta" />
          <span><i className="punto punto-vehiculo" /> Ícono con número: varias unidades en el mismo nodo</span>
        </div>
      </div>}
    </div>
    </div>

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
          <span className={`mapa-tip-estado mapa-tip-stock-${fichaAlmacen.estado}`}>
            <i aria-hidden="true" />{fichaAlmacen.estadoTexto}
          </span>
        </div>
        <div className="mapa-tip-filas">
          <div className="mapa-tip-fila"><IconoUI nombre="caja" size={17} /><span>Stock actual</span><strong>{fichaAlmacen.stockActual}</strong></div>
          <div className="mapa-tip-fila"><span className="mapa-tip-calendario"><IconoUI nombre="calendario" size={17} /><IconoUI nombre="reloj" size={9} /></span><span>Último corte disponible</span><strong>{fichaAlmacen.ultimoCorte}</strong></div>
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

function DibujoAlmacen({ tipo, leyenda = false }) {
  const gradiente = useId();
  if (tipo === 'CENTRAL') return <svg x={leyenda ? undefined : -22.5}
    y={leyenda ? undefined : -24.5} width={leyenda ? 20 : 45} height={leyenda ? 20 : 49}
    viewBox="0 0 36 40" overflow="visible" aria-hidden="true" focusable="false">
    <defs><linearGradient id={gradiente} x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stopColor="#B667D0" /><stop offset="1" stopColor="#8E24AA" />
    </linearGradient></defs>
    <path d="M18 1 Q19 1 20 2 L34.5 17 Q36 19 34.5 21 L20 37 Q18 39 16 37 L1.5 21 Q0 19 1.5 17 L16 2 Q17 1 18 1Z"
      fill={`url(#${gradiente})`} />
    <path d="M6 19 18 9 30 19 V34 H6Z" fill="#8E24AA" />
    <path d="M5.5 19 18 8 30.5 19 M5.5 19v15 M30.5 19v15"
      fill="none" stroke="white" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    <text x="18" y="25.5" textAnchor="middle" fontFamily="Archivo, sans-serif" fontSize="9.5"
      fontWeight="900" fill="white">C</text>
    <rect x="10" y="27" width="16" height="8" rx="1.2" fill="white" />
    <rect x="12" y="30" width="9" height="4" rx=".6" fill="#6A1B9A" />
    <path d="M21 31h2l2 2v1h-4Z" fill="#8E24AA" />
    <circle cx="14.5" cy="34" r="1.15" fill="#6A1B9A" stroke="white" strokeWidth=".5" />
    <circle cx="23" cy="34" r="1.15" fill="#6A1B9A" stroke="white" strokeWidth=".5" />
  </svg>;
  return <svg x={leyenda ? undefined : -20} y={leyenda ? undefined : -20}
    width={leyenda ? 20 : 40} height={leyenda ? 20 : 40} viewBox="0 0 20 20"
    overflow="visible" aria-hidden="true" focusable="false">
    <path d="M2.7 10.5 10 5.2l7.3 5.3V18q0 .6-.6.6H3.3q-.6 0-.6-.6Z" fill="#9222B4" />
    <path d="M1.7 7.9 9.5 2.3q.5-.4 1 0l7.8 5.6" fill="none"
      stroke="#9222B4" strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M2.4 9.2 10 4l7.6 5.2" fill="none" stroke="white"
      strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M8.5 10.5h3M10 10.5v5.2M8.5 15.7h3" fill="none"
      stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}

const ETIQUETA_TIPO = {
  AUTO: 'Auto', MOTO: 'Moto', BICICLETA: 'Bicicleta', CAMION: 'Camión'
};

const ETIQUETA_ESTADO = {
  PLANIFICADO: 'planificado', EN_RUTA: 'en ruta', ENTREGADO: 'entregado', NO_ASIGNADO: 'sin asignar'
};
