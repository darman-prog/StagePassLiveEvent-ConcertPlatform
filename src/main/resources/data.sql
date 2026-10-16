-- Seeds de desarrollo. INSERT OR IGNORE mantiene los scripts idempotentes: Boot los
-- ejecuta en cada arranque (spring.sql.init.mode=always) y nunca duplica filas.
INSERT OR IGNORE INTO clientes (id, nombre_completo, correo_electronico, telefono_movil, nacionalidad)
    VALUES ('CL-001', 'Camila Osorio', 'camila.osorio@example.com', '573001234567', 'CO');
INSERT OR IGNORE INTO clientes (id, nombre_completo, correo_electronico, telefono_movil, nacionalidad)
    VALUES ('CL-002', 'Andres Villalba', 'andres.villalba@example.com', '573007654321', 'CO');
INSERT OR IGNORE INTO clientes (id, nombre_completo, correo_electronico, telefono_movil, nacionalidad)
    VALUES ('CL-003', 'Luisa Mendez', 'luisa.mendez@example.com', '573006789012', 'PE');

-- Recinto principal: tribuna VIP y general del REC-01.
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('VIP-A1', 'REC-01', 'VIP-A1', '150000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('VIP-A2', 'REC-01', 'VIP-A2', '150000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('VIP-A3', 'REC-01', 'VIP-A3', '150000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('VIP-A4', 'REC-01', 'VIP-A4', '150000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-01', 'REC-01', 'General-01', '80000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-02', 'REC-01', 'General-02', '80000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-03', 'REC-01', 'General-03', '80000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-04', 'REC-01', 'General-04', '80000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-05', 'REC-01', 'General-05', '80000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('GEN-06', 'REC-01', 'General-06', '80000.00', 'DISPONIBLE', NULL);

-- Recinto secundario: palcos del REC-02 para comprar corporativas (6 o mas).
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('BOX-01', 'REC-02', 'Palco-01', '220000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('BOX-02', 'REC-02', 'Palco-02', '220000.00', 'DISPONIBLE', NULL);
INSERT OR IGNORE INTO asientos (id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta)
    VALUES ('BOX-03', 'REC-02', 'Palco-03', '220000.00', 'DISPONIBLE', NULL);
