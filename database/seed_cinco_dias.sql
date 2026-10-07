-- DEMO sintetica: cinco dias con demanda diaria, dos bloqueos y mantenimiento.
USE paqrap;
INSERT INTO cliente(id) VALUES ('DEMO-CINCO') ON DUPLICATE KEY UPDATE id=id;
INSERT INTO archivo_fuente(tipo,nombre,periodo_inicio,periodo_fin)
VALUES ('VENTAS','demo.cinco.202902','2029-02-01','2029-02-28'),
       ('BLOQUEOS','demo.bloqueos.cinco.202902','2029-02-01','2029-02-28'),
       ('MANTENIMIENTO','demo.mant.cinco.202902','2029-02-01','2029-02-28')
ON DUPLICATE KEY UPDATE nombre=nombre;
SET @ventas_cinco=(SELECT id FROM archivo_fuente WHERE nombre='demo.cinco.202902');
SET @bloqueo_cinco=(SELECT id FROM archivo_fuente WHERE nombre='demo.bloqueos.cinco.202902');
SET @mant_cinco=(SELECT id FROM archivo_fuente WHERE nombre='demo.mant.cinco.202902');
INSERT INTO ubicacion(x,y) VALUES (18,20),(33,18),(52,28),(43,31),(23,34),
  (40,20),(44,20),(20,30),(20,34)
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_cinco,s.n,CONCAT('C-',LPAD(s.n,3,'0')),'DEMO-CINCO',u.id,s.cantidad,
       DATE_ADD('2029-02-01 08:00:00',INTERVAL ((s.dia-1)*24+s.hora) HOUR),s.plazo,
       DATE_ADD('2029-02-01 08:00:00',INTERVAL ((s.dia-1)*24+s.hora+s.plazo) HOUR),
       CONCAT('DEMO cinco dias pedido ',s.n)
FROM (SELECT 1 n,1 dia,0 hora,18 x,20 y,5 cantidad,12 plazo UNION ALL
      SELECT 2,1,2,33,18,4,18 UNION ALL SELECT 3,1,4,52,28,7,20 UNION ALL
      SELECT 4,2,0,43,31,6,12 UNION ALL SELECT 5,2,2,23,34,3,18 UNION ALL
      SELECT 6,2,4,18,20,5,20 UNION ALL SELECT 7,3,0,33,18,8,12 UNION ALL
      SELECT 8,3,2,52,28,4,18 UNION ALL SELECT 9,3,4,43,31,6,20 UNION ALL
      SELECT 10,4,0,23,34,5,12 UNION ALL SELECT 11,4,2,18,20,7,18 UNION ALL
      SELECT 12,4,4,33,18,3,20 UNION ALL SELECT 13,5,0,52,28,6,12 UNION ALL
      SELECT 14,5,2,43,31,4,18 UNION ALL SELECT 15,5,4,23,34,5,20) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;
INSERT INTO bloqueo(archivo_id,numero_linea,inicio,fin,registro_original) VALUES
  (@bloqueo_cinco,1,'2029-02-02 09:00:00','2029-02-02 13:00:00','DEMO tramo (40,20)-(44,20)'),
  (@bloqueo_cinco,2,'2029-02-04 10:00:00','2029-02-04 14:00:00','DEMO tramo (20,30)-(20,34)')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO bloqueo_vertice(bloqueo_id,orden,ubicacion_id)
SELECT b.id,v.orden,u.id FROM
  (SELECT 1 linea,1 orden,40 x,20 y UNION ALL SELECT 1,2,44,20 UNION ALL
   SELECT 2,1,20,30 UNION ALL SELECT 2,2,20,34) v
JOIN bloqueo b ON b.archivo_id=@bloqueo_cinco AND b.numero_linea=v.linea
JOIN ubicacion u ON u.x=v.x AND u.y=v.y
ON DUPLICATE KEY UPDATE ubicacion_id=ubicacion_id;
INSERT INTO mantenimiento_programado(archivo_id,numero_linea,fecha,vehiculo_id,registro_original)
VALUES (@mant_cinco,1,'2029-02-03','TA01','DEMO TA01 fuera de servicio'),
       (@mant_cinco,2,'2029-02-05','TB02','DEMO TB02 fuera de servicio')
ON DUPLICATE KEY UPDATE vehiculo_id=vehiculo_id;
