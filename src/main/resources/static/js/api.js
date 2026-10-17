/**
 * StagePass Live — Cliente API
 * Wrapper fetch para interactuar con el backend hexagonal.
 * Mapea respuestas JSON y traduce ProblemDetail (RFC 7807) a errores accionables.
 */

export class ApiError extends Error {
    constructor(status, title, detail, type = null) {
        super(detail || title || `Error HTTP ${status}`);
        this.name = 'ApiError';
        this.status = status;
        this.title = title || 'Error';
        this.detail = detail || 'Ocurrió un error inesperado al procesar la solicitud.';
        this.type = type;
    }
}

async function peticionJson(url, opciones = {}) {
    const config = {
        headers: {
            'Accept': 'application/json',
            ...(opciones.body ? { 'Content-Type': 'application/json' } : {}),
            ...opciones.headers,
        },
        ...opciones,
    };

    let respuesta;
    try {
        respuesta = await fetch(url, config);
    } catch (err) {
        throw new ApiError(
            0,
            'Error de conexión',
            'No se pudo conectar con el servidor backend. Verifica que la aplicación esté corriendo y MongoDB esté activo (`docker compose up -d`).'
        );
    }

    if (respuesta.status === 204) {
        return null;
    }

    const contentType = respuesta.headers.get('content-type') || '';
    const esJson = contentType.includes('application/json') || contentType.includes('application/problem+json');

    if (!respuesta.ok) {
        let detalle = `Error ${respuesta.status}`;
        let titulo = respuesta.statusText;
        let tipo = null;

        if (esJson) {
            try {
                const problema = await respuesta.json();
                detalle = problema.detail || detalle;
                titulo = problema.title || titulo;
                tipo = problema.type || tipo;
            } catch (_) {
                // Si el JSON de error viniese malformado, conservamos el status
            }
        } else {
            const texto = await respuesta.text();
            if (texto) detalle = texto;
        }

        throw new ApiError(respuesta.status, titulo, detalle, tipo);
    }

    if (esJson) {
        return await respuesta.json();
    }
    return await respuesta.text();
}

export const api = {
    /** GET /ping */
    async ping() {
        return peticionJson('/ping');
    },

    /** GET /api/v1/recintos/{recintoId}/asientos */
    async listarAsientos(recintoId) {
        return peticionJson(`/api/v1/recintos/${encodeURIComponent(recintoId)}/asientos`);
    },

    /** POST /api/v1/compras */
    async iniciarCompra({ eventoId, clienteId, asientosId }) {
        return peticionJson('/api/v1/compras', {
            method: 'POST',
            body: JSON.stringify({ eventoId, clienteId, asientosId }),
        });
    },

    /** POST /api/v1/compras/{ordenId}/seleccion */
    async seleccionarAsientos(ordenId, asientosId) {
        return peticionJson(`/api/v1/compras/${encodeURIComponent(ordenId)}/seleccion`, {
            method: 'POST',
            body: JSON.stringify({ asientosId }),
        });
    },

    /** POST /api/v1/pagos/confirmar */
    async confirmarPago({ ordenId, pasarela, identificadorTransaccion }) {
        return peticionJson('/api/v1/pagos/confirmar', {
            method: 'POST',
            body: JSON.stringify({
                ordenId,
                pasarela: pasarela.toLowerCase(),
                identificadorTransaccion,
            }),
        });
    },

    /** POST /api/v1/pagos/webhook/{pasarela} */
    async simularWebhook(pasarela, { identificadorTransaccion, aprobado = true }) {
        return peticionJson(`/api/v1/pagos/webhook/${encodeURIComponent(pasarela.toLowerCase())}`, {
            method: 'POST',
            body: JSON.stringify({ identificadorTransaccion, aprobado }),
        });
    },

    /** GET /api/v1/boletos/{codigo} */
    async validarBoleto(codigo) {
        return peticionJson(`/api/v1/boletos/${encodeURIComponent(codigo.trim())}`);
    },
};
