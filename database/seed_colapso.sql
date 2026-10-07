-- DEMO sintetica de estres: plazos de una hora y destinos lejanos.
-- El resultado de colapso debe comprobarse ejecutando el simulador; no se inserta artificialmente.
USE paqrap;
INSERT INTO cliente(id) VALUES ('DEMO-COLAPSO') ON DUPLICATE KEY UPDATE id=id;
INSERT INTO archivo_fuente(tipo,nombre,periodo_inicio,periodo_fin)
VALUES ('VENTAS','demo.colapso.202903','2029-03-01','2029-03-31'),
       ('BLOQUEOS','demo.bloqueos.colapso.202903','2029-03-01','2029-03-31'),
       ('MANTENIMIENTO','demo.mant.colapso.202903','2029-03-01','2029-03-31')
ON DUPLICATE KEY UPDATE nombre=nombre;
SET @ventas_colapso=(SELECT id FROM archivo_fuente WHERE nombre='demo.colapso.202903');
SET @bloqueo_colapso=(SELECT id FROM archivo_fuente WHERE nombre='demo.bloqueos.colapso.202903');
SET @mant_colapso=(SELECT id FROM archivo_fuente WHERE nombre='demo.mant.colapso.202903');
INSERT INTO ubicacion(x,y) VALUES (64,40),(65,40),(66,40),(67,40),(68,40),
  (64,42),(65,42),(66,42),(67,42),(68,42),(45,30),(45,38)
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_colapso,s.n,CONCAT('X-',LPAD(s.n,3,'0')),'DEMO-COLAPSO',u.id,24,
       DATE_ADD('2029-03-01 08:00:00',INTERVAL s.minuto MINUTE),1,
       DATE_ADD('2029-03-01 09:00:00',INTERVAL s.minuto MINUTE),
       CONCAT('DEMO estres pedido ',s.n)
FROM (SELECT 1 n,64 x,40 y,0 minuto UNION ALL SELECT 2,65,40,1 UNION ALL
      SELECT 3,66,40,2 UNION ALL SELECT 4,67,40,3 UNION ALL
      SELECT 5,68,40,4 UNION ALL SELECT 6,64,42,5 UNION ALL
      SELECT 7,65,42,6 UNION ALL SELECT 8,66,42,7 UNION ALL
      SELECT 9,67,42,8 UNION ALL SELECT 10,68,42,9) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;
INSERT INTO bloqueo(archivo_id,numero_linea,inicio,fin,registro_original)
VALUES (@bloqueo_colapso,1,'2029-03-01 08:00:00','2029-03-01 12:00:00',
        'DEMO tramo (45,30)-(45,38)')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO bloqueo_vertice(bloqueo_id,orden,ubicacion_id)
SELECT b.id,v.orden,u.id FROM
  (SELECT 1 orden,45 x,30 y UNION ALL SELECT 2,45,38) v
JOIN bloqueo b ON b.archivo_id=@bloqueo_colapso AND b.numero_linea=1
JOIN ubicacion u ON u.x=v.x AND u.y=v.y
ON DUPLICATE KEY UPDATE ubicacion_id=ubicacion_id;
INSERT INTO mantenimiento_programado(archivo_id,numero_linea,fecha,vehiculo_id,registro_original)
VALUES (@mant_colapso,1,'2029-03-01','TA01','DEMO TA01 fuera de servicio'),
       (@mant_colapso,2,'2029-03-01','TM01','DEMO TM01 fuera de servicio')
ON DUPLICATE KEY UPDATE vehiculo_id=vehiculo_id;
