package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;

import java.time.LocalDateTime;

/**
 * Producto que una unidad retira de un almacén en un momento dado.
 *
 * Es lo que descuenta el inventario: cada viaje empieza cargando en el almacén del que sale,
 * y el momento importa porque los almacenes intermedios se recargan cada 24 horas.
 */
public record CargaEnAlmacen(Almacen almacen, LocalDateTime instante, int unidades) {
}
