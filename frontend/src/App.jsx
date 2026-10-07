import React, { useEffect, useState } from 'react';
import Simulacion from './pages/Simulacion.jsx';
import OperacionDiaria from './pages/OperacionDiaria.jsx';
import Pedidos from './pages/Pedidos.jsx';
import Averias from './pages/Averias.jsx';
import Mapa from './components/Mapa.jsx';
import Chips from './components/Kpis.jsx';
import ListaRutas from './components/ListaRutas.jsx';
import DetalleRuta from './components/DetalleRuta.jsx';
import { AvisoError, BannerAveria, ModalColapso } from './components/Avisos.jsx';
import { api, esFinal } from './services/api.js';
import useEjecucion from './services/useEjecucion.js';
import { fecha } from './services/formato.js';

/* Armazón de la aplicación, con la estructura del prototipo HTML v6: el mapa ocupa toda
   la pantalla y encima flotan la barra superior, la franja de indicadores, el menú lateral
   y las tarjetas de configuración y de rutas. Pedidos y Averías se dibujan como una hoja
   que cubre el mapa.

   Una ejecución se identifica por su UUID y viaja en la URL, de modo que la misma corrida
   se puede abrir en otro dispositivo pegando el enlace. El zoom, el desplazamiento y los
   filtros del mapa son locales a cada navegador. */

const PANTALLAS = [
  ['pedidos', 'Pedidos'],
  ['averias', 'Averías de la flota'],
  ['operacion', 'Operación diaria'],
  ['simulacion', 'Simulación']
];

const TITULOS = Object.fromEntries(PANTALLAS);

const ESTADOS = {
  PENDIENTE: 'En espera', EN_CURSO: 'En vivo', TERMINADA: 'Terminada',
  COLAPSADA: 'Colapso detectado', FALLIDA: 'Fallida', CANCELADA: 'Cancelada'
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function idDeLaUrl() {
  const valor = new URLSearchParams(location.search).get('ejecucion');
  return UUID.test(valor || '') ? valor : null;
}

export default function App() {
  const [pantalla, setPantalla] = useState('simulacion');
  const [id, setId] = useState(idDeLaUrl);
  const [periodos, setPeriodos] = useState([]);
  const [detallePeriodo, setDetallePeriodo] = useState(null);
  const [mapaDatos, setMapaDatos] = useState(null);
  const [fuente, setFuente] = useState('ARCHIVOS');
  const [rutaSeleccionada, setRutaSeleccionada] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const [colapsoVisto, setColapsoVisto] = useState(false);
  /* En pantallas angostas el menú se superpone al contenido, así que arranca plegado. */
  const [menuAbierto, setMenuAbierto] = useState(() => window.innerWidth > 920);
  const [cfgAbierta, setCfgAbierta] = useState(true);

  const [config, setConfig] = useState({
    escenario: 'OPERACION_DIARIA', algoritmo: 'GRASP',
    periodo: '', dia: '', hora: '00:00', horizonte: 30
  });

  const { ejecucion, setEjecucion, error, setError, enVivo, desactualizado } = useEjecucion(id);

  useEffect(() => {
    let activo = true;
    /* La salud dice si el backend lee de MySQL o de los archivos del curso. */
    api.salud().then(info => { if (activo) setFuente(info.fuente || 'ARCHIVOS'); }).catch(() => {});
    api.periodos()
      .then(lista => { if (activo) setPeriodos(lista); })
      .catch(e => { if (activo) setError(`No se pudo cargar el catálogo de datos: ${e.message}`); });
    return () => { activo = false; };
  }, [setError]);

  useEffect(() => {
    if (!config.periodo) { setDetallePeriodo(null); setMapaDatos(null); return undefined; }
    let activo = true;
    api.periodo(config.periodo)
      .then(info => {
        if (!activo) return;
        setDetallePeriodo(info);
        /* Si todavía no hay fecha elegida, se parte de la primera con pedidos. */
        setConfig(previo => previo.dia ? previo : { ...previo, dia: info.primeraFechaPedido || '' });
      })
      .catch(e => { if (activo) { setDetallePeriodo(null); setError(e.message); } });
    api.mapa(config.periodo)
      .then(info => { if (activo) setMapaDatos(info); })
      .catch(e => { if (activo) { setMapaDatos(null); setError(e.message); } });
    return () => { activo = false; };
  }, [config.periodo, setError]);

  /* Al recuperar una corrida por enlace, el formulario se rellena con lo que ya corrió. */
  useEffect(() => {
    if (!ejecucion?.inicio) return;
    setConfig(previo => previo.dia ? previo : {
      ...previo,
      escenario: ejecucion.escenario,
      algoritmo: ejecucion.algoritmo,
      periodo: ejecucion.inicio.slice(0, 4) + ejecucion.inicio.slice(5, 7),
      dia: ejecucion.inicio.slice(0, 10),
      hora: ejecucion.inicio.slice(11, 16)
    });
  }, [ejecucion?.inicio, ejecucion?.escenario, ejecucion?.algoritmo]);

  useEffect(() => { setColapsoVisto(false); }, [id]);

  function cambiarConfig(cambios) {
    setConfig(previo => ({ ...previo, ...cambios }));
    setError('');
  }

  async function iniciar(evento) {
    evento.preventDefault();
    setError('');
    const { periodo, dia, hora, escenario, algoritmo, horizonte } = config;
    if (!periodo || !dia || dia.replaceAll('-', '').slice(0, 6) !== periodo) {
      setError('La fecha inicial debe pertenecer al periodo de datos elegido.');
      return;
    }
    setOcupado(true);
    try {
      const nueva = await api.iniciar({
        escenario, algoritmo, inicio: `${dia}T${hora}:00`,
        ...(escenario === 'COLAPSO' ? { horizonteDias: Number(horizonte) } : {})
      });
      setRutaSeleccionada(null);
      setEjecucion(nueva);
      setId(nueva.id);
      history.replaceState(null, '', `?ejecucion=${encodeURIComponent(nueva.id)}`);
    } catch (e) {
      setError(e.message);
    } finally {
      setOcupado(false);
    }
  }

  async function detener() {
    if (!id) return;
    setOcupado(true);
    try { setEjecucion(await api.cancelar(id)); }
    catch (e) { setError(e.message); }
    finally { setOcupado(false); }
  }

  const conMapa = pantalla === 'simulacion' || pantalla === 'operacion';
  const hayAveria = (ejecucion?.vehiculos || [])
    .some(v => v.estado === 'AVERIADO' || v.estado === 'EN_MANTENIMIENTO');

  /* La tarjeta de configuración solo existe en Simulación; Operación diaria pone su panel
     en el riel derecho para dejar la retícula entera, como en el prototipo. */
  const conCfg = pantalla === 'simulacion' && cfgAbierta;

  const clases = ['app'];
  if (!menuAbierto) clases.push('mini');
  if (!cfgAbierta) clases.push('cfgoff');
  if (conCfg) clases.push('con-cfg');
  if (conMapa) clases.push('con-riel');
  if (!conMapa) clases.push('hoja');
  if (conMapa && hayAveria) clases.push('alerta');

  return <div className={clases.join(' ')}>
    {conMapa && <Mapa ejecucion={ejecucion} rutaSeleccionada={rutaSeleccionada}
      alSeleccionarRuta={setRutaSeleccionada}
      mapaDatos={mapaDatos} instante={ejecucion?.reloj} />}

    <header className="top">
      <span className="marca">PQR</span>
      <span className="wordmark">PaqRap</span>
      <button type="button" className="burger" onClick={() => setMenuAbierto(a => !a)}
        aria-label={menuAbierto ? 'Ocultar el menú' : 'Mostrar el menú'}><i /></button>
      <h1>{TITULOS[pantalla]}</h1>
    </header>

    {conMapa && <div className="kbar">
      <Chips ejecucion={ejecucion} />
      <div className="kmeta">
        {desactualizado && <span className="desact">DESACTUALIZADO</span>}
        <span className={enVivo && ejecucion && !esFinal(ejecucion.estado) ? 'vivo' : undefined}>
          {ejecucion ? (ESTADOS[ejecucion.estado] || ejecucion.estado).toUpperCase() : 'SIN EJECUCIÓN'}
          {ejecucion ? ` · ${fecha(ejecucion.reloj)}` : ''}
        </span>
        {ejecucion && <span>{ejecucion.algoritmo}</span>}
      </div>
    </div>}

    <aside className="side">
      <h6>EQUIPO 2B · PUCP 2026-2</h6>
      <nav aria-label="Pantallas">
        {PANTALLAS.map(([clave, titulo]) => <button key={clave} type="button"
          data-on={pantalla === clave || undefined}
          onClick={() => {
            setPantalla(clave);
            /* Donde el menú se superpone, elegir una pantalla lo cierra. */
            if (window.innerWidth <= 920) setMenuAbierto(false);
          }}>{titulo}</button>)}
      </nav>
      <footer>
        {fuente === 'MYSQL' ? 'Datos en MySQL' : 'Datos desde archivos'}<br />
        Ciudad reticulada 70 × 50 km
      </footer>
    </aside>

    {conMapa && hayAveria && <BannerAveria ejecucion={ejecucion}
      alVerUnidad={() => setPantalla('averias')} />}

    {pantalla === 'simulacion' && <Simulacion
      ejecucion={ejecucion} config={config} alCambiarConfig={cambiarConfig}
      periodos={periodos} detallePeriodo={detallePeriodo}
      alIniciar={iniciar} alDetener={detener} ocupado={ocupado}
      cfgAbierta={cfgAbierta} alAbrirCfg={() => setCfgAbierta(true)}
      alCerrarCfg={() => setCfgAbierta(false)} />}

    {conMapa && <div className="riel-der">
      {pantalla === 'operacion' && <OperacionDiaria ejecucion={ejecucion}
        alIrASimulacion={() => setPantalla('simulacion')} />}
      <ListaRutas ejecucion={ejecucion} seleccion={rutaSeleccionada}
        alSeleccionar={setRutaSeleccionada} />
      {rutaSeleccionada && <DetalleRuta ejecucion={ejecucion} rutaId={rutaSeleccionada}
        alCerrar={() => setRutaSeleccionada(null)} />}
    </div>}

    {pantalla === 'pedidos' && <div className="sheet">
      <Pedidos ejecucion={ejecucion} alIrASimulacion={() => setPantalla('simulacion')} />
    </div>}

    {pantalla === 'averias' && <div className="sheet">
      <Averias ejecucion={ejecucion}
        alIrASimulacion={() => setPantalla('simulacion')}
        alIrAOperacion={() => setPantalla('operacion')} />
    </div>}

    {error && <div className="aviso-flotante">
      <AvisoError mensaje={error} alCerrar={() => setError('')} />
    </div>}

    {!colapsoVisto && <ModalColapso ejecucion={ejecucion}
      alCerrar={() => { setColapsoVisto(true); setPantalla('simulacion'); }}
      alRepetir={() => { setColapsoVisto(true); setPantalla('simulacion'); }} />}
  </div>;
}
