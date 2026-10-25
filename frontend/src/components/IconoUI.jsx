import React from 'react';

const trazos = {
  pedidos: <><path d="M7 3h8l5 5v13H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"/><path d="M15 3v6h5M9 13h7M9 17h7"/></>,
  averias: <path d="M21 7.5a6 6 0 0 1-7.8 5.7L7 19.4a2 2 0 0 1-2.8-2.8l6.2-6.2A6 6 0 0 1 16.1 2L13 5.1l.9 3 3 .9L21 5Z"/>,
  calendario: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18M8 15h3"/></>,
  engranaje: <><path d="M10 2h4l.6 2.3 1.9.8 2-.9 2.8 2.8-.9 2 .8 1.9L23 11v4l-2.3.6-.8 1.9.9 2-2.8 2.8-2-.9-1.9.8L14 24h-4l-.6-2.3-1.9-.8-2 .9-2.8-2.8.9-2-.8-1.9L1 15v-4l2.3-.6.8-1.9-.9-2L6 3.7l2 .9 1.9-.8Z" transform="translate(0 -1) scale(.96)"/><circle cx="12" cy="12" r="3"/></>,
  caja: <><path d="m12 2 9 5v10l-9 5-9-5V7l9-5Z"/><path d="m3 7 9 5 9-5M12 12v10"/></>,
  ruta: <><circle cx="5" cy="18" r="3"/><circle cx="19" cy="6" r="3"/><path d="M8 18h3a4 4 0 0 0 4-4v-4a4 4 0 0 1 4-4"/></>,
  reloj: <><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 3"/></>,
  camion: <><path d="M2 5h12v12H2zM14 9h4l4 4v4h-8Z"/><circle cx="6" cy="18" r="2"/><circle cx="18" cy="18" r="2"/></>,
  enviar: <path d="m3 11 18-8-7 18-3-8-8-2Zm8 2 10-10"/>,
  persona: <><circle cx="12" cy="7" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
  campana: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 9h18c0-1-3-2-3-9ZM10 21h4"/></>,
  info: <><circle cx="12" cy="12" r="10"/><path d="M12 11v6M12 7h.01"/></>,
  clip: <path d="m8 12 7-7a4 4 0 0 1 6 6L11 21a6 6 0 0 1-9-9L12 2"/>
};

export default function IconoUI({ nombre, size = 24, className = '' }) {
  return <svg className={className} width={size} height={size} viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true" focusable="false">{trazos[nombre]}</svg>;
}
