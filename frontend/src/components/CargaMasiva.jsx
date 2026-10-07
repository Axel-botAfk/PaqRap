import React, { useState } from 'react';

/* Carga masiva de los archivos mensuales del curso: ventas y calles bloqueadas.

   La validación de formato se hace en el servidor, que es el único que conoce el parser
   (LectorVentas y LectorBloqueos). Aquí solo se comprueba el nombre del archivo antes de
   subirlo, para no mandar algo que ya se sabe que va a ser rechazado.

   El endpoint todavía no existe. Contrato pedido al backend, en docs/api-pendiente.md:
     POST /api/datos/archivos  (multipart: tipo=VENTAS|BLOQUEOS, archivo)
     -> 200 { periodo, registros, descartados, avisos[] }  |  400 ARCHIVO_INVALIDO */

const TIPOS = {
  VENTAS: {
    etiqueta: 'Ventas del mes',
    patron: /^ventas\.?(\d{6})\.txt$/i,
    ejemplo: 'ventas.202608.txt',
    formato: '##d##h##m:posX,posY,cIdCliente,qq,hl'
  },
  BLOQUEOS: {
    etiqueta: 'Calles bloqueadas',
    patron: /^(\d{6})\.bloqueadas$|^bloqueo\.?(\d{4})\.txt$/i,
    ejemplo: '202608.bloqueadas',
    formato: 'una línea por tramo cerrado, con vigencia horaria'
  }
};

export default function CargaMasiva() {
  const [tipo, setTipo] = useState('VENTAS');
  const [archivo, setArchivo] = useState(null);

  const spec = TIPOS[tipo];
  const nombreValido = archivo ? spec.patron.test(archivo.name) : null;

  return <div className="card">
    <header><h2>Registro masivo</h2></header>
    <div className="body">
      <div className="tabs">
        {Object.entries(TIPOS).map(([clave, valor]) => <button key={clave} type="button"
          data-on={tipo === clave || undefined}
          onClick={() => { setTipo(clave); setArchivo(null); }}>{valor.etiqueta}</button>)}
      </div>

      <label className="campo">Archivo del mes · ejemplo <span className="mono">{spec.ejemplo}</span>
        <input type="file" accept=".txt,.bloqueadas"
          onChange={e => setArchivo(e.target.files?.[0] || null)} />
      </label>

      {archivo && <div className={`aviso ${nombreValido ? '' : 'aviso-error'}`}>
        <b>{nombreValido ? 'OK' : 'ERROR'}</b>
        <span>
          <span className="mono">{archivo.name}</span> · {(archivo.size / 1024).toFixed(0)} KB.{' '}
          {nombreValido
            ? 'El contenido se valida en el servidor antes de incorporarlo.'
            : `Se esperaba un nombre como ${spec.ejemplo}.`}
        </span>
      </div>}

      <p className="nota">
        Formato de cada registro: <span className="mono">{spec.formato}</span>. El mismo archivo
        alimenta la operación día a día y las simulaciones de periodo y de colapso.
      </p>

      <div className="aviso aviso-ambar" style={{ marginTop: 14 }}>
        <b>PENDIENTE</b>
        <span>
          La subida al servidor todavía no está disponible: la API no tiene endpoint de escritura
          para archivos. Por ahora los archivos del curso se copian a la carpeta de datos de la
          máquina virtual. El contrato que falta está anotado en{' '}
          <span className="mono">docs/api-pendiente.md</span>.
        </span>
      </div>
    </div>
  </div>;
}
