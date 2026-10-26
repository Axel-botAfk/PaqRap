import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ANCHO, ALTO, caminoEnGrilla, nodosBloqueados, posicionEnCamino, tramosDeRuta,
  prepararTrayectos, posicionEnTrayecto, trazosConRecorrido } from '../mapa.js';
import { aMs } from '../reloj.js';
import { esFinal } from '../services/api.js';
import { fecha, numero, duracion, colorDeRuta, textoRitmo, estadoVehiculo } from '../formato.js';
import DibujoVehiculo, { IconoLeyenda } from './DibujoVehiculo.jsx';
import FondoUrbano from './FondoUrbano.jsx';
import IconoUI from './IconoUI.jsx';
import { estadoDeStock, ETIQUETAS_STOCK } from '../stock.js';

const LISTA_VACIA = [];

// La retícula de 70 × 50 km se dibuja a 10 unidades por km.
const LIENZO_ANCHO = ANCHO * 10;
const LIENZO_ALTO = ALTO * 10;
const ZOOM_MIN = 1;
const ZOOM_MAX = 8;
const DURACION_ZOOM_MS = 250;
const VISTA_INICIAL = { cx: LIENZO_ANCHO / 2, cy: LIENZO_ALTO / 2, z: 1 };

const TIPOS_FLOTA = [['AUTO', 'Auto'], ['MOTO', 'Moto'], ['BICICLETA', 'Bicicleta'], ['CAMION', 'Camión']];
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
  alReproducir, pasoMs, alCambiarPaso, enVivo = false, trayectosEnVivo = null,
  alVolverEnVivo = null, noAsignadosDelInstante = null, estadosPedido = null,
  modoOperacion = false, transcurridoMs = null, stockDelInstante = false, ahoraReal = null }) {
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
  const [tip, setTip] = useState(null);
  const arrastre = useRef(null);
  const animacionZoom = useRef(null);
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
  const posicionesObservadas = fotograma?.vehiculos ?? ejecucion?.vehiculos ?? LISTA_VACIA;
  const [vehiculosAnimados, setVehiculosAnimados] = useState(posicionesObservadas);
  const anteriores = useRef(null);
  const vehiculosObservados = fotograma ? vehiculosAnimados : posicionesObservadas;
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
  const instanteMs = instante ? aMs(instante) : NaN;
  const vehiculosTodos = trayectosPreparados ? posicionesObservadas.map(vehiculo => {
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

  const trazosTodos = useMemo(() => trayectosPreparados
    ? trazosConRecorrido(rutas, trayectosPreparados, bloqueados)
    : rutas.map(ruta => ({ ruta, tramos: tramosDeRuta(ruta, bloqueados) })), [rutas, bloqueados, trayectosPreparados]);

  // ── filtros del mapa ───────────────────────────────────────────────────────────────
  const estadoDe = pedido => estadosPedido?.get(pedido.id) || 'PLANIFICADO';
  const enFlota = tipo => !flota || tipo === flota;
  const enEstado = valor => !estadoPedido || valor === estadoPedido;

  const vehiculos = vehiculosTodos.filter(v => enFlota(v.tipo));
  const trazos = estadoPedido === 'NO_ASIGNADO' ? LISTA_VACIA
    : trazosTodos.filter(({ ruta }) => enFlota(ruta.tipoVehiculo));
  const puntos = rutas.filter(ruta => enFlota(ruta.tipoVehiculo))
    .flatMap(ruta => ruta.paradas.map((parada, indice) => ({
      ruta, parada, estado: estadoDe(parada), orden: indice + 1,
      ultima: indice === ruta.paradas.length - 1
    })))
    .filter(punto => enEstado(punto.estado));
  const sinAsignar = enEstado('NO_ASIGNADO') && !flota ? noAsignados : LISTA_VACIA;
  const hayFiltro = !!flota || !!estadoPedido;

  const finalizada = ejecucion && esFinal(ejecucion.estado);
  const hayReproduccion = fotogramas.length > 0 || enVivo;
  const gruposVehiculos = [...vehiculos.reduce((grupos, vehiculo) => {
    if (!vehiculo.ubicacion) return grupos;
    const clave = `${vehiculo.ubicacion.x},${vehiculo.ubicacion.y}`;
    if (!grupos.has(clave)) grupos.set(clave, []);
    grupos.get(clave).push(vehiculo);
    return grupos;
  }, new Map()).values()];
  const salidas = [...new Map(rutas.filter(ruta => ruta.origen && enFlota(ruta.tipoVehiculo))
    .map(ruta => [`${ruta.origen.x},${ruta.origen.y}`, ruta.origen])).values()];
  const vacio = rutas.length === 0 && vehiculosTodos.length === 0 && noAsignados.length === 0 && !mapaDatos;

  const x = valor => limitar((valor ?? 0) * 10, 0, LIENZO_ANCHO);
  const y = valor => limitar((ALTO - (valor ?? 0)) * 10, 0, LIENZO_ALTO);
  // Los símbolos conservan su tamaño en pantalla: al acercar crece la ciudad, no los iconos.
  const escala = 1 / vista.z;
  const fijo = (px, py) => `translate(${x(px)},${y(py)}) scale(${escala})`;

  // Ficha al pasar el cursor: el <title> nativo tarda en salir y no admite varias líneas.
  function mostrarTip(evento, titulo, lineas) {
    const caja = lienzoRef.current?.getBoundingClientRect();
    if (!caja) return;
    setTip({ titulo, lineas, x: evento.clientX - caja.left, y: evento.clientY - caja.top });
  }
  const ocultarTip = () => setTip(null);

  function fichaDeAlmacen(almacen) {
    const stock = stockDe(almacen);
    const inicial = almacen.stockInicial > 0 ? almacen.stockInicial : null;
    const estado = estadoDeStock(stock, almacen.stockInicial);
    const lineas = [`Nodo (${almacen.ubicacion.x}, ${almacen.ubicacion.y})`, ETIQUETAS_STOCK[estado]];
    if (stock?.ilimitado) lineas.push('Stock ilimitadas');
    else if (stock && Number.isFinite(stock.disponible)) {
      lineas.push(`Existencias ${numero(stock.disponible)}${inicial ? ` / ${numero(inicial)} iniciales` : ''}`);
      if (inventario?.reloj) lineas.push(`Último corte disponible: ${fecha(inventario.reloj)}`);
    }
    return { titulo: `${almacen.id} · ${almacen.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}`, lineas };
  }

  const DETALLE_MAXIMO = 6;
  function fichaDeUnidades(grupo) {
    if (grupo.length === 1) {
      const v = grupo[0];
      return { titulo: `${v.id} · ${ETIQUETA_TIPO[v.tipo] || v.tipo}`,
        lineas: [`Carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)}`, estadoVehiculo(v.estado)] };
    }
    const titulo = `${grupo.length} unidades en (${grupo[0].ubicacion.x}, ${grupo[0].ubicacion.y})`;
    if (grupo.length <= DETALLE_MAXIMO) {
      return { titulo, lineas: grupo.map(v =>
        `${v.id} · carga ${numero(v.cargaAbordo)} / ${numero(v.capacidad)} · ${estadoVehiculo(v.estado)}`) };
    }
    // Aparcadas en el almacén pueden ser decenas: se resume por tipo en vez de listarlas.
    const porTipo = new Map();
    for (const v of grupo) {
      const actual = porTipo.get(v.tipo) || { n: 0, carga: 0, capacidad: 0 };
      porTipo.set(v.tipo, { n: actual.n + 1, carga: actual.carga + v.cargaAbordo,
        capacidad: actual.capacidad + v.capacidad });
    }
    return { titulo, lineas: [...porTipo].map(([tipo, t]) =>
      `${t.n} × ${ETIQUETA_TIPO[tipo] || tipo} · carga ${numero(t.carga)} / ${numero(t.capacidad)}`) };
  }

  return <div className="mapa-lienzo" ref={lienzoRef}>
    <div className="mapa-reloj-actual">
      <time dateTime={ahoraReal ? new Date(ahoraReal).toISOString() : undefined}>
        Ahora: {ahoraReal ? fecha(new Date(ahoraReal)) : '—'}
      </time>
      {modoOperacion && ejecucion && <span>Real transcurrido: {duracion(transcurridoMs)}</span>}
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
      {almacenes.map(almacen => <g key={almacen.id} className="mapa-almacen"
        transform={fijo(almacen.ubicacion.x, almacen.ubicacion.y)} role="img"
        aria-label={`${almacen.id}. ${fichaDeAlmacen(almacen).lineas.join('. ')}`}
        onPointerEnter={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas); }}
        onPointerMove={e => { const f = fichaDeAlmacen(almacen); mostrarTip(e, f.titulo, f.lineas); }}
        onPointerLeave={ocultarTip}>
        {almacen.tipo === 'CENTRAL' ? <path d="M0 -11 11 0 0 11 -11 0Z" fill="#733e91" stroke="white" strokeWidth="2" />
          : <rect x="-9" y="-9" width="18" height="18" rx="2" fill="#a366ae" stroke="white" strokeWidth="2" />}
        <text x="0" y="4" textAnchor="middle" fontSize="10" fontWeight="800" fill="white">{almacen.tipo === 'CENTRAL' ? 'C' : 'I'}</text>
        {modoOperacion && <circle className={`mapa-stock mapa-stock-${estadoDeStock(stockDe(almacen), almacen.stockInicial)}`}
          cx="9" cy="-9" r="4.5" pointerEvents="none" />}
      </g>)}
      {gruposVehiculos.map(grupo => <g key={grupo[0].id}
        className={`mapa-unidad ${grupo.length > 1 ? 'grupo-vehiculos' : ''}`}
        transform={fijo(grupo[0].ubicacion.x, grupo[0].ubicacion.y)} role="img"
        aria-label={fichaDeUnidades(grupo).lineas.join('. ')}
        onPointerEnter={e => { const f = fichaDeUnidades(grupo); mostrarTip(e, f.titulo, f.lineas); }}
        onPointerMove={e => { const f = fichaDeUnidades(grupo); mostrarTip(e, f.titulo, f.lineas); }}
        onPointerLeave={ocultarTip}>
        {/* Ícono de vehículo con la cantidad en un globito: no debe confundirse con una parada. */}
        <svg x="-13" y="-13" width="26" height="26" viewBox="0 0 32 32">
          <circle cx="16" cy="16" r="15" fill="white" stroke="#c8dce0" strokeWidth="1.5" />
          <DibujoVehiculo tipo={grupo[0].tipo} />
        </svg>
        {grupo.length > 1 && <>
          <circle className="grupo-contador" cx="12" cy="-11" r="8" />
          <text x="12" y="-7.5" textAnchor="middle">{grupo.length}</text>
        </>}
      </g>)}
      {salidas.map(punto => <g key={`salida-${punto.x}-${punto.y}`} className="mapa-salida"
        transform={fijo(punto.x, punto.y)}>
        <title>{`Salida de ruta desde el nodo (${punto.x}, ${punto.y})`}</title>
        <path d="M0 -10 V-17" /><circle cx="0" cy="-21" r="10" />
        <text x="0" y="-17" textAnchor="middle">S</text>
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
        <span>{numero(vehiculos.length)} unidad(es) · {numero(puntos.length + sinAsignar.length)} pedido(s) a la vista</span>
        <button type="button" disabled={!hayFiltro} onClick={() => { setFlota(''); setEstadoPedido(''); }}>Limpiar filtros</button>
      </div>
    </div>}

    <div className="mapa-abajo">
    {modoOperacion ? <div className="mapa-reproductor">
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
        {!enVivo && alVolverEnVivo && <button type="button" className="reproduccion-vivo"
          onClick={alVolverEnVivo}>En vivo</button>}
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
        {!enVivo && <label className="reproduccion-velocidad">Velocidad <select value={pasoMs} disabled={!hayReproduccion}
          onChange={evento => alCambiarPaso(Number(evento.target.value))}>
          <option value="800">Lenta</option><option value="450">Normal</option><option value="200">Rápida</option>
        </select></label>}
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
        <span className="reproduccion-contador" title={enVivo
          ? `${ejecucion?.estado === 'PAUSADA' ? 'En pausa' : 'En vivo'} · ${fecha(instante)} · ventana ${ejecucion?.ritmo?.ventana ?? 0} de ${ejecucion?.ritmo?.ventanas ?? '—'} · ${ejecucion?.ritmo ? textoRitmo(ejecucion.ritmo) : ''}`
          : !hayReproduccion ? 'Sin reproducción disponible'
            : `Reproducción ${indiceFotograma + 1}/${fotogramas.length} · ${fecha(fotograma?.reloj)}${finalizada && reproduciendo ? ' · cálculo terminado' : ''}`}>
          {enVivo
            ? `${ejecucion?.estado === 'PAUSADA' ? 'En pausa' : 'En vivo'} · ${fecha(instante)} · ventana ${ejecucion?.ritmo?.ventana ?? 0} de ${ejecucion?.ritmo?.ventanas ?? '—'} · ${ejecucion?.ritmo ? textoRitmo(ejecucion.ritmo) : ''}`
            : hayReproduccion ? `Reproducción ${indiceFotograma + 1}/${fotogramas.length} · ${fecha(fotograma?.reloj)}${finalizada && reproduciendo ? ' · cálculo terminado' : ''}` : 'Reproducción 0/0'}
        </span>
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
        {!enVivo && <label className="reproduccion-velocidad">Velocidad <select value={pasoMs} disabled={!hayReproduccion}
          onChange={evento => alCambiarPaso(Number(evento.target.value))}>
          <option value="800">Lenta</option><option value="450">Normal</option><option value="200">Rápida</option>
        </select></label>}
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
          <span><i className="punto punto-central" /> Central</span>
          <span><i className="punto punto-intermedio" /> Intermedio</span>
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
    {tip && <div className="mapa-tip" style={{ left: tip.x, top: tip.y }} role="tooltip">
      <b>{tip.titulo}</b>
      {tip.lineas.map(linea => <span key={linea}>{linea}</span>)}
    </div>}

    {vacio && <div className="mapa-cartel"><b>Sin operación cargada</b>
      <span>Elige un periodo en el panel de la derecha e inicia una corrida para ver el mapa.</span></div>}
  </div>;
}

const ETIQUETA_TIPO = {
  AUTO: 'Auto', MOTO: 'Moto', BICICLETA: 'Bicicleta', CAMION: 'Camión'
};

const ETIQUETA_ESTADO = {
  PLANIFICADO: 'planificado', EN_RUTA: 'en ruta', ENTREGADO: 'entregado', NO_ASIGNADO: 'sin asignar'
};
