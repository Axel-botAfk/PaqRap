package com.paqrap.api;

import com.paqrap.api.ApiModels.AvanceVista;
import com.paqrap.api.ApiModels.Coordenada;
import com.paqrap.api.ApiModels.PedidoVista;
import com.paqrap.api.ApiModels.ResumenVista;
import com.paqrap.api.ApiModels.RutaVista;
import com.paqrap.api.ApiModels.VehiculoVista;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.simulacion.AvanceDeLaOperacion;
import com.paqrap.simulacion.MedicionDePlanificacion;
import com.paqrap.simulacion.ResumenSimulacion;

import java.util.List;

/** Copias inmutables para evitar exponer el estado mutable del motor en JSON. */
final class Vistas {
    private Vistas() { }

    static Coordenada coordenada(Ubicacion ubicacion) {
        return new Coordenada(ubicacion.x(), ubicacion.y());
    }

    static PedidoVista pedido(Pedido pedido, String estado, String motivo) {
        return new PedidoVista(pedido.getId(), pedido.getIdOriginal(),
                coordenada(pedido.getDestino()), pedido.getCantidad(),
                pedido.getFechaRegistro(), pedido.getFechaLimite(), estado, motivo);
    }

    static List<PedidoVista> pedidos(List<Pedido> pedidos, String estado, String motivo) {
        return pedidos.stream().map(p -> pedido(p, estado, motivo)).toList();
    }

    static VehiculoVista vehiculo(Vehiculo vehiculo) {
        return new VehiculoVista(vehiculo.getId(), vehiculo.getTipo().name(),
                coordenada(vehiculo.getUbicacion()), vehiculo.getEstado().name(),
                vehiculo.getCapacidad(), vehiculo.getCargaABordo());
    }

    static RutaVista ruta(Ruta ruta) {
        return new RutaVista(ruta.getId(), ruta.getVehiculo().getId(),
                ruta.getVehiculo().getTipo().name(), ruta.getAlmacen().getId(),
                coordenada(ruta.getAlmacen().getUbicacion()), ruta.getCargaTotal(),
                ruta.getDistanciaTotalKm(), ruta.getCostoTotal(),
                pedidos(ruta.getPedidos(), "PLANIFICADO", null));
    }

    static AvanceVista avance(AvanceDeLaOperacion avance, MedicionDePlanificacion medicion) {
        return new AvanceVista(avance.pedidosEntregados(), avance.productosEntregados(),
                avance.pedidosVencidos(), avance.productosVencidos(),
                avance.productosABordo(), medicion.pedidosEnCola(), medicion.milisegundos());
    }

    static ResumenVista resumen(ResumenSimulacion resumen) {
        return new ResumenVista(resumen.inicio(), resumen.fin(), resumen.pedidosRecibidos(),
                resumen.pedidosOriginalesCompletos(), resumen.pedidosOriginalesEnPlazo(),
                resumen.pedidosPendientes(), resumen.productosRecibidos(),
                resumen.distanciaTotalKm(), resumen.costoTotal(),
                resumen.iteracionesDePlanificacion(), resumen.huboColapso(),
                resumen.instanteDelColapso(), resumen.pedidoQueColapso() == null
                        ? null : resumen.pedidoQueColapso().getId());
    }
}
