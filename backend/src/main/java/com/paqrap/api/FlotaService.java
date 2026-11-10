package com.paqrap.api;

import com.paqrap.api.ApiModels.ActualizarTipoFlotaRequest;
import com.paqrap.api.ApiModels.ActualizarUnidadRequest;
import com.paqrap.api.ApiModels.Coordenada;
import com.paqrap.api.ApiModels.FlotaVista;
import com.paqrap.api.ApiModels.NuevaUnidadRequest;
import com.paqrap.api.ApiModels.TipoFlotaVista;
import com.paqrap.api.ApiModels.UnidadFlotaVista;
import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.ConfiguracionFlota;
import com.paqrap.modelo.EspecificacionVehiculo;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mantenimiento de las unidades de transporte (ítem B04 de la evaluación).
 *
 * La flota base sale de la fuente de datos —archivos del curso o MySQL— y aquí se guardan los
 * cambios hechos desde la pantalla: alta de unidades, baja de las agregadas, activación,
 * nodo de partida y, por tipo, capacidad, velocidad y costo. El caso define estos tres últimos
 * por tipo de unidad y no por unidad individual.
 *
 * Cada corrida recibe una copia con su propia {@link ConfiguracionFlota}: un cambio hecho
 * mientras una simulación está en marcha aplica a las corridas que se inicien después y no
 * altera a mitad de camino las decisiones de la que ya corre.
 *
 * Los cambios viven en memoria, igual que las corridas: se pierden al reiniciar el servidor.
 */
@Service
final class FlotaService {
    static final int MAX_CAPACIDAD = 500;
    static final double MAX_VELOCIDAD = 120.0;
    static final double MAX_COSTO = 1000.0;
    private static final int MAX_POR_TIPO = 99;

    private final DatosFuente datos;
    private final Map<TipoVehiculo, EspecificacionVehiculo> tipos = new EnumMap<>(TipoVehiculo.class);
    private final Map<String, TipoVehiculo> agregadas = new LinkedHashMap<>();
    private final Map<String, Ubicacion> ubicaciones = new LinkedHashMap<>();
    private final Set<String> inactivas = new HashSet<>();

    FlotaService(DatosFuente datos) {
        this.datos = datos;
        restaurarTipos();
    }

    /** Unidades activas con las que arranca una corrida nueva, aisladas de cambios posteriores. */
    synchronized List<Vehiculo> vigente() {
        ConfiguracionFlota configuracion = ConfiguracionFlota.nueva();
        for (var tipo : tipos.entrySet()) {
            configuracion.cambiarCapacidad(tipo.getKey(), tipo.getValue().capacidadPaquetes());
            configuracion.cambiarVelocidad(tipo.getKey(), tipo.getValue().velocidadKmH());
            configuracion.cambiarCosto(tipo.getKey(), tipo.getValue().costoPorKm());
        }
        return todas().stream().filter(v -> !inactivas.contains(v.getId()))
                .map(v -> new Vehiculo(v.getId(), v.getTipo(), EstadoVehiculo.DISPONIBLE,
                        v.getUbicacion(), null, configuracion))
                .toList();
    }

    synchronized FlotaVista consultar() {
        List<Vehiculo> unidades = todas();
        List<TipoFlotaVista> porTipo = new ArrayList<>();
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            EspecificacionVehiculo vigente = tipos.get(tipo);
            EspecificacionVehiculo caso = tipo.getEspecificacionDelCaso();
            long total = unidades.stream().filter(v -> v.getTipo() == tipo).count();
            long activas = unidades.stream().filter(v -> v.getTipo() == tipo
                    && !inactivas.contains(v.getId())).count();
            porTipo.add(new TipoFlotaVista(tipo.name(), vigente.capacidadPaquetes(),
                    vigente.velocidadKmH(), vigente.costoPorKm(), caso.capacidadPaquetes(),
                    caso.velocidadKmH(), caso.costoPorKm(), (int) total, (int) activas));
        }
        List<UnidadFlotaVista> filas = unidades.stream().map(v -> {
            EspecificacionVehiculo vigente = tipos.get(v.getTipo());
            return new UnidadFlotaVista(v.getId(), v.getTipo().name(),
                    Vistas.coordenada(v.getUbicacion()), !inactivas.contains(v.getId()),
                    agregadas.containsKey(v.getId()), vigente.capacidadPaquetes(),
                    vigente.velocidadKmH(), vigente.costoPorKm());
        }).toList();
        boolean modificada = !agregadas.isEmpty() || !ubicaciones.isEmpty() || !inactivas.isEmpty()
                || java.util.Arrays.stream(TipoVehiculo.values())
                        .anyMatch(t -> !tipos.get(t).equals(t.getEspecificacionDelCaso()));
        return new FlotaVista(porTipo, filas, modificada);
    }

    synchronized FlotaVista actualizarTipo(String codigo, ActualizarTipoFlotaRequest cambios) {
        TipoVehiculo tipo = tipo(codigo);
        EspecificacionVehiculo actual = tipos.get(tipo);
        int capacidad = cambios.capacidad() == null ? actual.capacidadPaquetes() : cambios.capacidad();
        double velocidad = cambios.velocidadKmH() == null ? actual.velocidadKmH() : cambios.velocidadKmH();
        double costo = cambios.costoPorKm() == null ? actual.costoPorKm() : cambios.costoPorKm();
        if (capacidad < 1 || capacidad > MAX_CAPACIDAD) throw invalido(
                "La capacidad debe estar entre 1 y " + MAX_CAPACIDAD + " productos.");
        if (!(velocidad >= 1.0) || velocidad > MAX_VELOCIDAD) throw invalido(
                "La velocidad debe estar entre 1 y " + (int) MAX_VELOCIDAD + " km/h.");
        if (!(costo > 0.0) || costo > MAX_COSTO) throw invalido(
                "El costo por kilómetro debe ser mayor que 0 y no superar " + (int) MAX_COSTO + ".");
        tipos.put(tipo, new EspecificacionVehiculo(capacidad, velocidad, costo));
        return consultar();
    }

    synchronized FlotaVista agregar(NuevaUnidadRequest solicitud) {
        TipoVehiculo tipo = solicitud.tipo();
        Set<String> usados = new HashSet<>();
        for (Vehiculo v : todas()) usados.add(v.getId());
        String id = null;
        for (int numero = 1; numero <= MAX_POR_TIPO && id == null; numero++) {
            String candidato = tipo.codigoUnidad(numero);
            if (!usados.contains(candidato)) id = candidato;
        }
        if (id == null) throw new ApiException(HttpStatus.CONFLICT, "FLOTA_COMPLETA",
                "Ya existen " + MAX_POR_TIPO + " unidades de ese tipo.");
        agregadas.put(id, tipo);
        if (solicitud.ubicacion() != null) ubicaciones.put(id, ubicacion(solicitud.ubicacion()));
        return consultar();
    }

    synchronized FlotaVista actualizarUnidad(String id, ActualizarUnidadRequest cambios) {
        Vehiculo unidad = unidad(id);
        if (cambios.ubicacion() != null) {
            ubicaciones.put(unidad.getId(), ubicacion(cambios.ubicacion()));
        }
        if (cambios.activa() != null) {
            if (cambios.activa()) {
                inactivas.remove(unidad.getId());
            } else {
                long activas = todas().stream().filter(v -> !inactivas.contains(v.getId())).count();
                if (activas <= 1 && !inactivas.contains(unidad.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "FLOTA_VACIA",
                            "Debe quedar al menos una unidad activa.");
                }
                inactivas.add(unidad.getId());
            }
        }
        return consultar();
    }

    /** Solo se eliminan las unidades agregadas aquí; las de la fuente de datos se desactivan. */
    synchronized FlotaVista eliminar(String id) {
        Vehiculo unidad = unidad(id);
        if (!agregadas.containsKey(unidad.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIDAD_BASE",
                    "Las unidades de la flota base no se eliminan: se pueden desactivar.");
        }
        long activas = todas().stream().filter(v -> !inactivas.contains(v.getId())).count();
        if (activas <= 1 && !inactivas.contains(unidad.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "FLOTA_VACIA",
                    "Debe quedar al menos una unidad activa.");
        }
        agregadas.remove(unidad.getId());
        ubicaciones.remove(unidad.getId());
        inactivas.remove(unidad.getId());
        return consultar();
    }

    synchronized FlotaVista restablecer() {
        agregadas.clear();
        ubicaciones.clear();
        inactivas.clear();
        restaurarTipos();
        return consultar();
    }

    /** Flota base más las unidades agregadas, cada una en su nodo de partida configurado. */
    private List<Vehiculo> todas() {
        List<Vehiculo> base = datos.flota();
        List<Vehiculo> unidades = new ArrayList<>();
        Ubicacion partida = base.isEmpty() ? datos.almacenes().get(0).getUbicacion()
                : base.get(0).getUbicacion();
        for (Vehiculo v : base) {
            unidades.add(ubicaciones.containsKey(v.getId()) ? v.en(ubicaciones.get(v.getId())) : v);
        }
        Set<String> enBase = new HashSet<>();
        for (Vehiculo v : base) enBase.add(v.getId());
        for (var agregada : agregadas.entrySet()) {
            if (enBase.contains(agregada.getKey())) continue;
            unidades.add(new Vehiculo(agregada.getKey(), agregada.getValue(),
                    EstadoVehiculo.DISPONIBLE,
                    ubicaciones.getOrDefault(agregada.getKey(), partida)));
        }
        unidades.sort(java.util.Comparator.comparing(Vehiculo::getId));
        return unidades;
    }

    private Vehiculo unidad(String id) {
        String buscado = id == null ? "" : id.trim();
        return todas().stream().filter(v -> v.getId().equalsIgnoreCase(buscado)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNIDAD_DESCONOCIDA",
                        "La unidad no pertenece a la flota."));
    }

    private void restaurarTipos() {
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            tipos.put(tipo, tipo.getEspecificacionDelCaso());
        }
    }

    private static TipoVehiculo tipo(String codigo) {
        try {
            return TipoVehiculo.valueOf(codigo == null ? "" : codigo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TIPO_DESCONOCIDO",
                    "El tipo de unidad no existe.");
        }
    }

    private static Ubicacion ubicacion(Coordenada coordenada) {
        if (!Ciudad.contiene(coordenada.x(), coordenada.y())) throw invalido(
                "El nodo de partida debe estar dentro de la retícula de "
                        + Ciudad.ANCHO_KM + " × " + Ciudad.ALTO_KM + " km.");
        return new Ubicacion(coordenada.x(), coordenada.y());
    }

    private static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FLOTA_INVALIDA", mensaje);
    }
}
