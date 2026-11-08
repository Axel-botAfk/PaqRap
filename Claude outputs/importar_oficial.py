#!/usr/bin/env python3
"""
Genera un script SQL (MySQL 8) que carga los archivos oficiales del curso en la base `paqrap`.

Uso:
    python importar_oficial.py <carpeta_reales> <salida.sql> [archivo1 archivo2 ...]

Si no se indican archivos, usa todos los que haya en la carpeta con estos nombres:
    ventas.AAAAMM.txt        registro  ##d##h##m:x,y,cIdCliente,qq,hl
    bloqueo.AAMM.txt         registro  ##d##h##m-##d##h##m:x1,y1,x2,y2,...
    mant.preventivo.MM.MM.txt registro aaaammdd:TTNN

El SQL es idempotente: se puede ejecutar varias veces. No borra ni modifica filas existentes;
si una fila ya existe (mismo archivo + numero de linea) se deja como esta.
"""
import calendar
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

LOTE = 400
RE_VENTAS = re.compile(r"^ventas\.(\d{4})(\d{2})\.txt$")
RE_BLOQUEO = re.compile(r"^bloqueo\.(\d{2})(\d{2})\.txt$")
RE_MANT = re.compile(r"^mant\.preventivo\.(\d{2})\.(\d{2})\.txt$")
RE_TIEMPO = r"(\d{2})d(\d{2})h(\d{2})m"


def q(texto):
    return "'" + texto.replace("\\", "\\\\").replace("'", "''") + "'"


def tiempo(base, d, h, m):
    return base + timedelta(days=int(d) - 1, hours=int(h), minutes=int(m))


def fmt(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def lineas(ruta):
    crudas = ruta.read_text(encoding="utf-8").split("\n")
    while crudas and not crudas[-1].strip():
        crudas.pop()
    for i, linea in enumerate(crudas, start=1):
        linea = linea.rstrip("\r")
        if not linea.strip():
            raise SystemExit(f"{ruta.name}: linea {i} vacia; no se puede numerar con seguridad.")
        yield i, linea


def punto(x, y, donde):
    x, y = int(x), int(y)
    if not (0 <= x <= 70 and 0 <= y <= 50):
        raise SystemExit(f"{donde}: coordenada fuera de la ciudad ({x},{y}).")
    return x, y


def lotes(filas):
    for i in range(0, len(filas), LOTE):
        yield filas[i:i + LOTE]


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    carpeta, salida = Path(sys.argv[1]), Path(sys.argv[2])
    nombres = sys.argv[3:] or sorted(p.name for p in carpeta.iterdir() if p.is_file())
    ventas, bloqueos, mants = [], [], []
    for nombre in nombres:
        if RE_VENTAS.match(nombre):
            ventas.append(nombre)
        elif RE_BLOQUEO.match(nombre):
            bloqueos.append(nombre)
        elif RE_MANT.match(nombre):
            mants.append(nombre)
    if not (ventas or bloqueos or mants):
        raise SystemExit("No hay archivos oficiales reconocibles.")

    out = []
    w = out.append
    w("-- Carga de archivos oficiales. Generado por importar_oficial.py; idempotente.")
    w("-- Archivos: " + ", ".join(ventas + bloqueos + mants))
    w("USE paqrap;")
    w("SET NAMES utf8mb4;")
    w("START TRANSACTION;")

    ubicaciones, clientes = set(), set()
    datos_v, datos_b, datos_m = {}, {}, {}

    for nombre in ventas:
        a, m = map(int, RE_VENTAS.match(nombre).groups())
        base = datetime(a, m, 1)
        filas = []
        for n, linea in lineas(carpeta / nombre):
            r = re.fullmatch(RE_TIEMPO + r":(\d+),(\d+),(c\w+),(\d+),(\d+)", linea)
            if not r:
                raise SystemExit(f"{nombre}:{n}: formato inesperado: {linea}")
            d, h, mi, x, y, cli, cant, hl = r.groups()
            x, y = punto(x, y, f"{nombre}:{n}")
            reg = tiempo(base, d, h, mi)
            if int(cant) <= 0 or int(hl) <= 0:
                raise SystemExit(f"{nombre}:{n}: cantidad o plazo no positivo.")
            ubicaciones.add((x, y))
            clientes.add(cli)
            filas.append((n, cli, x, y, int(cant), fmt(reg), int(hl), linea))
        ultimo = calendar.monthrange(a, m)[1]
        datos_v[nombre] = (filas, base.date(), datetime(a, m, ultimo).date())

    for nombre in bloqueos:
        aa, mm = map(int, RE_BLOQUEO.match(nombre).groups())
        a = 2000 + aa
        base = datetime(a, mm, 1)
        cab, vert = [], []
        for n, linea in lineas(carpeta / nombre):
            r = re.fullmatch(RE_TIEMPO + "-" + RE_TIEMPO + r":([\d,]+)", linea)
            if not r:
                raise SystemExit(f"{nombre}:{n}: formato inesperado: {linea}")
            g = r.groups()
            ini, fin = tiempo(base, *g[0:3]), tiempo(base, *g[3:6])
            if fin <= ini:
                raise SystemExit(f"{nombre}:{n}: el bloqueo termina antes de empezar.")
            nums = [int(v) for v in g[6].split(",")]
            if len(nums) % 2 or len(nums) < 4:
                raise SystemExit(f"{nombre}:{n}: poligonal con coordenadas incompletas.")
            cab.append((n, fmt(ini), fmt(fin), linea))
            for orden, i in enumerate(range(0, len(nums), 2), start=1):
                x, y = punto(nums[i], nums[i + 1], f"{nombre}:{n}")
                ubicaciones.add((x, y))
                vert.append((n, orden, x, y))
        ultimo = calendar.monthrange(a, mm)[1]
        datos_b[nombre] = (cab, vert, base.date(), datetime(a, mm, ultimo).date())

    for nombre in mants:
        filas = []
        for n, linea in lineas(carpeta / nombre):
            r = re.fullmatch(r"(\d{4})(\d{2})(\d{2}):(T[ABM]\d{2})", linea)
            if not r:
                raise SystemExit(f"{nombre}:{n}: formato inesperado: {linea}")
            a, m, d, veh = r.groups()
            filas.append((n, f"{a}-{m}-{d}", veh, linea))
        fechas = [f[1] for f in filas]
        ini = datetime.strptime(min(fechas), "%Y-%m-%d").replace(day=1).date()
        f = datetime.strptime(max(fechas), "%Y-%m-%d")
        fin = datetime(f.year, f.month, calendar.monthrange(f.year, f.month)[1]).date()
        datos_m[nombre] = (filas, ini, fin)

    # Archivos fuente
    w("")
    w("-- Archivos fuente (los que ya existen con ese nombre se conservan).")
    todos = ([(n, "VENTAS", *datos_v[n][1:]) for n in ventas]
             + [(n, "BLOQUEOS", *datos_b[n][2:]) for n in bloqueos]
             + [(n, "MANTENIMIENTO", *datos_m[n][1:]) for n in mants])
    w("INSERT INTO archivo_fuente (tipo, nombre, periodo_inicio, periodo_fin) VALUES")
    w(",\n".join(f"  ({q(t)}, {q(n)}, {q(str(i))}, {q(str(f))})" for n, t, i, f in todos))
    w("ON DUPLICATE KEY UPDATE nombre = nombre;")
    for i, (n, *_r) in enumerate(todos):
        w(f"SET @arch{i} = (SELECT id FROM archivo_fuente WHERE nombre = {q(n)});")
    var = {n: f"@arch{i}" for i, (n, *_r) in enumerate(todos)}

    # Ubicaciones y clientes
    w("")
    w(f"-- Ubicaciones ({len(ubicaciones)}) y clientes ({len(clientes)}) que falten.")
    for lote in lotes(sorted(ubicaciones)):
        w("INSERT INTO ubicacion (x, y) VALUES " + ",".join(f"({x},{y})" for x, y in lote)
          + " ON DUPLICATE KEY UPDATE x = x;")
    for lote in lotes(sorted(clientes)):
        w("INSERT INTO cliente (id) VALUES " + ",".join(f"({q(c)})" for c in lote)
          + " ON DUPLICATE KEY UPDATE id = id;")

    # Ventas
    for nombre in ventas:
        filas = datos_v[nombre][0]
        w("")
        w(f"-- {nombre}: {len(filas)} pedidos.")
        w("DROP TEMPORARY TABLE IF EXISTS stg_pedido;")
        w("CREATE TEMPORARY TABLE stg_pedido (n INT UNSIGNED PRIMARY KEY, cliente VARCHAR(32), "
          "x TINYINT UNSIGNED, y TINYINT UNSIGNED, cantidad INT UNSIGNED, registrado DATETIME, "
          "plazo INT UNSIGNED, original VARCHAR(255));")
        for lote in lotes(filas):
            w("INSERT INTO stg_pedido VALUES " + ",".join(
                f"({n},{q(c)},{x},{y},{cant},{q(reg)},{hl},{q(orig)})"
                for n, c, x, y, cant, reg, hl, orig in lote) + ";")
        w("INSERT INTO pedido (archivo_id, numero_linea, codigo, cliente_id, destino_id, cantidad, "
          "registrado_en, plazo_horas, limite_en, registro_original)")
        w(f"SELECT {var[nombre]}, s.n, CONCAT('P-', LPAD(s.n, 5, '0')), s.cliente, u.id, s.cantidad, "
          "s.registrado, s.plazo, DATE_ADD(s.registrado, INTERVAL s.plazo HOUR), s.original")
        w("FROM stg_pedido s JOIN ubicacion u ON u.x = s.x AND u.y = s.y")
        w("ON DUPLICATE KEY UPDATE pedido.id = pedido.id;")
        w("DROP TEMPORARY TABLE stg_pedido;")

    # Bloqueos
    for nombre in bloqueos:
        cab, vert = datos_b[nombre][0], datos_b[nombre][1]
        w("")
        w(f"-- {nombre}: {len(cab)} bloqueos, {len(vert)} vertices.")
        for lote in lotes(cab):
            w("INSERT INTO bloqueo (archivo_id, numero_linea, inicio, fin, registro_original) VALUES "
              + ",".join(f"({var[nombre]},{n},{q(i)},{q(f)},{q(o)})" for n, i, f, o in lote)
              + " ON DUPLICATE KEY UPDATE id = id;")
        w("DROP TEMPORARY TABLE IF EXISTS stg_vertice;")
        w("CREATE TEMPORARY TABLE stg_vertice (linea INT UNSIGNED, orden SMALLINT UNSIGNED, "
          "x TINYINT UNSIGNED, y TINYINT UNSIGNED, PRIMARY KEY (linea, orden));")
        for lote in lotes(vert):
            w("INSERT INTO stg_vertice VALUES " + ",".join(f"({n},{o},{x},{y})" for n, o, x, y in lote) + ";")
        w("INSERT INTO bloqueo_vertice (bloqueo_id, orden, ubicacion_id)")
        w("SELECT b.id, v.orden, u.id FROM stg_vertice v")
        w(f"JOIN bloqueo b ON b.archivo_id = {var[nombre]} AND b.numero_linea = v.linea")
        w("JOIN ubicacion u ON u.x = v.x AND u.y = v.y")
        w("ON DUPLICATE KEY UPDATE bloqueo_vertice.ubicacion_id = bloqueo_vertice.ubicacion_id;")
        w("DROP TEMPORARY TABLE stg_vertice;")

    # Mantenimiento
    for nombre in mants:
        filas = datos_m[nombre][0]
        w("")
        w(f"-- {nombre}: {len(filas)} jornadas de mantenimiento.")
        for lote in lotes(filas):
            w("INSERT INTO mantenimiento_programado (archivo_id, numero_linea, fecha, vehiculo_id, registro_original) VALUES "
              + ",".join(f"({var[nombre]},{n},{q(f)},{q(v)},{q(o)})" for n, f, v, o in lote)
              + " ON DUPLICATE KEY UPDATE vehiculo_id = vehiculo_id;")

    w("")
    w("COMMIT;")
    w("")
    w("-- Comprobacion: pedidos, bloqueos y mantenimientos por archivo fuente.")
    w("SELECT a.tipo, a.nombre,")
    w("  CASE a.tipo WHEN 'VENTAS' THEN (SELECT COUNT(*) FROM pedido p WHERE p.archivo_id = a.id)")
    w("              WHEN 'BLOQUEOS' THEN (SELECT COUNT(*) FROM bloqueo b WHERE b.archivo_id = a.id)")
    w("              ELSE (SELECT COUNT(*) FROM mantenimiento_programado m WHERE m.archivo_id = a.id) END AS filas")
    w("FROM archivo_fuente a WHERE a.nombre IN (" + ", ".join(q(n) for n, *_r in todos) + ")")
    w("ORDER BY a.id;")

    salida.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Escrito {salida} ({salida.stat().st_size // 1024} KB)")
    for n in ventas:
        print(f"  {n}: {len(datos_v[n][0])} pedidos")
    for n in bloqueos:
        print(f"  {n}: {len(datos_b[n][0])} bloqueos, {len(datos_b[n][1])} vertices")
    for n in mants:
        print(f"  {n}: {len(datos_m[n][0])} jornadas")
    print(f"  ubicaciones distintas: {len(ubicaciones)}, clientes distintos: {len(clientes)}")


if __name__ == "__main__":
    main()
