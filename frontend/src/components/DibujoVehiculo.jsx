import React from 'react';

/** Silueta de cada tipo de unidad; se usa igual en el mapa y en la leyenda. */
export default function DibujoVehiculo({ tipo }) {
  switch (tipo) {
    case 'AUTO':
      return <g strokeLinejoin="round">
        <path d="M13 2.5Q16 1 19 2.5L21.3 5.6Q23 8.1 23 12v12.5q0 5.7-4.2 6.1h-5.6Q9 30.2 9 24.5V12q0-3.9 1.7-6.4Z"
          fill="#1565C0" stroke="#0D47A1" strokeWidth="1.2" />
        <path d="M12.3 5.1Q16 3.6 19.7 5.1l1.2 2.4Q16 6.6 11.1 7.5Z" fill="#0D47A1" />
        <path d="M11.6 8.4Q16 7.1 20.4 8.4l-.8 3.7Q16 11.3 12.4 12.1Z" fill="#BBD9F5" />
        <path d="M12.4 13Q16 12.2 19.6 13l.8 8.8Q16 22.9 11.6 21.8Z" fill="#BBD9F5" />
        <path d="M11.7 22.9Q16 24 20.3 22.9l-.7 3.5Q16 27.2 12.4 26.4Z" fill="#BBD9F5" />
        <path d="M9.8 11.6 11.5 13v8.4l-1.7 1.5Zm12.4 0L20.5 13v8.4l1.7 1.5Z"
          fill="#0D47A1" />
        <path d="M12.4 13 11.6 21.8M19.6 13l.8 8.8" fill="none" stroke="#0D47A1" strokeWidth=".65" />
        <path d="M9 13.3 7.7 14.2v2.6L9 16.3Zm14 0 1.3.9v2.6l-1.3-.5Z" fill="#0D47A1" />
        <path d="M10.1 4.8 12.4 3.5l-.7 2.4-1.9.6Zm11.8 0-2.3-1.3.7 2.4 1.9.6Z" fill="#EAF5FF" />
        <path d="M10 27.4 12 28l-.2 1.5-1.7-.6Zm12 0-2 .6.2 1.5 1.7-.6Z" fill="#E74852" />
      </g>;
    case 'MOTO':
      return <g strokeLinecap="round" strokeLinejoin="round">
        <rect x="14.2" y="1.7" width="3.6" height="7.4" rx="1.8" fill="#212121" stroke="#607D8B" strokeWidth=".6" />
        <rect x="14.1" y="25.2" width="3.8" height="5.3" rx="1.9" fill="#212121" stroke="#607D8B" strokeWidth=".6" />
        <path d="M13.4 6.1q2.6-1.7 5.2 0l1.4 4.3-.8 6.2-1.2 8.7q-2 1.9-4 0l-1.2-8.7-.8-6.2Z"
          fill="#E53935" stroke="#B71C1C" strokeWidth="1" />
        <path d="M13.3 7.3q2.7-1.1 5.4 0l.6 2.7q-3.3 1.3-6.6 0Z" fill="#B71C1C" />
        <path d="M11.6 11.6 9.1 12.4M20.4 11.6l2.5.8M10.4 12.1l1.5 2M21.6 12.1l-1.5 2"
          fill="none" stroke="#212121" strokeWidth="1.2" />
        <path d="M7.8 11.6q1.2-.7 2.5.2l.5 1.1-2.7.5q-1.2-.2-.3-1.8Zm16.4 0q-1.2-.7-2.5.2l-.5 1.1 2.7.5q1.2-.2.3-1.8Z"
          fill="#E53935" stroke="#B71C1C" strokeWidth=".6" />
        <path d="M12.9 13.8q3.1-2.2 6.2 0l.6 4.2q-3.7 1.6-7.4 0Z" fill="#E53935" stroke="#B71C1C" strokeWidth=".7" />
        <path d="M13.7 18.9q2.3-.8 4.6 0l.7 5.1q-3 1.7-6 0Z" fill="#212121" stroke="#607D8B" strokeWidth=".7" />
        <path d="M13.8 26.1q2.2 1 4.4 0" fill="none" stroke="#B71C1C" strokeWidth="1" />
        <path d="M14.6 7.3h2.8l.5 1.6q-1.9.7-3.8 0Z" fill="#EAF5FF" stroke="#607D8B" strokeWidth=".45" />
        <path d="M13.8 27.2h4.4" fill="none" stroke="#E53935" strokeWidth="1.1" />
      </g>;
    case 'BICICLETA':
      return <g strokeLinecap="round" strokeLinejoin="round">
        <rect x="14.6" y="1.7" width="2.8" height="8.1" rx="1.4" fill="#212121" stroke="#607D8B" strokeWidth=".5" />
        <rect x="14.6" y="25" width="2.8" height="5.5" rx="1.4" fill="#212121" stroke="#607D8B" strokeWidth=".5" />
        <path d="M14.5 8.4 13.3 14.3l.8 6.2-1 4.7q2.9 1.6 5.8 0l-1-4.7.8-6.2-1.2-5.9Z"
          fill="#FFC107" stroke="#F9A825" strokeWidth=".85" />
        <path d="M14.2 11.3 16 13l1.8-1.7M14.1 20.4 16 17.5l1.9 2.9" fill="none"
          stroke="#F9A825" strokeWidth=".8" />
        <path d="M14.2 10.6 11.2 9.7 8.6 11.1m9.2-.5 3-.9 2.6 1.4"
          fill="none" stroke="#212121" strokeWidth="2" />
        <path d="M8.1 10.9 10.6 9.7m13.3 1.2-2.5-1.2" fill="none" stroke="#607D8B" strokeWidth=".6" />
        <circle cx="16" cy="10.6" r="1.25" fill="#607D8B" stroke="#212121" strokeWidth=".5" />
        <path d="M14.4 18.7q1.6-.8 3.2 0l.7 4.5q-2.3 1.3-4.6 0Z"
          fill="#212121" stroke="#607D8B" strokeWidth=".6" />
        <path d="M11.3 22.8h9.4" fill="none" stroke="#607D8B" strokeWidth=".8" />
        <rect x="10.5" y="21.8" width="2.1" height="2" rx=".4" fill="#212121" />
        <rect x="19.4" y="21.8" width="2.1" height="2" rx=".4" fill="#212121" />
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
