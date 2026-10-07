-- PaqRap: esquema y muestra fiel de los archivos del curso.
-- MySQL 8.0.16+; ejecutar en una base nueva desde MySQL Workbench.
-- No borra bases ni tablas. Los DATETIME representan la hora local de Lima.

CREATE DATABASE IF NOT EXISTS paqrap
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE paqrap;

-- Catalogos y entradas del problema.
CREATE TABLE ubicacion (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  x TINYINT UNSIGNED NOT NULL,
  y TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_ubicacion_xy (x, y),
  CONSTRAINT ck_ubicacion_x CHECK (x <= 70),
  CONSTRAINT ck_ubicacion_y CHECK (y <= 50)
) ENGINE=InnoDB;

CREATE TABLE cliente (
  id VARCHAR(32) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE almacen (
  id VARCHAR(32) NOT NULL,
  tipo VARCHAR(12) NOT NULL,
  ubicacion_id BIGINT UNSIGNED NOT NULL,
  stock_inicial INT UNSIGNED NOT NULL DEFAULT 0,
  capacidad_maxima INT UNSIGNED NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_almacen_ubicacion (ubicacion_id),
  CONSTRAINT fk_almacen_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicacion (id),
  CONSTRAINT ck_almacen_tipo CHECK (tipo IN ('CENTRAL', 'INTERMEDIO')),
  CONSTRAINT ck_almacen_capacidad CHECK (
    (tipo = 'CENTRAL' AND capacidad_maxima IS NULL AND stock_inicial = 0)
    OR (tipo = 'INTERMEDIO' AND capacidad_maxima IS NOT NULL AND capacidad_maxima = 1000
        AND stock_inicial <= capacidad_maxima)
  )
) ENGINE=InnoDB;

CREATE TABLE tipo_vehiculo (
  codigo VARCHAR(12) NOT NULL,
  prefijo CHAR(2) NOT NULL,
  capacidad_defecto INT UNSIGNED NOT NULL,
  velocidad_kmh_defecto DECIMAL(8,2) NOT NULL,
  costo_km_defecto DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (codigo),
  UNIQUE KEY uq_tipo_vehiculo_prefijo (prefijo),
  CONSTRAINT ck_tipo_vehiculo_capacidad CHECK (capacidad_defecto > 0),
  CONSTRAINT ck_tipo_vehiculo_velocidad CHECK (velocidad_kmh_defecto > 0),
  CONSTRAINT ck_tipo_vehiculo_costo CHECK (costo_km_defecto >= 0)
) ENGINE=InnoDB;

CREATE TABLE vehiculo (
  id CHAR(4) NOT NULL,
  tipo_codigo VARCHAR(12) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_vehiculo_tipo (tipo_codigo),
  CONSTRAINT fk_vehiculo_tipo FOREIGN KEY (tipo_codigo) REFERENCES tipo_vehiculo (codigo)
) ENGINE=InnoDB;

CREATE TABLE archivo_fuente (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tipo VARCHAR(16) NOT NULL,
  nombre VARCHAR(120) NOT NULL,
  periodo_inicio DATE NOT NULL,
  periodo_fin DATE NOT NULL,
  sha256 CHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_archivo_fuente_nombre (nombre),
  CONSTRAINT ck_archivo_fuente_tipo CHECK (tipo IN ('VENTAS', 'BLOQUEOS', 'MANTENIMIENTO')),
  CONSTRAINT ck_archivo_fuente_periodo CHECK (periodo_fin >= periodo_inicio)
) ENGINE=InnoDB;

-- P-00001 se repite en distintos meses: el identificador real es archivo + correlativo.
CREATE TABLE pedido (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  archivo_id BIGINT UNSIGNED NOT NULL,
  numero_linea INT UNSIGNED NOT NULL,
  codigo VARCHAR(64) NOT NULL,
  cliente_id VARCHAR(32) NOT NULL,
  destino_id BIGINT UNSIGNED NOT NULL,
  cantidad INT UNSIGNED NOT NULL,
  registrado_en DATETIME NOT NULL,
  plazo_horas INT UNSIGNED NOT NULL,
  limite_en DATETIME NOT NULL,
  registro_original VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_pedido_archivo_linea (archivo_id, numero_linea),
  UNIQUE KEY uq_pedido_archivo_codigo (archivo_id, codigo),
  KEY ix_pedido_registro (registrado_en),
  KEY ix_pedido_limite (limite_en),
  KEY ix_pedido_cliente (cliente_id),
  KEY ix_pedido_destino (destino_id),
  CONSTRAINT fk_pedido_archivo FOREIGN KEY (archivo_id) REFERENCES archivo_fuente (id),
  CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
  CONSTRAINT fk_pedido_destino FOREIGN KEY (destino_id) REFERENCES ubicacion (id),
  CONSTRAINT ck_pedido_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_pedido_plazo CHECK (plazo_horas > 0),
  CONSTRAINT ck_pedido_limite CHECK (limite_en > registrado_en)
) ENGINE=InnoDB;

CREATE TABLE bloqueo (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  archivo_id BIGINT UNSIGNED NOT NULL,
  numero_linea INT UNSIGNED NOT NULL,
  inicio DATETIME NOT NULL,
  fin DATETIME NOT NULL,
  registro_original VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_bloqueo_archivo_linea (archivo_id, numero_linea),
  KEY ix_bloqueo_vigencia (inicio, fin),
  CONSTRAINT fk_bloqueo_archivo FOREIGN KEY (archivo_id) REFERENCES archivo_fuente (id),
  CONSTRAINT ck_bloqueo_vigencia CHECK (fin > inicio)
) ENGINE=InnoDB;

-- La poligonal conserva el orden original; Bloqueo.dePoligonal expande sus tramos.
CREATE TABLE bloqueo_vertice (
  bloqueo_id BIGINT UNSIGNED NOT NULL,
  orden SMALLINT UNSIGNED NOT NULL,
  ubicacion_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (bloqueo_id, orden),
  KEY ix_bloqueo_vertice_ubicacion (ubicacion_id),
  CONSTRAINT fk_bloqueo_vertice_bloqueo FOREIGN KEY (bloqueo_id) REFERENCES bloqueo (id),
  CONSTRAINT fk_bloqueo_vertice_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicacion (id),
  CONSTRAINT ck_bloqueo_vertice_orden CHECK (orden > 0)
) ENGINE=InnoDB;

-- Relacion fecha-vehiculo del PlanMantenimiento; un vehiculo puede aparecer muchos dias.
CREATE TABLE mantenimiento_programado (
  archivo_id BIGINT UNSIGNED NOT NULL,
  numero_linea INT UNSIGNED NOT NULL,
  fecha DATE NOT NULL,
  vehiculo_id CHAR(4) NOT NULL,
  registro_original VARCHAR(80) NOT NULL,
  PRIMARY KEY (archivo_id, numero_linea),
  UNIQUE KEY uq_mantenimiento_fecha_vehiculo (fecha, vehiculo_id),
  KEY ix_mantenimiento_vehiculo (vehiculo_id),
  CONSTRAINT fk_mantenimiento_archivo FOREIGN KEY (archivo_id) REFERENCES archivo_fuente (id),
  CONSTRAINT fk_mantenimiento_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id)
) ENGINE=InnoDB;

-- Ejecuciones y sus estados; las entradas pueden compartirse entre corridas.
CREATE TABLE ejecucion (
  id CHAR(36) NOT NULL,
  escenario VARCHAR(20) NOT NULL,
  algoritmo VARCHAR(12) NOT NULL,
  inicio_simulado DATETIME NOT NULL,
  horizonte_dias SMALLINT UNSIGNED NOT NULL,
  semilla BIGINT NULL,
  parametros_json JSON NULL,
  estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
  reloj_simulado DATETIME NULL,
  creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  mensaje_error VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY ix_ejecucion_estado (estado, creada_en),
  CONSTRAINT ck_ejecucion_escenario CHECK (escenario IN ('OPERACION_DIARIA', 'PERIODO_5_DIAS', 'COLAPSO')),
  CONSTRAINT ck_ejecucion_algoritmo CHECK (algoritmo IN ('GRASP', 'TABU')),
  CONSTRAINT ck_ejecucion_estado CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'TERMINADA', 'COLAPSADA', 'FALLIDA', 'CANCELADA')),
  CONSTRAINT ck_ejecucion_horizonte CHECK (horizonte_dias > 0)
) ENGINE=InnoDB;

CREATE TABLE ejecucion_archivo (
  ejecucion_id CHAR(36) NOT NULL,
  archivo_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (ejecucion_id, archivo_id),
  KEY ix_ejecucion_archivo_archivo (archivo_id),
  CONSTRAINT fk_ejecucion_archivo_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_ejecucion_archivo_archivo FOREIGN KEY (archivo_id) REFERENCES archivo_fuente (id)
) ENGINE=InnoDB;

-- ConfiguracionFlota puede cambiar durante una corrida: se guarda una instantanea.
CREATE TABLE ejecucion_tipo_vehiculo (
  ejecucion_id CHAR(36) NOT NULL,
  tipo_codigo VARCHAR(12) NOT NULL,
  capacidad INT UNSIGNED NOT NULL,
  velocidad_kmh DECIMAL(8,2) NOT NULL,
  costo_km DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (ejecucion_id, tipo_codigo),
  KEY ix_ejecucion_tipo_codigo (tipo_codigo),
  CONSTRAINT fk_ejecucion_tipo_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_ejecucion_tipo_tipo FOREIGN KEY (tipo_codigo) REFERENCES tipo_vehiculo (codigo),
  CONSTRAINT ck_ejecucion_tipo_capacidad CHECK (capacidad > 0),
  CONSTRAINT ck_ejecucion_tipo_velocidad CHECK (velocidad_kmh > 0),
  CONSTRAINT ck_ejecucion_tipo_costo CHECK (costo_km >= 0)
) ENGINE=InnoDB;

CREATE TABLE ejecucion_almacen (
  ejecucion_id CHAR(36) NOT NULL,
  almacen_id VARCHAR(32) NOT NULL,
  stock_actual INT UNSIGNED NULL,
  actualizado_en DATETIME NOT NULL,
  PRIMARY KEY (ejecucion_id, almacen_id),
  KEY ix_ejecucion_almacen_almacen (almacen_id),
  CONSTRAINT fk_ejecucion_almacen_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_ejecucion_almacen_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id)
) ENGINE=InnoDB;

CREATE TABLE ejecucion_vehiculo (
  ejecucion_id CHAR(36) NOT NULL,
  vehiculo_id CHAR(4) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
  ubicacion_id BIGINT UNSIGNED NOT NULL,
  disponible_desde DATETIME NULL,
  carga_a_bordo INT UNSIGNED NOT NULL DEFAULT 0,
  actualizado_en DATETIME NOT NULL,
  PRIMARY KEY (ejecucion_id, vehiculo_id),
  KEY ix_ejecucion_vehiculo_vehiculo (vehiculo_id),
  KEY ix_ejecucion_vehiculo_ubicacion (ubicacion_id),
  CONSTRAINT fk_ejecucion_vehiculo_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_ejecucion_vehiculo_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id),
  CONSTRAINT fk_ejecucion_vehiculo_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicacion (id),
  CONSTRAINT ck_ejecucion_vehiculo_estado CHECK (estado IN ('DISPONIBLE', 'EN_RUTA', 'AVERIADO', 'EN_MANTENIMIENTO'))
) ENGINE=InnoDB;

-- Cada parte de un pedido pertenece a una corrida y a su pedido original de archivo.
CREATE TABLE pedido_parte (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ejecucion_id CHAR(36) NOT NULL,
  pedido_id BIGINT UNSIGNED NOT NULL,
  codigo VARCHAR(80) NOT NULL,
  cantidad INT UNSIGNED NOT NULL,
  paga_acondicionamiento BOOLEAN NOT NULL DEFAULT TRUE,
  estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
  PRIMARY KEY (id),
  UNIQUE KEY uq_pedido_parte_codigo (ejecucion_id, pedido_id, codigo),
  UNIQUE KEY uq_pedido_parte_ejecucion (id, ejecucion_id),
  KEY ix_pedido_parte_pedido (pedido_id),
  CONSTRAINT fk_pedido_parte_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_pedido_parte_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id),
  CONSTRAINT ck_pedido_parte_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_pedido_parte_estado CHECK (estado IN ('PENDIENTE', 'ASIGNADO', 'ENTREGADO', 'VENCIDO'))
) ENGINE=InnoDB;

CREATE TABLE plan (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ejecucion_id CHAR(36) NOT NULL,
  version INT UNSIGNED NOT NULL,
  algoritmo VARCHAR(12) NOT NULL,
  creado_en DATETIME NOT NULL,
  motivo VARCHAR(120) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_plan_version (ejecucion_id, version),
  UNIQUE KEY uq_plan_ejecucion (id, ejecucion_id),
  CONSTRAINT fk_plan_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT ck_plan_version CHECK (version > 0),
  CONSTRAINT ck_plan_algoritmo CHECK (algoritmo IN ('GRASP', 'TABU'))
) ENGINE=InnoDB;

CREATE TABLE ruta (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id BIGINT UNSIGNED NOT NULL,
  ejecucion_id CHAR(36) NOT NULL,
  codigo VARCHAR(80) NOT NULL,
  orden_plan INT UNSIGNED NOT NULL,
  almacen_id VARCHAR(32) NOT NULL,
  vehiculo_id CHAR(4) NOT NULL,
  ya_cargado BOOLEAN NOT NULL DEFAULT FALSE,
  distancia_km DECIMAL(12,3) NOT NULL DEFAULT 0,
  costo_total DECIMAL(14,4) NOT NULL DEFAULT 0,
  duracion_horas DECIMAL(12,4) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_ruta_plan_codigo (plan_id, codigo),
  UNIQUE KEY uq_ruta_plan_orden (plan_id, orden_plan),
  UNIQUE KEY uq_ruta_plan_ejecucion (id, plan_id, ejecucion_id),
  UNIQUE KEY uq_ruta_ejecucion (id, ejecucion_id),
  KEY ix_ruta_almacen (almacen_id),
  KEY ix_ruta_vehiculo (vehiculo_id),
  CONSTRAINT fk_ruta_plan FOREIGN KEY (plan_id, ejecucion_id) REFERENCES plan (id, ejecucion_id),
  CONSTRAINT fk_ruta_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
  CONSTRAINT fk_ruta_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id),
  CONSTRAINT ck_ruta_distancia CHECK (distancia_km >= 0),
  CONSTRAINT ck_ruta_costo CHECK (costo_total >= 0),
  CONSTRAINT ck_ruta_duracion CHECK (duracion_horas >= 0)
) ENGINE=InnoDB;

-- Tabla intermedia Ruta-PedidoParte: conserva el orden y evita repetir una parte en un plan.
CREATE TABLE ruta_parada (
  ruta_id BIGINT UNSIGNED NOT NULL,
  plan_id BIGINT UNSIGNED NOT NULL,
  ejecucion_id CHAR(36) NOT NULL,
  orden INT UNSIGNED NOT NULL,
  pedido_parte_id BIGINT UNSIGNED NOT NULL,
  llegada_estimada DATETIME NULL,
  PRIMARY KEY (ruta_id, orden),
  UNIQUE KEY uq_ruta_parada_plan_parte (plan_id, pedido_parte_id),
  KEY ix_ruta_parada_ruta_scope (ruta_id, plan_id, ejecucion_id),
  KEY ix_ruta_parada_parte_scope (pedido_parte_id, ejecucion_id),
  CONSTRAINT fk_ruta_parada_ruta FOREIGN KEY (ruta_id, plan_id, ejecucion_id)
    REFERENCES ruta (id, plan_id, ejecucion_id),
  CONSTRAINT fk_ruta_parada_parte FOREIGN KEY (pedido_parte_id, ejecucion_id)
    REFERENCES pedido_parte (id, ejecucion_id),
  CONSTRAINT ck_ruta_parada_orden CHECK (orden > 0)
) ENGINE=InnoDB;

CREATE TABLE plan_pedido_no_asignado (
  plan_id BIGINT UNSIGNED NOT NULL,
  ejecucion_id CHAR(36) NOT NULL,
  pedido_parte_id BIGINT UNSIGNED NOT NULL,
  motivo VARCHAR(160) NULL,
  PRIMARY KEY (plan_id, pedido_parte_id),
  KEY ix_plan_no_asignado_plan_scope (plan_id, ejecucion_id),
  KEY ix_plan_no_asignado_parte_scope (pedido_parte_id, ejecucion_id),
  CONSTRAINT fk_plan_no_asignado_plan FOREIGN KEY (plan_id, ejecucion_id)
    REFERENCES plan (id, ejecucion_id),
  CONSTRAINT fk_plan_no_asignado_parte FOREIGN KEY (pedido_parte_id, ejecucion_id)
    REFERENCES pedido_parte (id, ejecucion_id)
) ENGINE=InnoDB;

-- La entrega existe solo cuando Simulador confirma una llegada real al cliente.
CREATE TABLE entrega (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ejecucion_id CHAR(36) NOT NULL,
  pedido_parte_id BIGINT UNSIGNED NOT NULL,
  ruta_id BIGINT UNSIGNED NULL,
  vehiculo_id CHAR(4) NOT NULL,
  almacen_id VARCHAR(32) NOT NULL,
  llegada DATETIME NOT NULL,
  kilometros DECIMAL(12,3) NOT NULL,
  costo DECIMAL(14,4) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_entrega_parte (pedido_parte_id),
  KEY ix_entrega_ejecucion_llegada (ejecucion_id, llegada),
  KEY ix_entrega_ruta_scope (ruta_id, ejecucion_id),
  KEY ix_entrega_vehiculo (vehiculo_id),
  KEY ix_entrega_almacen (almacen_id),
  CONSTRAINT fk_entrega_parte FOREIGN KEY (pedido_parte_id, ejecucion_id)
    REFERENCES pedido_parte (id, ejecucion_id),
  CONSTRAINT fk_entrega_ruta FOREIGN KEY (ruta_id, ejecucion_id)
    REFERENCES ruta (id, ejecucion_id),
  CONSTRAINT fk_entrega_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id),
  CONSTRAINT fk_entrega_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
  CONSTRAINT ck_entrega_kilometros CHECK (kilometros >= 0),
  CONSTRAINT ck_entrega_costo CHECK (costo >= 0)
) ENGINE=InnoDB;

CREATE TABLE averia (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ejecucion_id CHAR(36) NOT NULL,
  vehiculo_id CHAR(4) NOT NULL,
  tipo VARCHAR(6) NOT NULL,
  instante DATETIME NOT NULL,
  ubicacion_id BIGINT UNSIGNED NULL,
  fin_permanencia DATETIME NOT NULL,
  reingreso DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY ix_averia_ejecucion_instante (ejecucion_id, instante),
  KEY ix_averia_vehiculo (vehiculo_id),
  KEY ix_averia_ubicacion (ubicacion_id),
  CONSTRAINT fk_averia_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_averia_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id),
  CONSTRAINT fk_averia_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicacion (id),
  CONSTRAINT ck_averia_tipo CHECK (tipo IN ('TIPO_1', 'TIPO_2', 'TIPO_3')),
  CONSTRAINT ck_averia_tiempos CHECK (fin_permanencia >= instante AND reingreso >= fin_permanencia)
) ENGINE=InnoDB;

CREATE TABLE medicion_planificacion (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ejecucion_id CHAR(36) NOT NULL,
  plan_id BIGINT UNSIGNED NULL,
  instante DATETIME NOT NULL,
  pedidos_en_cola INT UNSIGNED NOT NULL,
  productos_en_cola INT UNSIGNED NOT NULL,
  milisegundos BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  KEY ix_medicion_ejecucion_instante (ejecucion_id, instante),
  KEY ix_medicion_plan_scope (plan_id, ejecucion_id),
  CONSTRAINT fk_medicion_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_medicion_plan FOREIGN KEY (plan_id, ejecucion_id)
    REFERENCES plan (id, ejecucion_id)
) ENGINE=InnoDB;

CREATE TABLE resumen_simulacion (
  ejecucion_id CHAR(36) NOT NULL,
  fin_simulado DATETIME NOT NULL,
  pedidos_recibidos INT UNSIGNED NOT NULL,
  pedidos_entregados INT UNSIGNED NOT NULL,
  pedidos_pendientes INT UNSIGNED NOT NULL,
  productos_recibidos INT UNSIGNED NOT NULL,
  productos_entregados INT UNSIGNED NOT NULL,
  productos_vencidos INT UNSIGNED NOT NULL,
  pedidos_originales_completos INT UNSIGNED NOT NULL,
  pedidos_originales_en_plazo INT UNSIGNED NOT NULL,
  entregas_en_plazo INT UNSIGNED NOT NULL,
  distancia_total_km DECIMAL(14,3) NOT NULL,
  costo_total DECIMAL(16,4) NOT NULL,
  iteraciones_planificacion INT UNSIGNED NOT NULL,
  milisegundos_computo BIGINT UNSIGNED NOT NULL,
  pedido_colapso_id BIGINT UNSIGNED NULL,
  instante_colapso DATETIME NULL,
  PRIMARY KEY (ejecucion_id),
  KEY ix_resumen_pedido_colapso (pedido_colapso_id),
  CONSTRAINT fk_resumen_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id),
  CONSTRAINT fk_resumen_pedido_colapso FOREIGN KEY (pedido_colapso_id) REFERENCES pedido (id),
  CONSTRAINT ck_resumen_distancia CHECK (distancia_total_km >= 0),
  CONSTRAINT ck_resumen_costo CHECK (costo_total >= 0)
) ENGINE=InnoDB;

CREATE TABLE resumen_dia (
  ejecucion_id CHAR(36) NOT NULL,
  fecha DATE NOT NULL,
  pedidos_recibidos INT UNSIGNED NOT NULL DEFAULT 0,
  entregas INT UNSIGNED NOT NULL DEFAULT 0,
  cola_al_cierre INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (ejecucion_id, fecha),
  CONSTRAINT fk_resumen_dia_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id)
) ENGINE=InnoDB;

CREATE TABLE pedido_vencido (
  ejecucion_id CHAR(36) NOT NULL,
  pedido_parte_id BIGINT UNSIGNED NOT NULL,
  destino_inalcanzable BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (ejecucion_id, pedido_parte_id),
  KEY ix_pedido_vencido_parte_scope (pedido_parte_id, ejecucion_id),
  CONSTRAINT fk_pedido_vencido_parte FOREIGN KEY (pedido_parte_id, ejecucion_id)
    REFERENCES pedido_parte (id, ejecucion_id)
) ENGINE=InnoDB;

-- Catalogos coherentes con DatosCaso y TipoVehiculo (codigo/paqrap).
INSERT INTO ubicacion (id, x, y) VALUES
  (1, 27, 14), (2, 12, 38), (3, 57, 27),
  (4, 26, 15), (5, 9, 22), (6, 32, 21), (7, 59, 48),
  (8, 46, 13), (9, 48, 29), (10, 21, 29), (11, 47, 24),
  (12, 10, 27), (13, 23, 39), (14, 67, 37), (15, 67, 22),
  (16, 15, 40), (17, 27, 40);

INSERT INTO almacen (id, tipo, ubicacion_id, stock_inicial, capacidad_maxima) VALUES
  ('ALM-CENTRAL', 'CENTRAL', 1, 0, NULL),
  ('ALM-NOR-OESTE', 'INTERMEDIO', 2, 1000, 1000),
  ('ALM-ESTE', 'INTERMEDIO', 3, 1000, 1000);

INSERT INTO tipo_vehiculo
  (codigo, prefijo, capacidad_defecto, velocidad_kmh_defecto, costo_km_defecto) VALUES
  ('AUTO', 'TA', 24, 40.00, 8.00),
  ('MOTO', 'TM', 8, 25.00, 6.00),
  ('BICICLETA', 'TB', 4, 12.00, 3.00);

INSERT INTO vehiculo (id, tipo_codigo) VALUES
  ('TA01','AUTO'),('TA02','AUTO'),('TA03','AUTO'),('TA04','AUTO'),('TA05','AUTO'),
  ('TA06','AUTO'),('TA07','AUTO'),('TA08','AUTO'),('TA09','AUTO'),('TA10','AUTO'),
  ('TM01','MOTO'),('TM02','MOTO'),('TM03','MOTO'),('TM04','MOTO'),('TM05','MOTO'),
  ('TM06','MOTO'),('TM07','MOTO'),('TM08','MOTO'),('TM09','MOTO'),('TM10','MOTO'),
  ('TM11','MOTO'),('TM12','MOTO'),('TM13','MOTO'),('TM14','MOTO'),('TM15','MOTO'),
  ('TB01','BICICLETA'),('TB02','BICICLETA'),('TB03','BICICLETA'),('TB04','BICICLETA'),
  ('TB05','BICICLETA'),('TB06','BICICLETA'),('TB07','BICICLETA'),('TB08','BICICLETA'),
  ('TB09','BICICLETA'),('TB10','BICICLETA'),('TB11','BICICLETA'),('TB12','BICICLETA');

-- Muestra original: primeras 10 ventas, 2 bloqueos y 3 mantenimientos.
-- La columna registro_original conserva cada linea exactamente como figura en el TXT.
INSERT INTO archivo_fuente (id, tipo, nombre, periodo_inicio, periodo_fin) VALUES
  (1, 'VENTAS', 'ventas.202609.txt', '2026-09-01', '2026-09-30'),
  (2, 'BLOQUEOS', 'bloqueo.2609.txt', '2026-09-01', '2026-09-30'),
  (3, 'MANTENIMIENTO', 'mant.preventivo.09.10.txt', '2026-09-01', '2026-10-31');

INSERT INTO cliente (id) VALUES
  ('c0497'),('c6006'),('c3048'),('c1526'),('c2084'),
  ('c4477'),('c6194'),('c9592'),('c3891'),('c5687');

INSERT INTO pedido
  (id, archivo_id, numero_linea, codigo, cliente_id, destino_id,
   cantidad, registrado_en, plazo_horas, limite_en, registro_original) VALUES
  (1,1,1,'P-00001','c0497',4,6,'2026-09-01 00:26:00',36,'2026-09-02 12:26:00','01d00h26m:26,15,c0497,06,36'),
  (2,1,2,'P-00002','c6006',5,6,'2026-09-01 00:44:00',8,'2026-09-01 08:44:00','01d00h44m:09,22,c6006,06,08'),
  (3,1,3,'P-00003','c3048',6,5,'2026-09-01 00:52:00',36,'2026-09-02 12:52:00','01d00h52m:32,21,c3048,05,36'),
  (4,1,4,'P-00004','c1526',7,3,'2026-09-01 01:15:00',18,'2026-09-01 19:15:00','01d01h15m:59,48,c1526,03,18'),
  (5,1,5,'P-00005','c2084',8,7,'2026-09-01 01:18:00',18,'2026-09-01 19:18:00','01d01h18m:46,13,c2084,07,18'),
  (6,1,6,'P-00006','c4477',9,2,'2026-09-01 01:20:00',4,'2026-09-01 05:20:00','01d01h20m:48,29,c4477,02,04'),
  (7,1,7,'P-00007','c6194',10,6,'2026-09-01 01:27:00',36,'2026-09-02 13:27:00','01d01h27m:21,29,c6194,06,36'),
  (8,1,8,'P-00008','c9592',11,9,'2026-09-01 01:40:00',12,'2026-09-01 13:40:00','01d01h40m:47,24,c9592,09,12'),
  (9,1,9,'P-00009','c3891',12,2,'2026-09-01 01:57:00',4,'2026-09-01 05:57:00','01d01h57m:10,27,c3891,02,04'),
  (10,1,10,'P-00010','c5687',13,6,'2026-09-01 01:57:00',36,'2026-09-02 13:57:00','01d01h57m:23,39,c5687,06,36');

INSERT INTO bloqueo (id, archivo_id, numero_linea, inicio, fin, registro_original) VALUES
  (1,2,1,'2026-09-01 00:00:00','2026-09-01 03:49:00','01d00h00m-01d03h49m:67,37,67,22'),
  (2,2,2,'2026-09-01 00:13:00','2026-09-01 03:57:00','01d00h13m-01d03h57m:67,37,67,22');

INSERT INTO bloqueo_vertice (bloqueo_id, orden, ubicacion_id) VALUES
  (1,1,14),(1,2,15),(2,1,14),(2,2,15);

INSERT INTO mantenimiento_programado
  (archivo_id, numero_linea, fecha, vehiculo_id, registro_original) VALUES
  (3,1,'2026-09-01','TA01','20260901:TA01'),
  (3,2,'2026-09-02','TB01','20260902:TB01'),
  (3,3,'2026-09-03','TM07','20260903:TM07');

-- Comprobacion de la muestra: 37 vehiculos, 10 pedidos, 2 bloqueos, 3 mantenimientos.
SELECT (SELECT COUNT(*) FROM vehiculo) AS vehiculos,
       (SELECT COUNT(*) FROM pedido) AS pedidos,
       (SELECT COUNT(*) FROM bloqueo) AS bloqueos,
       (SELECT COUNT(*) FROM mantenimiento_programado) AS mantenimientos;
