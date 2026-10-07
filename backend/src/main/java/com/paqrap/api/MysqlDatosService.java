package com.paqrap.api;

import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.TipoAlmacen;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Consulta entradas normalizadas con sentencias parametrizadas; no ejecuta DDL ni semillas. */
@Service
@Profile("mysql")
final class MysqlDatosService implements DatosFuente {
    private final DataSource dataSource;

    MysqlDatosService(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public String tipo() { return "MYSQL"; }

    @Override
    public List<String> periodos() {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT DISTINCT DATE_FORMAT(registrado_en, '%Y%m') periodo "
                             + "FROM pedido ORDER BY periodo");
             ResultSet filas = sentencia.executeQuery()) {
            List<String> meses = new ArrayList<>();
            while (filas.next()) meses.add(filas.getString(1));
            return List.copyOf(meses);
        } catch (SQLException e) { throw errorBase(); }
    }

    @Override
    public PeriodoVista periodo(String aaaamm) {
        YearMonth mes = DatosService.parsearPeriodo(aaaamm);
        LocalDateTime desde = mes.atDay(1).atStartOfDay();
        LocalDateTime hasta = mes.plusMonths(1).atDay(1).atStartOfDay();
        try (Connection conexion = dataSource.getConnection()) {
            int pedidos = contar(conexion,
                    "SELECT COUNT(*) FROM pedido WHERE registrado_en >= ? AND registrado_en < ?",
                    desde, hasta);
            if (pedidos == 0) throw new ApiException(HttpStatus.NOT_FOUND,
                    "PERIODO_NO_DISPONIBLE", "No hay pedidos en ese periodo.");
            int bloqueos = contar(conexion,
                    "SELECT COUNT(*) FROM bloqueo WHERE inicio < ? AND fin > ?", hasta, desde);
            int jornadas = contar(conexion,
                    "SELECT COUNT(DISTINCT fecha) FROM mantenimiento_programado "
                            + "WHERE fecha >= ? AND fecha < ?", desde, hasta);
            LocalDate primera = primeraFecha(conexion, desde, hasta);
            return new PeriodoVista(aaaamm, pedidos, bloqueos, jornadas, jornadas > 0, primera);
        } catch (SQLException e) { throw errorBase(); }
    }

    @Override
    public MapaDatosVista mapa(String aaaamm) {
        YearMonth mes = DatosService.parsearPeriodo(aaaamm);
        periodo(aaaamm);
        LocalDateTime desde = mes.atDay(1).atStartOfDay();
        LocalDateTime hasta = mes.plusMonths(1).atDay(1).atStartOfDay();
        try (Connection conexion = dataSource.getConnection()) {
            return new MapaDatosVista(almacenes(conexion).stream().map(Vistas::almacen).toList(),
                    bloqueos(conexion, desde, hasta).stream().map(Vistas::bloqueo).toList());
        } catch (SQLException e) { throw errorBase(); }
    }

    @Override
    public DatosEntrada cargar(LocalDateTime inicio, int dias) {
        LocalDateTime fin = inicio.plusDays(dias);
        LocalDateTime desdeMes = YearMonth.from(inicio).atDay(1).atStartOfDay();
        LocalDateTime hastaMes = YearMonth.from(fin.minusNanos(1))
                .plusMonths(1).atDay(1).atStartOfDay();
        try (Connection conexion = dataSource.getConnection()) {
            List<Pedido> pedidos = pedidos(conexion, desdeMes, hastaMes);
            if (pedidos.stream().noneMatch(p -> !p.getFechaRegistro().isBefore(inicio)
                    && p.getFechaRegistro().isBefore(fin))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SIN_PEDIDOS",
                        "No hay pedidos en las fechas seleccionadas.");
            }
            return new DatosEntrada(List.copyOf(pedidos),
                    bloqueos(conexion, desdeMes, hastaMes),
                    mantenimiento(conexion, desdeMes, hastaMes));
        } catch (SQLException e) { throw errorBase(); }
    }

    @Override
    public List<Almacen> almacenes() {
        try (Connection conexion = dataSource.getConnection()) {
            return almacenes(conexion);
        } catch (SQLException e) { throw errorBase(); }
    }

    @Override
    public List<Vehiculo> flota() {
        try (Connection conexion = dataSource.getConnection()) {
            Ubicacion central = almacenes(conexion).stream()
                    .filter(a -> a.getTipo() == TipoAlmacen.CENTRAL)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "No hay almacén central en la base de datos.")).getUbicacion();
            try (
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT id, tipo_codigo FROM vehiculo ORDER BY id");
             ResultSet filas = sentencia.executeQuery()) {
            List<Vehiculo> flota = new ArrayList<>();
            while (filas.next()) flota.add(new Vehiculo(filas.getString("id"),
                    TipoVehiculo.valueOf(filas.getString("tipo_codigo")),
                    EstadoVehiculo.DISPONIBLE, central));
            return List.copyOf(flota);
            }
        } catch (SQLException e) { throw errorBase(); }
    }

    private static List<Almacen> almacenes(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT a.id, a.tipo, a.stock_inicial, u.x, u.y FROM almacen a "
                        + "JOIN ubicacion u ON u.id = a.ubicacion_id ORDER BY a.id");
             ResultSet filas = sentencia.executeQuery()) {
            List<Almacen> almacenes = new ArrayList<>();
            while (filas.next()) almacenes.add(new Almacen(filas.getString("id"),
                    TipoAlmacen.valueOf(filas.getString("tipo")),
                    new Ubicacion(filas.getInt("x"), filas.getInt("y")),
                    filas.getInt("stock_inicial")));
            return List.copyOf(almacenes);
        }
    }

    private static List<Pedido> pedidos(Connection conexion, LocalDateTime desde,
                                        LocalDateTime hasta) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT p.archivo_id, p.codigo, p.cliente_id, p.cantidad, p.registrado_en, "
                        + "p.plazo_horas, u.x, u.y FROM pedido p "
                        + "JOIN ubicacion u ON u.id = p.destino_id "
                        + "WHERE p.registrado_en >= ? AND p.registrado_en < ? "
                        + "ORDER BY p.registrado_en, p.id")) {
            rango(sentencia, desde, hasta);
            try (ResultSet filas = sentencia.executeQuery()) {
                List<Pedido> pedidos = new ArrayList<>();
                while (filas.next()) pedidos.add(new Pedido(
                        filas.getLong("archivo_id") + "-" + filas.getString("codigo"),
                        filas.getString("cliente_id"),
                        new Ubicacion(filas.getInt("x"), filas.getInt("y")),
                        filas.getInt("cantidad"),
                        filas.getTimestamp("registrado_en").toLocalDateTime(),
                        filas.getInt("plazo_horas")));
                return List.copyOf(pedidos);
            }
        }
    }

    private static List<Bloqueo> bloqueos(Connection conexion, LocalDateTime desde,
                                          LocalDateTime hasta) throws SQLException {
        String consulta = "SELECT b.id, b.inicio, b.fin, u.x, u.y FROM bloqueo b "
                + "JOIN bloqueo_vertice v ON v.bloqueo_id = b.id "
                + "JOIN ubicacion u ON u.id = v.ubicacion_id "
                + "WHERE b.inicio < ? AND b.fin > ? ORDER BY b.id, v.orden";
        try (PreparedStatement sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setTimestamp(1, Timestamp.valueOf(hasta));
            sentencia.setTimestamp(2, Timestamp.valueOf(desde));
            try (ResultSet filas = sentencia.executeQuery()) {
                Map<Long, BloqueoAcumulado> grupos = new LinkedHashMap<>();
                while (filas.next()) {
                    long id = filas.getLong("id");
                    BloqueoAcumulado grupo = grupos.computeIfAbsent(id, key ->
                            new BloqueoAcumulado(
                                    fecha(filas, "inicio"), fecha(filas, "fin")));
                    grupo.vertices.add(new Ubicacion(filas.getInt("x"), filas.getInt("y")));
                }
                return grupos.values().stream().map(g -> Bloqueo.dePoligonal(
                        g.inicio, g.fin, g.vertices)).toList();
            }
        }
    }

    private static PlanMantenimiento mantenimiento(Connection conexion, LocalDateTime desde,
                                                     LocalDateTime hasta) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT fecha, vehiculo_id FROM mantenimiento_programado "
                        + "WHERE fecha >= ? AND fecha < ? ORDER BY fecha, vehiculo_id")) {
            rango(sentencia, desde, hasta);
            try (ResultSet filas = sentencia.executeQuery()) {
                Map<LocalDate, Set<String>> porDia = new HashMap<>();
                while (filas.next()) {
                    LocalDate dia = filas.getDate("fecha").toLocalDate();
                    porDia.computeIfAbsent(dia, key -> new java.util.HashSet<>())
                            .add(filas.getString("vehiculo_id"));
                }
                return new PlanMantenimiento(porDia);
            }
        }
    }

    private static int contar(Connection conexion, String consulta, LocalDateTime primero,
                              LocalDateTime segundo) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(consulta)) {
            rango(sentencia, primero, segundo);
            try (ResultSet filas = sentencia.executeQuery()) {
                filas.next();
                return filas.getInt(1);
            }
        }
    }

    private static LocalDate primeraFecha(Connection conexion, LocalDateTime desde,
                                           LocalDateTime hasta) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT MIN(DATE(registrado_en)) FROM pedido "
                        + "WHERE registrado_en >= ? AND registrado_en < ?")) {
            rango(sentencia, desde, hasta);
            try (ResultSet filas = sentencia.executeQuery()) {
                filas.next();
                java.sql.Date valor = filas.getDate(1);
                return valor == null ? null : valor.toLocalDate();
            }
        }
    }

    private static void rango(PreparedStatement sentencia, LocalDateTime primero,
                              LocalDateTime segundo) throws SQLException {
        sentencia.setTimestamp(1, Timestamp.valueOf(primero));
        sentencia.setTimestamp(2, Timestamp.valueOf(segundo));
    }

    private static LocalDateTime fecha(ResultSet filas, String columna) {
        try { return filas.getTimestamp(columna).toLocalDateTime(); }
        catch (SQLException e) { throw new IllegalStateException("Bloqueo inválido.", e); }
    }

    private static ApiException errorBase() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BASE_NO_DISPONIBLE",
                "No se pudo consultar la base de datos.");
    }

    private static final class BloqueoAcumulado {
        final LocalDateTime inicio;
        final LocalDateTime fin;
        final List<Ubicacion> vertices = new ArrayList<>();
        BloqueoAcumulado(LocalDateTime inicio, LocalDateTime fin) {
            this.inicio = inicio;
            this.fin = fin;
        }
    }
}
