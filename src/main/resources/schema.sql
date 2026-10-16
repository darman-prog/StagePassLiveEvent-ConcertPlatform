-- Catalogo legacy de recintos y asientos (parte relacional del sistema).
-- El precio viaja como TEXTO para conservar los decimales exactos del BigDecimal:
-- el NUMERIC/REAL de SQLite es un double y pierde centavos en operaciones tipicas.
CREATE TABLE IF NOT EXISTS asientos (
    id TEXT PRIMARY KEY,
    recinto_id TEXT NOT NULL,
    codigo_ubicacion TEXT NOT NULL,
    precio_base TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('DISPONIBLE', 'BLOQUEADO', 'VENDIDO')),
    bloqueado_hasta TEXT
);

-- Catálogo de compradores para componer ordenes y enviar avisos.
CREATE TABLE IF NOT EXISTS clientes (
    id TEXT PRIMARY KEY,
    nombre_completo TEXT NOT NULL,
    correo_electronico TEXT NOT NULL,
    telefono_movil TEXT,
    nacionalidad TEXT
);
