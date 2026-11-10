package com.paqrap.api;

import com.paqrap.api.ApiModels.ActualizarTipoFlotaRequest;
import com.paqrap.api.ApiModels.ActualizarUnidadRequest;
import com.paqrap.api.ApiModels.Coordenada;
import com.paqrap.api.ApiModels.NuevaUnidadRequest;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Vehiculo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlotaServiceTest {
    private final FlotaService flota = new FlotaService(new DatosService("no-se-usa"));

    @Test
    void laFlotaBaseEsLaDelCasoYNoFiguraComoModificada() {
        var vista = flota.consultar();
        assertEquals(37, vista.unidades().size());
        assertFalse(vista.modificada());
        assertEquals(24, vista.tipos().stream().filter(t -> t.tipo().equals("AUTO"))
                .findFirst().orElseThrow().capacidad());
        assertEquals(37, flota.vigente().size());
    }

    @Test
    void altaDesactivacionYBajaDeUnidades() {
        var conNueva = flota.agregar(new NuevaUnidadRequest(TipoVehiculo.AUTO, new Coordenada(12, 38)));
        var nueva = conNueva.unidades().stream().filter(u -> u.agregada()).findFirst().orElseThrow();
        assertEquals("TA11", nueva.id());
        assertEquals(new Coordenada(12, 38), nueva.ubicacionInicial());
        assertEquals(38, flota.vigente().size());

        flota.actualizarUnidad("TM01", new ActualizarUnidadRequest(false, null));
        List<Vehiculo> vigente = flota.vigente();
        assertEquals(37, vigente.size());
        assertTrue(vigente.stream().noneMatch(v -> v.getId().equals("TM01")));

        assertThrows(ApiException.class, () -> flota.eliminar("TA01"));
        flota.eliminar("TA11");
        assertEquals(36, flota.vigente().size());
        assertFalse(flota.restablecer().modificada());
        assertEquals(37, flota.vigente().size());
    }

    @Test
    void losAtributosPorTipoSeValidanYNoAlteranUnaFlotaYaEntregada() {
        List<Vehiculo> antes = flota.vigente();
        flota.actualizarTipo("MOTO", new ActualizarTipoFlotaRequest(10, 30.0, null));
        Vehiculo motoAntes = antes.stream().filter(v -> v.getTipo() == TipoVehiculo.MOTO)
                .findFirst().orElseThrow();
        Vehiculo motoDespues = flota.vigente().stream()
                .filter(v -> v.getTipo() == TipoVehiculo.MOTO).findFirst().orElseThrow();
        assertEquals(8, motoAntes.getCapacidad());
        assertEquals(10, motoDespues.getCapacidad());
        assertEquals(30.0, motoDespues.getVelocidadKmH());
        assertEquals(6.0, motoDespues.getCostoPorKm());
        assertThrows(ApiException.class, () -> flota.actualizarTipo("MOTO",
                new ActualizarTipoFlotaRequest(0, null, null)));
        assertThrows(ApiException.class, () -> flota.actualizarTipo("TRAILER",
                new ActualizarTipoFlotaRequest(5, null, null)));
    }

    @Test
    void elNodoDePartidaDebeEstarEnLaReticulaYQuedaAlMenosUnaUnidadActiva() {
        assertThrows(ApiException.class, () -> flota.actualizarUnidad("TA01",
                new ActualizarUnidadRequest(null, new Coordenada(71, 10))));
        flota.actualizarUnidad("TA01", new ActualizarUnidadRequest(null, new Coordenada(57, 27)));
        assertEquals(57, flota.vigente().stream().filter(v -> v.getId().equals("TA01"))
                .findFirst().orElseThrow().getUbicacion().x());
        var unidades = flota.consultar().unidades();
        for (var unidad : unidades.subList(1, unidades.size())) {
            flota.actualizarUnidad(unidad.id(), new ActualizarUnidadRequest(false, null));
        }
        assertThrows(ApiException.class, () -> flota.actualizarUnidad(unidades.get(0).id(),
                new ActualizarUnidadRequest(false, null)));
    }
}
