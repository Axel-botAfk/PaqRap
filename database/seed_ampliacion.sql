-- Ampliacion sintetica e idempotente de los tres escenarios.
-- Se aplica despues de las semillas base y tambien sobre un volumen ya existente.
-- Esta version conserva los 117 pedidos de la ampliacion original y agrega
-- 100 pedidos: 25 diarios, 60 de cinco dias y 15 de colapso.
-- Solo se cargan pedidos de entrada; los estados, rutas y entregas los determina
-- el simulador y no deben anticiparse en la semilla.
USE paqrap;

CREATE TEMPORARY TABLE demo_numeros (n INT PRIMARY KEY);
INSERT INTO demo_numeros(n)
SELECT unidades.d + decenas.d * 10 + centenas.d * 100 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
      SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
      SELECT 8 UNION ALL SELECT 9) unidades
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
            SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
            SELECT 8 UNION ALL SELECT 9) decenas
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1) centenas
WHERE unidades.d + decenas.d * 10 + centenas.d * 100 + 1 BETWEEN 9 AND 135;

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

-- Segunda ampliacion: demanda a distintas horas y zonas durante el dia.
-- D-041..D-065 son nuevos y no alteran los D-001..D-040 ya cargados.
INSERT INTO ubicacion(x,y)
SELECT 3 + MOD(n * 11,65),2 + MOD(n * 17,47)
FROM demo_numeros WHERE n BETWEEN 41 AND 65
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_diario,s.n,CONCAT('D-',LPAD(s.n,3,'0')),'DEMO-DIARIO',u.id,
       s.cantidad,s.registro,s.plazo,
       DATE_ADD(s.registro,INTERVAL s.plazo HOUR),
       CONCAT('DEMO diario mayor volumen pedido ',s.n)
FROM (SELECT n,3 + MOD(n * 11,65) x,2 + MOD(n * 17,47) y,
             2 + MOD(n,9) cantidad,
             DATE_ADD('2029-01-15 07:00:00',INTERVAL (n-41)*33 MINUTE) registro,
             CASE MOD(n,5)
               WHEN 0 THEN 4 WHEN 1 THEN 8 WHEN 2 THEN 12
               WHEN 3 THEN 18 ELSE 36 END plazo
      FROM demo_numeros WHERE n BETWEEN 41 AND 65) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

-- C-076..C-135: doce pedidos nuevos por cada uno de los cinco dias.
-- Las horas van de 06:30 a 20:15; los plazos son los del caso.
INSERT INTO ubicacion(x,y)
SELECT 4 + MOD(n * 13,63),3 + MOD(n * 19,45)
FROM demo_numeros WHERE n BETWEEN 76 AND 135
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_cinco,s.n,CONCAT('C-',LPAD(s.n,3,'0')),'DEMO-CINCO',u.id,
       s.cantidad,s.registro,s.plazo,
       DATE_ADD(s.registro,INTERVAL s.plazo HOUR),
       CONCAT('DEMO cinco dias mayor volumen pedido ',s.n)
FROM (SELECT n,4 + MOD(n * 13,63) x,3 + MOD(n * 19,45) y,
             1 + MOD(n,12) cantidad,
             DATE_ADD('2029-02-01 06:30:00',
                      INTERVAL (FLOOR((n-76)/12)*1440 + MOD(n-76,12)*75) MINUTE) registro,
             CASE MOD(n,5)
               WHEN 0 THEN 4 WHEN 1 THEN 8 WHEN 2 THEN 12
               WHEN 3 THEN 18 ELSE 36 END plazo
      FROM demo_numeros WHERE n BETWEEN 76 AND 135) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

-- X-036..X-050: mas demanda lejana para el escenario de estres.
-- No se registra un colapso artificial: el resultado depende del simulador.
INSERT INTO ubicacion(x,y)
SELECT 56 + MOD(n * 7,14),31 + MOD(n * 11,18)
FROM demo_numeros WHERE n BETWEEN 36 AND 50
ON DUPLICATE KEY UPDATE x=x;
INSERT INTO pedido(archivo_id,numero_linea,codigo,cliente_id,destino_id,cantidad,
                   registrado_en,plazo_horas,limite_en,registro_original)
SELECT @ventas_colapso,s.n,CONCAT('X-',LPAD(s.n,3,'0')),'DEMO-COLAPSO',u.id,
       s.cantidad,s.registro,4,DATE_ADD(s.registro,INTERVAL 4 HOUR),
       CONCAT('DEMO estres mayor volumen pedido ',s.n)
FROM (SELECT n,56 + MOD(n * 7,14) x,31 + MOD(n * 11,18) y,
             8 + MOD(n * 5,17) cantidad,
             DATE_ADD('2029-03-01 08:00:00',INTERVAL (n-36)*3 MINUTE) registro
      FROM demo_numeros WHERE n BETWEEN 36 AND 50) s
JOIN ubicacion u ON u.x=s.x AND u.y=s.y
ON DUPLICATE KEY UPDATE codigo=codigo;

DROP TEMPORARY TABLE demo_numeros;

-- Verificacion sin modificar datos: tras semillas base y esta ampliacion,
-- los totales esperados son 65, 135 y 50 pedidos, respectivamente.
SELECT f.nombre,COUNT(p.id) AS pedidos
FROM archivo_fuente f
LEFT JOIN pedido p ON p.archivo_id=f.id
WHERE f.nombre IN ('demo.diario.202901','demo.cinco.202902','demo.colapso.202903')
GROUP BY f.id,f.nombre
ORDER BY f.nombre;

-- Esperado: 100 pedidos de esta segunda ampliacion, tambien al repetirla.
SELECT COUNT(*) AS pedidos_segunda_ampliacion
FROM pedido p
JOIN archivo_fuente f ON f.id=p.archivo_id
WHERE (f.nombre='demo.diario.202901' AND p.numero_linea BETWEEN 41 AND 65)
   OR (f.nombre='demo.cinco.202902' AND p.numero_linea BETWEEN 76 AND 135)
   OR (f.nombre='demo.colapso.202903' AND p.numero_linea BETWEEN 36 AND 50);
