import React from 'react';
import { numero, SIN_DATO } from '../services/formato.js';

/* Dos presentaciones del mismo dato, como en el prototipo:

   - `Chips`: la franja compacta bajo la barra superior, en las vistas con mapa.
   - `Kpi`: la tarjeta blanca con borde superior de color, en las hojas (Pedidos, Averías).

   En ambas, sin dato se muestra «Sin datos», nunca 0 (25.dis.gui.v2 seccion 8). */

export function Kpi({ etiqueta, valor, nota, color }) {
  return <div className="kpi" style={color ? { '--k': color } : undefined}>
    <span>{etiqueta}</span>
    <b>{valor}</b>
    {nota && <small>{nota}</small>}
  </div>;
}

export function Chip({ etiqueta, valor, alerta = false }) {
  return <div className="chip" data-alerta={alerta || undefined}>
    <span>{etiqueta}</span>
    <b>{valor}</b>
  </div>;
}

export default function Chips({ ejecucion }) {
  const avance = ejecucion?.avance;
  const planificadas = (ejecucion?.rutas || []).reduce((total, r) => total + r.paradas.length, 0);
  const sinAsignar = ejecucion?.noAsignados?.length;
  const fuera = (ejecucion?.vehiculos || [])
    .filter(v => v.estado === 'AVERIADO' || v.estado === 'EN_MANTENIMIENTO').length;

  return <div className="chips">
    <Chip etiqueta="PEDIDOS ENTREGADOS" valor={numero(avance?.pedidosEntregados, SIN_DATO)} />
    <Chip etiqueta="PRODUCTOS ENTREGADOS" valor={numero(avance?.productosEntregados, SIN_DATO)} />
    <Chip etiqueta="EN COLA" valor={numero(avance?.pedidosEnCola, SIN_DATO)} />
    <Chip etiqueta="PARADAS PLANIFICADAS" valor={ejecucion ? numero(planificadas) : SIN_DATO} />
    <Chip etiqueta="SIN ASIGNAR" valor={ejecucion ? numero(sinAsignar ?? 0) : SIN_DATO}
      alerta={!!sinAsignar} />
    <Chip etiqueta="FUERA DE SERVICIO" valor={ejecucion ? numero(fuera) : SIN_DATO}
      alerta={fuera > 0} />
  </div>;
}
