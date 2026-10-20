import React from 'react';

/** Silueta de cada tipo de unidad; se usa igual en el mapa y en la leyenda. */
export default function DibujoVehiculo({ tipo }) {
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

export function IconoLeyenda({ tipo, texto }) {
  return <span className="leyenda-vehiculo"><svg viewBox="0 0 32 32" aria-hidden="true"><DibujoVehiculo tipo={tipo} /></svg>{texto}</span>;
}
