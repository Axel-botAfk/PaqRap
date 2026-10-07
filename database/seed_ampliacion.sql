-- Ampliacion sintetica e idempotente de los tres escenarios de la beta.
-- Se aplica despues de las semillas base y tambien sobre un volumen ya existente.
USE paqrap;

CREATE TEMPORARY TABLE demo_numeros (n INT PRIMARY KEY);
INSERT INTO demo_numeros(n)
SELECT unidades.d + decenas.d * 10 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
      SELECT 8 UNION ALL SELECT 9) unidades
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
            SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) decenas
WHERE unidades.d + decenas.d * 10 + 1 BETWEEN 9 AND 75;

-- Operacion diaria: 32 pedidos nuevos, repartidos entre zonas de la ciudad.
SET @ventas_diario = (SELECT id FROM archivo_fuente WHERE nombre='demo.diario.202901');
INSERT INTO ubicacion(x,y)
SELECT 5 + MOD(n * 7, 61), 5 + MOD(n * 11, 41)
FROM demo_numeros WHERE n BETWEEN 9 AND 40
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_diario,s.n,CONCAT('D-',LPAD(s.n,3,'0')),'DEMO-DIARIO',u.id,
       3 + MOD(s.n,8),s.registro,18,DATE_ADD(s.registro,INTERVAL 18 HOUR),
       CONCAT('DEMO diario ampliado pedido ',s.n)
FROM (SELECT n,5 + MOD(n * 7,61) x,5 + MOD(n * 11,41) y,
             DATE_ADD('2029-01-15 08:00:00',INTERVAL (n-9)*15 MINUTE) registro
      FROM demo_numeros WHERE n BETWEEN 9 AND 40) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

-- Cinco dias: 12 pedidos adicionales por jornada, 60 en total.
SET @ventas_cinco = (SELECT id FROM archivo_fuente WHERE nombre='demo.cinco.202902');
INSERT INTO ubicacion(x,y)
SELECT 5 + MOD(n * 7,61),5 + MOD(n * 11,41)
FROM demo_numeros WHERE n BETWEEN 16 AND 75
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_cinco,s.n,CONCAT('C-',LPAD(s.n,3,'0')),'DEMO-CINCO',u.id,
       3 + MOD(s.n,9),s.registro,20,DATE_ADD(s.registro,INTERVAL 20 HOUR),
       CONCAT('DEMO cinco dias ampliado pedido ',s.n)
FROM (SELECT n,5 + MOD(n * 7,61) x,5 + MOD(n * 11,41) y,
             DATE_ADD('2029-02-01 08:00:00',
                      INTERVAL (FLOOR((n-16)/12)*1440 + MOD(n-16,12)*45) MINUTE) registro
      FROM demo_numeros WHERE n BETWEEN 16 AND 75) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

-- Estres: 25 pedidos nuevos con plazos de una hora y destinos lejanos.
SET @ventas_colapso = (SELECT id FROM archivo_fuente WHERE nombre='demo.colapso.202903');
INSERT INTO ubicacion(x,y)
SELECT 58 + MOD(n * 3,11),34 + MOD(n * 5,13)
FROM demo_numeros WHERE n BETWEEN 11 AND 35
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_colapso,s.n,CONCAT('X-',LPAD(s.n,3,'0')),'DEMO-COLAPSO',u.id,
       24,s.registro,1,DATE_ADD(s.registro,INTERVAL 1 HOUR),
       CONCAT('DEMO estres ampliado pedido ',s.n)
FROM (SELECT n,58 + MOD(n * 3,11) x,34 + MOD(n * 5,13) y,
             DATE_ADD('2029-03-01 08:00:00',INTERVAL (n-1) MINUTE) registro
      FROM demo_numeros WHERE n BETWEEN 11 AND 35) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

DROP TEMPORARY TABLE demo_numeros;
