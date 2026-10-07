-- DEMO sintetica: un dia con pedidos, bloqueo y mantenimiento.
-- Ejecutar despues de paqrap_mysql.sql; idempotente para esta muestra.
USE paqrap;
INSERT INTO cliente(id) VALUES ('DEMO-DIARIO') ON DUPLICATE KEY UPDATE id=id;
INSERT INTO archivo_fuente(tipo,nombre,periodo_inicio,periodo_fin)
VALUES ('VENTAS','demo.diario.202901','2029-01-01','2029-01-31'),
       ('BLOQUEOS','demo.bloqueos.diario.202901','2029-01-01','2029-01-31'),
       ('MANTENIMIENTO','demo.mant.diario.202901','2029-01-01','2029-01-31')
ON DUPLICATE KEY UPDATE nombre=nombre;
SET @ventas_diario = (SELECT id FROM archivo_fuente WHERE nombre='demo.diario.202901');
SET @bloqueo_diario = (SELECT id FROM archivo_fuente WHERE nombre='demo.bloqueos.diario.202901');
SET @mant_diario = (SELECT id FROM archivo_fuente WHERE nombre='demo.mant.diario.202901');
INSERT INTO ubicacion(x,y) VALUES (17,16),(22,19),(31,23),(38,16),
  (47,20),(51,31),(61,36),(14,31),(35,25),(35,29)
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_diario,s.n,CONCAT('D-',LPAD(s.n,3,'0')),'DEMO-DIARIO',u.id,s.cantidad,
       CAST(CONCAT('2029-01-15 ',s.hora) AS DATETIME),s.plazo,
       DATE_ADD(CAST(CONCAT('2029-01-15 ',s.hora) AS DATETIME),INTERVAL s.plazo HOUR),
       CONCAT('DEMO diario pedido ',s.n)
FROM (SELECT 1 n,17 x,16 y,6 cantidad,'08:00:00' hora,8 plazo UNION ALL
      SELECT 2,22,19,4,'08:10:00',12 UNION ALL
      SELECT 3,31,23,7,'08:30:00',10 UNION ALL
      SELECT 4,38,16,3,'09:00:00',8 UNION ALL
      SELECT 5,47,20,8,'09:20:00',12 UNION ALL
      SELECT 6,51,31,2,'10:00:00',18 UNION ALL
      SELECT 7,61,36,4,'10:30:00',20 UNION ALL
      SELECT 8,14,31,5,'11:00:00',12) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;
INSERT INTO bloqueo(archivo_id,numero_linea,inicio,fin,registro_original)
VALUES (@bloqueo_diario,1,'2029-01-15 09:00:00','2029-01-15 12:00:00',
        'DEMO bloqueo horizontal (35,25)-(35,29)')
ON DUPLICATE KEY UPDATE id=id;
SET @bloqueo_diario_id=(SELECT id FROM bloqueo WHERE archivo_id=@bloqueo_diario AND numero_linea=1);
INSERT INTO bloqueo_vertice(bloqueo_id,orden,ubicacion_id)
SELECT @bloqueo_diario_id,1,id FROM ubicacion WHERE x=35 AND y=25
ON DUPLICATE KEY UPDATE ubicacion_id=ubicacion_id;
INSERT INTO bloqueo_vertice(bloqueo_id,orden,ubicacion_id)
SELECT @bloqueo_diario_id,2,id FROM ubicacion WHERE x=35 AND y=29
ON DUPLICATE KEY UPDATE ubicacion_id=ubicacion_id;
INSERT INTO mantenimiento_programado(archivo_id,numero_linea,fecha,vehiculo_id,registro_original)
VALUES (@mant_diario,1,'2029-01-15','TM01','DEMO TM01 fuera de servicio')
ON DUPLICATE KEY UPDATE vehiculo_id=vehiculo_id;
