import React, { useMemo, useRef, useState } from 'react';
import { ANCHO, ALTO, nodosBloqueados, tramosDeRuta } from '../mapa.js';
import { esFinal } from '../services/api.js';
import { fecha, numero } from '../services/formato.js';

/* Visualizador de la operación sobre la retícula del modelo: 70 km en X por 50 en Y,
   nodos cada kilómetro, origen (0,0) abajo a la izquierda. El eje Y se invierte al
   dibujar porque en SVG crece hacia abajo.

   El trazo de cada tramo lo calcula `mapa.js` con una búsqueda en anchura sobre la
   retícula: solo norte, sur, este y oeste, rodeando los nodos bloqueados vigentes en el
   reloj mostrado. Aun así es el plan vigente, no un registro GPS ni una entrega
   confirmada.

   Zoom, desplazamiento y filtros son locales a este navegador: cambiarlos no toca el
   estado de la simulación ni la vista de otro dispositivo (25.dis.gui.v2 seccion 8). */

const PASO = 10;
const MARGEN_IZQ = 34;
const MARGEN_INF = 26;

const COLOR = { AUTO: 'var(--auto)', MOTO: 'var(--moto)', BICICLETA: 'var(--bici)' };
const TIPOS = [['AUTO', 'Auto'], ['MOTO', 'Moto'], ['BICICLETA', 'Bici']];
const ESTADOS = [['plan', 'Planificados'], ['sin', 'Sin asignar']];

const x = v => Math.min(ANCHO, Math.max(0, v ?? 0)) * PASO;
const y = v => (ALTO - Math.min(ALTO, Math.max(0, v ?? 0))) * PASO;
const puntos = tramo => tramo.map(p => `${x(p.x)},${y(p.y)}`).join(' ');

export default function Mapa({ ejecucion, rutaSeleccionada, alSeleccionarRuta, mapaDatos, instante, children }) {
  const [zoom, setZoom] = useState(1);
  const [centro, setCentro] = useState({ x: ANCHO * PASO / 2, y: ALTO * PASO / 2 });
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);
  const [tiposVisibles, setTiposVisibles] = useState(TIPOS.map(t => t[0]));
  const [estadosVisibles, setEstadosVisibles] = useState(['plan', 'sin']);
  const [cursor, setCursor] = useState(null);
  const [rutaEnFoco, setRutaEnFoco] = useState(null);
  const arrastre = useRef(null);

  const rutas = ejecucion?.rutas || [];
  const vehiculos = ejecucion?.vehiculos || [];
  const sinAsignar = ejecucion?.noAsignados || [];
  const almacenes = mapaDatos?.almacenes || [];
  const finalizada = ejecucion && esFinal(ejecucion.estado);
  const vacio = rutas.length === 0 && vehiculos.length === 0 && sinAsignar.length === 0 && !mapaDatos;

  const rutasVisibles = useMemo(
    () => rutas.filter(r => tiposVisibles.includes(r.tipoVehiculo)), [rutas, tiposVisibles]);
  const vehiculosVisibles = useMemo(
    () => vehiculos.filter(v => tiposVisibles.includes(v.tipo)), [vehiculos, tiposVisibles]);

  /* Un bloqueo solo cuenta si su vigencia abarca el instante mostrado. */
  const bloqueosActivos = useMemo(
    () => (mapaDatos?.bloqueos || []).filter(b => instante && b.inicio <= instante && b.fin > instante),
    [mapaDatos, instante]);
  const bloqueados = useMemo(() => nodosBloqueados(bloqueosActivos), [bloqueosActivos]);
  const trazos = useMemo(
    () => rutasVisibles.map(ruta => ({ ruta, tramos: tramosDeRuta(ruta, bloqueados) })),
    [rutasVisibles, bloqueados]);

  const salidas = useMemo(() => [...new Map(rutasVisibles.filter(r => r.origen)
    .map(r => [`${r.origen.x},${r.origen.y}`, r.origen])).values()], [rutasVisibles]);
  const llegadas = useMemo(() => rutasVisibles.filter(r => r.paradas.length > 0)
    .map(r => ({ id: r.id, vehiculoId: r.vehiculoId, punto: r.paradas.at(-1).destino })), [rutasVisibles]);

  const anchoVista = (ANCHO * PASO + MARGEN_IZQ + 16) / zoom;
  const altoVista = (ALTO * PASO + MARGEN_INF + 16) / zoom;
  const vx = centro.x - anchoVista / 2;
  const vy = centro.y - altoVista / 2;

  function alternar(lista, asignar, valor) {
    asignar(lista.includes(valor) ? lista.filter(v => v !== valor) : [...lista, valor]);
  }

  function aNodo(evento) {
    const caja = evento.currentTarget.getBoundingClientRect();
    return {
      x: vx + ((evento.clientX - caja.left) / caja.width) * anchoVista,
      y: vy + ((evento.clientY - caja.top) / caja.height) * altoVista
    };
  }

  function mover(evento) {
    const punto = aNodo(evento);
    if (arrastre.current) {
      setCentro({
        x: arrastre.current.centro.x - (punto.x - arrastre.current.punto.x),
        y: arrastre.current.centro.y - (punto.y - arrastre.current.punto.y)
      });
      return;
    }
    const nx = Math.round(punto.x / PASO);
    const ny = Math.round(ALTO - punto.y / PASO);
    setCursor(nx < 0 || nx > ANCHO || ny < 0 || ny > ALTO ? null : [nx, ny]);
  }

  function restablecer() {
    setTiposVisibles(TIPOS.map(t => t[0]));
    setEstadosVisibles(['plan', 'sin']);
    setZoom(1);
    setCentro({ x: ANCHO * PASO / 2, y: ALTO * PASO / 2 });
  }

  return <div className="mapa-fondo">
    <div className="mapa-lienzo">
      <svg className="mapa" viewBox={`${vx} ${vy} ${anchoVista} ${altoVista}`}
        preserveAspectRatio="xMidYMid meet" role="img"
        aria-label="Mapa de la operación: rutas, unidades, almacenes, bloqueos y pedidos sobre la retícula de la ciudad"
        style={{ cursor: arrastre.current ? 'grabbing' : 'grab', touchAction: 'none' }}
        onMouseDown={e => { arrastre.current = { punto: aNodo(e), centro }; }}
        onMouseUp={() => { arrastre.current = null; }}
        onMouseMove={mover}
        onMouseLeave={() => { arrastre.current = null; setCursor(null); }}>

        <rect x={-MARGEN_IZQ} y={-16} width={ANCHO * PASO + MARGEN_IZQ + 32}
          height={ALTO * PASO + MARGEN_INF + 32} fill="#f2eeed" />
        <rect x="0" y="0" width={ANCHO * PASO} height={ALTO * PASO} fill="#fdfbfb" />

        {Array.from({ length: ANCHO + 1 }, (_, i) =>
          <line key={`v${i}`} x1={i * PASO} x2={i * PASO} y1="0" y2={ALTO * PASO}
            className={i % 10 === 0 ? 'reticula-fuerte' : 'reticula'} />)}
        {Array.from({ length: ALTO + 1 }, (_, i) =>
          <line key={`h${i}`} x1="0" x2={ANCHO * PASO} y1={i * PASO} y2={i * PASO}
            className={i % 10 === 0 ? 'reticula-fuerte' : 'reticula'} />)}

        {Array.from({ length: ANCHO / 10 + 1 }, (_, i) =>
          <text key={`ex${i}`} className="eje" x={i * 10 * PASO} y={ALTO * PASO + 16}
            textAnchor="middle">{i * 10}</text>)}
        {Array.from({ length: ALTO / 10 + 1 }, (_, i) =>
          <text key={`ey${i}`} className="eje" x={-9} y={y(i * 10) + 3}
            textAnchor="end">{i * 10}</text>)}

        {bloqueosActivos.flatMap((bloqueo, indice) => (bloqueo.nodos || []).map((nodo, n) =>
          <rect key={`b${indice}-${n}`} className="nodo-bloqueado"
            x={x(nodo.x) - 4} y={y(nodo.y) - 4} width="8" height="8">
            <title>{`Nodo bloqueado hasta ${fecha(bloqueo.fin)}`}</title>
          </rect>))}

        {trazos.flatMap(({ ruta, tramos }, indice) => tramos.map((tramo, n) => {
          const resaltada = ruta.id === rutaSeleccionada || ruta.id === rutaEnFoco;
          const atenuada = rutaSeleccionada && ruta.id !== rutaSeleccionada;
          return <g key={`${ruta.id}-${indice}-${n}`}
            className={`trazo ${finalizada ? 'trazo-final' : ''} ${resaltada ? 'trazo-resaltado' : ''} ${atenuada ? 'trazo-atenuado' : ''}`}
            style={{ '--c': COLOR[ruta.tipoVehiculo] || 'var(--ink)' }}>
            <polyline className="trazo-visible" points={puntos(tramo)} fill="none" />
            <polyline className="trazo-interaccion" points={puntos(tramo)} fill="none"
              tabIndex="0" role="button"
              aria-label={`Resaltar la ruta ${ruta.id} del vehículo ${ruta.vehiculoId}`}
              onClick={() => alSeleccionarRuta?.(ruta.id === rutaSeleccionada ? null : ruta.id)}
              onMouseEnter={() => setRutaEnFoco(ruta.id)} onMouseLeave={() => setRutaEnFoco(null)}
              onFocus={() => setRutaEnFoco(ruta.id)} onBlur={() => setRutaEnFoco(null)}>
              <title>{`${ruta.id} · ${ruta.vehiculoId} · ${ruta.paradas.length} parada(s)`}</title>
            </polyline>
          </g>;
        }))}

        {estadosVisibles.includes('plan') && rutasVisibles.flatMap(ruta =>
          ruta.paradas.map((parada, indice) =>
            <circle key={`${ruta.id}-p${indice}`} cx={x(parada.destino?.x)} cy={y(parada.destino?.y)}
              r="4" fill={COLOR[ruta.tipoVehiculo] || 'var(--ink)'} stroke="#fff" strokeWidth="1.4"
              opacity={rutaSeleccionada && ruta.id !== rutaSeleccionada ? .3 : 1}>
              <title>{`${parada.id} · ${parada.cantidad} unidades · ruta ${ruta.id}`}</title>
            </circle>))}

        {estadosVisibles.includes('sin') && sinAsignar.map((pedido, indice) =>
          <g key={`sin${indice}`} transform={`translate(${x(pedido.destino?.x)}, ${y(pedido.destino?.y)})`}>
            <title>{`${pedido.id} · sin asignar · ${pedido.motivo || 'sin motivo informado'}`}</title>
            <circle r="6" fill="var(--red)" stroke="#fff" strokeWidth="1.5" />
            <path d="M-2.6 -2.6 L2.6 2.6 M2.6 -2.6 L-2.6 2.6" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" />
          </g>)}

        {almacenes.map(almacen => {
          const central = almacen.tipo === 'CENTRAL';
          return <g key={almacen.id} transform={`translate(${x(almacen.ubicacion.x)}, ${y(almacen.ubicacion.y)})`}>
            <title>
              {`${almacen.id} · ${central ? 'central' : 'intermedio'} · nodo (${almacen.ubicacion.x}, ${almacen.ubicacion.y})`
                + (central ? ' · abastecimiento principal' : ` · stock inicial ${numero(almacen.stockInicial)}`)}
            </title>
            {central
              ? <path d="M0 -8 8 0 0 8 -8 0Z" fill="var(--ink)" stroke="#fff" strokeWidth="1.8" />
              : <rect x="-6" y="-6" width="12" height="12" fill="#fff" stroke="var(--ink)" strokeWidth="2.2" />}
            <text className="eje" x="-10" y="-8" textAnchor="end"
              style={{ fill: 'var(--ink)', fontSize: 9.5 }}>
              {almacen.id} ({almacen.ubicacion.x}, {almacen.ubicacion.y})
            </text>
          </g>;
        })}

        {vehiculosVisibles.map(vehiculo => {
          const fuera = vehiculo.estado === 'AVERIADO' || vehiculo.estado === 'EN_MANTENIMIENTO';
          return <g key={vehiculo.id}
            transform={`translate(${x(vehiculo.ubicacion?.x)}, ${y(vehiculo.ubicacion?.y)})`}>
            <title>{`${vehiculo.id} · ${vehiculo.tipo} · ${vehiculo.estado} · ${vehiculo.cargaAbordo}/${vehiculo.capacidad}`}</title>
            <circle r="7" fill={fuera ? 'var(--red)' : COLOR[vehiculo.tipo] || 'var(--ink)'}
              stroke="#fff" strokeWidth="2" />
            {fuera && <path d="M-2.8 -2.8 L2.8 2.8 M2.8 -2.8 L-2.8 2.8" stroke="#fff"
              strokeWidth="1.8" strokeLinecap="round" />}
          </g>;
        })}

        {salidas.map(punto => <g key={`salida-${punto.x}-${punto.y}`} className="marcador-salida"
          transform={`translate(${x(punto.x)}, ${y(punto.y)})`}>
          <title>{`Salida de ruta desde el nodo (${punto.x}, ${punto.y})`}</title>
          <path d="M0 -8 V-13" /><circle cx="0" cy="-19" r="7" />
          <text x="0" y="-16" textAnchor="middle">S</text>
        </g>)}

        {llegadas.map((llegada, indice) => <g key={`llegada-${llegada.id}-${indice}`}
          className={`marcador-llegada ${llegada.id === rutaSeleccionada || llegada.id === rutaEnFoco ? 'marcador-resaltado' : ''}`}
          transform={`translate(${x(llegada.punto.x)}, ${y(llegada.punto.y)})`}
          onMouseEnter={() => setRutaEnFoco(llegada.id)} onMouseLeave={() => setRutaEnFoco(null)}>
          <title>{`Última llegada de ${llegada.id} · ${llegada.vehiculoId} · nodo (${llegada.punto.x}, ${llegada.punto.y})`}</title>
          <circle r="7" /><text x="0" y="3" textAnchor="middle">F</text>
        </g>)}
      </svg>
    </div>

    <div className="tools">
      {cursor && <span className="coord">nodo {cursor[0]}, {cursor[1]}</span>}
      <button type="button" title="Acercar" onClick={() => setZoom(z => Math.min(6, z * 1.4))}>+</button>
      <button type="button" title="Alejar" onClick={() => setZoom(z => Math.max(1, z / 1.4))}>−</button>
      <button type="button" className="ancho" title="Ver toda la ciudad" onClick={restablecer}>TODO</button>
      <button type="button" className="ancho" data-on={filtrosAbiertos || undefined}
        aria-expanded={filtrosAbiertos} onClick={() => setFiltrosAbiertos(a => !a)}>FILTROS</button>
    </div>

    {filtrosAbiertos && <div className="panel-filtros">
      <div className="filtro-grupo">
        <span>Flota</span>
        <div className="filtro-opciones">
          {TIPOS.map(([clave, texto]) => <button key={clave} type="button"
            data-on={tiposVisibles.includes(clave) || undefined}
            onClick={() => alternar(tiposVisibles, setTiposVisibles, clave)}>{texto}</button>)}
        </div>
      </div>
      <div className="filtro-grupo">
        <span>Pedidos</span>
        <div className="filtro-opciones">
          {ESTADOS.map(([clave, texto]) => <button key={clave} type="button"
            data-on={estadosVisibles.includes(clave) || undefined}
            onClick={() => alternar(estadosVisibles, setEstadosVisibles, clave)}>{texto}</button>)}
        </div>
      </div>
      <button type="button" className="btn" style={{ width: '100%' }}
        onClick={restablecer}>Limpiar filtros</button>
    </div>}

    <div className="leyenda-caja">
      <span><i className="ln" style={{ background: 'var(--auto)' }} />AUTO</span>
      <span><i className="ln" style={{ background: 'var(--moto)' }} />MOTO</span>
      <span><i className="ln" style={{ background: 'var(--bici)' }} />BICICLETA</span>
      <span><i className="sq" />ALMACÉN</span>
      <span><i className="dot" style={{ background: 'var(--auto)' }} />PLANIFICADO</span>
      <span><i className="dot" style={{ background: 'var(--red)' }} />SIN ASIGNAR</span>
      <span><i className="sq" style={{ background: 'var(--red)' }} />BLOQUEO ({bloqueosActivos.length})</span>
      <span>S SALIDA · F ÚLTIMA LLEGADA</span>
    </div>

    {vacio && <div className="mapa-vacio">
      <b>Sin operación cargada</b>
      <span>Elige un periodo y una fecha en el panel de configuración, e inicia la corrida para ver el mapa.</span>
    </div>}

    {children}
  </div>;
}
