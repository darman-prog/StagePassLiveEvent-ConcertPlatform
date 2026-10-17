/**
 * StagePass Live — Lógica de Interfaz y Narrativa Hexagonal (Impeccable Edition)
 * Controla el ciclo de vida, renderizado del mapa, temporizador y llamadas API.
 */

import { api, ApiError } from './api.js';

// Cadenas exactas del contrato §4.4 de la spec
const TRAZAS_ARQUITECTURA = {
    ping: 'GET /ping → PingController → 200',
    mapa: (recintoId) => `GET /recintos/${recintoId}/asientos → CatalogoController → CatalogoUseCase → ListarAsientos → AsientoRepository → SqliteAsientoRepository`,
    compra: 'POST /compras → CheckoutController → CheckoutUseCase → IniciarCompra → SqliteAsientoRepository(bloquear) + MongoOrdenRepository(guardar)',
    desglose: 'POST /compras/{id}/seleccion → CheckoutController → IniciarCompra.seleccionarAsientos → MongoOrdenRepository',
    pago: 'POST /pagos/confirmar → PagosController → PagosUseCase → ConfirmarPago → PaymentGateway(mock) + MongoOrdenRepository + MongoBoletoRepository + QrTicketGenerator + SendgridNotificador + DianFacturaEmitter',
    webhook: (pasarela) => `POST /pagos/webhook/${pasarela} → PagosController → ConfirmarPago.notificarCambioTransaccion (no-op documentado)`,
    validar: (codigo) => `GET /boletos/${codigo} → ValidacionController → ValidacionUseCase → ValidarBoleto → MongoBoletoRepository + MongoOrdenRepository`,
};

// Estado reactivo de la aplicación
const estado = {
    clienteId: 'CL-001',
    recintoId: 'REC-01',
    eventoId: 'EV-001',
    asientos: [],
    seleccionados: new Set(), // IDs de asientos seleccionados por el usuario
    ordenActiva: null,        // { ordenId, codigoPago, expiraEn, desglose, asientosId }
    pasarela: 'wompi',
    ultimoBoleto: null,       // { codigoRedencion, firmaHash, fechaEmision }
    timerId: null,
    autoRefreshId: null,
};

// Elementos del DOM
const dom = {
    // Header & Tabs
    tabComprar: document.getElementById('tab-comprar'),
    tabPuerta: document.getElementById('tab-puerta'),
    panelComprar: document.getElementById('panel-comprar'),
    panelPuerta: document.getElementById('panel-puerta'),

    // Filtros & Mapa
    selectCliente: document.getElementById('select-cliente'),
    selectRecinto: document.getElementById('select-recinto'),
    btnActualizarMapa: document.getElementById('btn-actualizar-mapa'),
    mapaAsientos: document.getElementById('mapa-asientos'),

    // Orden & Checkout
    cardOrden: document.getElementById('card-orden'),
    ordenAsientosSeleccionados: document.getElementById('orden-asientos-seleccionados'),
    contadorAsientosBadge: document.getElementById('contador-asientos-badge'),
    btnNuevaCompra: document.getElementById('btn-nueva-compra'),
    cajaTemporizador: document.getElementById('caja-temporizador'),
    contadorReloj: document.getElementById('contador-reloj'),
    resumenSubtotal: document.getElementById('resumen-subtotal'),
    filaTarifa: document.getElementById('fila-tarifa'),
    resumenTarifa: document.getElementById('resumen-tarifa'),
    filaDescuento: document.getElementById('fila-descuento'),
    resumenDescuento: document.getElementById('resumen-descuento'),
    resumenTotal: document.getElementById('resumen-total'),
    btnIniciarCompra: document.getElementById('btn-iniciar-compra'),
    pagoCodigoBadge: document.getElementById('pago-codigo-badge'),
    valorCodigoPago: document.getElementById('valor-codigo-pago'),

    // Pago
    cardPago: document.getElementById('card-pago'),
    pasarelasOpciones: document.querySelectorAll('.pasarela-opcion'),
    inputTransaccion: document.getElementById('input-transaccion'),
    btnConfirmarPago: document.getElementById('btn-confirmar-pago'),
    btnSimularWebhook: document.getElementById('btn-simular-webhook'),
    alertaPago: document.getElementById('alerta-pago'),
    alertaPagoTitulo: document.getElementById('alerta-pago-titulo'),
    alertaPagoDetalle: document.getElementById('alerta-pago-detalle'),
    alertaPagoPista: document.getElementById('alerta-pago-pista'),

    // Boleto Emitido
    cardBoleto: document.getElementById('card-boleto'),
    boletoCodigo: document.getElementById('boleto-codigo'),
    boletoQrImg: document.getElementById('boleto-qr-img'),
    boletoQrFallback: document.getElementById('boleto-qr-fallback'),
    boletoFirmaCorta: document.getElementById('boleto-firma-corta'),
    btnCopiarFirma: document.getElementById('btn-copiar-firma'),
    boletoFecha: document.getElementById('boleto-fecha'),
    btnIrValidar: document.getElementById('btn-ir-validar'),

    // Modo Escáner Puerta
    inputCodigoBoleto: document.getElementById('input-codigo-boleto'),
    btnValidarPuerta: document.getElementById('btn-validar-puerta'),
    btnAtajoUltimoBoleto: document.getElementById('btn-atajo-ultimo-boleto'),
    veredictoPuerta: document.getElementById('veredicto-puerta'),

    // Franja Arquitectura & Toasts
    franjaTag: document.getElementById('franja-tag'),
    franjaCadena: document.getElementById('franja-cadena'),
    franjaStatus: document.getElementById('franja-status'),
    toasts: document.getElementById('toasts'),
};

/** Actualiza la consola inferior con la traza de capas y el status HTTP */
function registrarTrazaArquitectura(cadena, status = 200) {
    dom.franjaCadena.textContent = cadena;
    dom.franjaStatus.textContent = `HTTP ${status}`;
    dom.franjaStatus.style.color = (status >= 200 && status < 300) ? 'var(--exito)' : 'var(--peligro)';
    dom.franjaStatus.style.borderColor = (status >= 200 && status < 300) ? 'color-mix(in srgb, var(--exito) 40%, transparent)' : 'color-mix(in srgb, var(--peligro) 40%, transparent)';
}

/** Despliega notificación flotante limpia (con icono vectorial SVG) */
function mostrarToast(mensaje, tipo = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${tipo === 'alerta' ? 'toast--alerta' : tipo === 'exito' ? 'toast--exito' : ''}`;

    let icono = `
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
        </svg>
    `;
    if (tipo === 'exito') {
        icono = `
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="color: var(--exito);">
                <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
        `;
    } else if (tipo === 'alerta') {
        icono = `
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="color: var(--alerta);">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
        `;
    }

    toast.innerHTML = `${icono} <div>${mensaje}</div>`;
    dom.toasts.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-6px)';
        toast.style.transition = 'all 160ms ease';
        setTimeout(() => toast.remove(), 160);
    }, 4000);
}

/** Inicialización de Pestañas */
function cambiarPestana(tabId) {
    const esComprar = tabId === '#comprar' || tabId === 'tab-comprar';
    dom.tabComprar.setAttribute('aria-selected', esComprar ? 'true' : 'false');
    dom.tabPuerta.setAttribute('aria-selected', !esComprar ? 'true' : 'false');
    dom.panelComprar.hidden = !esComprar;
    dom.panelPuerta.hidden = esComprar;
    window.location.hash = esComprar ? '#comprar' : '#puerta';
}

function inicializarPestanas() {
    dom.tabComprar.addEventListener('click', () => cambiarPestana('#comprar'));
    dom.tabPuerta.addEventListener('click', () => cambiarPestana('#puerta'));

    window.addEventListener('hashchange', () => {
        const hash = window.location.hash;
        if (hash === '#puerta' || hash === '#comprar') {
            cambiarPestana(hash);
        }
    });

    if (window.location.hash === '#puerta') {
        cambiarPestana('#puerta');
    }
}

/** Comprobación silenciosa de conectividad al arrancar para registrar traza en consola */
async function comprobarBackendSilencioso() {
    try {
        const res = await api.ping();
        if (res === 'pong') {
            registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.ping, 200);
        }
    } catch (_) {
        registrarTrazaArquitectura('GET /ping → Error de conexión con el backend', 503);
    }
}

/** Carga y renderizado del mapa de asientos */
async function cargarMapaAsientos(silencioso = false) {
    if (!silencioso) {
        dom.mapaAsientos.innerHTML = `
            <div class="grilla-asientos">
                ${Array(10).fill('<div class="skeleton-asiento"></div>').join('')}
            </div>
        `;
    }

    try {
        const asientos = await api.listarAsientos(estado.recintoId);
        estado.asientos = asientos;

        // Auto-refresh: verificar si asientos seleccionados cambiaron de estado en el server
        if (silencioso && estado.seleccionados.size > 0 && !estado.ordenActiva) {
            let algunRemovido = false;
            for (const id of estado.seleccionados) {
                const asientoEnServer = asientos.find(a => a.id === id);
                if (!asientoEnServer || asientoEnServer.estado !== 'DISPONIBLE') {
                    estado.seleccionados.delete(id);
                    algunRemovido = true;
                    mostrarToast(`El asiento ${id} fue tomado por otro usuario y se removió de tu selección.`, 'alerta');
                }
            }
            if (algunRemovido) {
                actualizarResumenOrden();
            }
        }

        renderizarMapa();
        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.mapa(estado.recintoId), 200);
    } catch (err) {
        dom.mapaAsientos.innerHTML = `
            <div style="text-align: center; padding: 24px; color: var(--peligro);">
                <p><strong>No se pudo sincronizar el mapa de asientos</strong></p>
                <p style="font-size: 0.82rem; color: var(--texto-2); margin-top: 6px;">${err.detail || err.message}</p>
            </div>
        `;
        registrarTrazaArquitectura(`GET /recintos/${estado.recintoId}/asientos → Error`, err.status || 500);
    }
}

/** Agrupa asientos por zona (VIP, General, Palcos) */
function agruparAsientosPorZona(asientos) {
    const grupos = {};
    for (const asiento of asientos) {
        let zona = 'General';
        const id = asiento.id.toUpperCase();
        if (id.startsWith('VIP')) {
            zona = 'Zona VIP';
        } else if (id.startsWith('GEN')) {
            zona = 'Platea General';
        } else if (id.startsWith('BOX')) {
            zona = 'Palcos Corporativos';
        }

        if (!grupos[zona]) {
            grupos[zona] = [];
        }
        grupos[zona].push(asiento);
    }
    return grupos;
}

/** Renderiza los asientos agrupados */
function renderizarMapa() {
    if (!estado.asientos || estado.asientos.length === 0) {
        dom.mapaAsientos.innerHTML = `
            <div style="text-align: center; padding: 30px; color: var(--texto-3);">
                <p>Recinto sin configuración de asientos activa.</p>
            </div>
        `;
        return;
    }

    const grupos = agruparAsientosPorZona(estado.asientos);
    dom.mapaAsientos.innerHTML = '';

    for (const [nombreZona, lista] of Object.entries(grupos)) {
        const contenedorZona = document.createElement('div');
        contenedorZona.className = 'zona-asientos';

        const precioZona = lista.length > 0 ? Number(lista[0].precio).toLocaleString('es-CO') : '0';

        contenedorZona.innerHTML = `
            <div class="zona-asientos__header">
                <span>${nombreZona}</span>
                <span class="zona-asientos__precio">$${precioZona} c/u</span>
            </div>
            <div class="grilla-asientos"></div>
        `;

        const grilla = contenedorZona.querySelector('.grilla-asientos');

        for (const asiento of lista) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'asiento';
            btn.dataset.id = asiento.id;
            btn.dataset.precio = asiento.precio;
            btn.dataset.codigo = asiento.codigoUbicacion;

            // Determinar estado visual
            const estaSeleccionado = estado.seleccionados.has(asiento.id);
            const esDeMiOrden = estado.ordenActiva && estado.ordenActiva.asientosId.includes(asiento.id);

            let claseEstado = 'asiento--disponible';
            let textoEstado = 'Libre';
            let deshabilitado = false;

            if (asiento.estado === 'VENDIDO') {
                claseEstado = 'asiento--vendido';
                textoEstado = 'Vendido';
                deshabilitado = true;
            } else if (esDeMiOrden) {
                claseEstado = 'asiento--tuyo';
                textoEstado = 'Tu orden';
                deshabilitado = true;
            } else if (asiento.estado === 'BLOQUEADO') {
                claseEstado = 'asiento--bloqueado';
                textoEstado = 'Bloqueado';
                deshabilitado = true;
            } else if (estaSeleccionado) {
                claseEstado = 'asiento--seleccionado';
                textoEstado = 'Elegido';
            }

            btn.classList.add(claseEstado);
            btn.disabled = deshabilitado;
            btn.setAttribute('aria-pressed', estaSeleccionado ? 'true' : 'false');
            btn.setAttribute('aria-label', `Asiento ${asiento.codigoUbicacion}, ${textoEstado}, precio ${asiento.precio}`);

            btn.innerHTML = `
                <span class="asiento__codigo">${asiento.codigoUbicacion}</span>
                <span class="asiento__estado-texto">${textoEstado}</span>
            `;

            if (!deshabilitado && !estado.ordenActiva) {
                btn.addEventListener('click', () => alternarSeleccionAsiento(asiento.id));
            }

            grilla.appendChild(btn);
        }

        dom.mapaAsientos.appendChild(contenedorZona);
    }
}

/** Alterna selección de un asiento disponible (tope 1 a 6) */
function alternarSeleccionAsiento(id) {
    if (estado.seleccionados.has(id)) {
        estado.seleccionados.delete(id);
    } else {
        if (estado.seleccionados.size >= 6) {
            mostrarToast('Has alcanzado el límite máximo de 6 boletos por compra.', 'alerta');
            return;
        }
        estado.seleccionados.add(id);
    }
    renderizarMapa();
    actualizarResumenOrden();
}

/** Actualiza la card de orden */
function actualizarResumenOrden() {
    const cantidad = estado.seleccionados.size;
    dom.contadorAsientosBadge.textContent = `${cantidad} de 6 permitidos`;

    if (cantidad === 0) {
        dom.ordenAsientosSeleccionados.innerHTML = `
            <span class="placeholder-vacio">Selecciona entre 1 y 6 asientos en el mapa para iniciar.</span>
        `;
        dom.resumenSubtotal.textContent = '$0.00';
        dom.resumenTotal.textContent = '$0.00';
        dom.btnIniciarCompra.disabled = true;
        return;
    }

    // Renderizar chips de asientos
    dom.ordenAsientosSeleccionados.innerHTML = '';
    let sumaBase = 0;

    for (const id of estado.seleccionados) {
        const asiento = estado.asientos.find(a => a.id === id);
        if (asiento) {
            sumaBase += Number(asiento.precio);
        }

        const chip = document.createElement('div');
        chip.className = 'chip-asiento';
        chip.innerHTML = `
            <span>${id}</span>
            ${!estado.ordenActiva ? `<button type="button" class="chip-asiento__remover" aria-label="Quitar asiento ${id}">×</button>` : ''}
        `;

        if (!estado.ordenActiva) {
            chip.querySelector('.chip-asiento__remover').addEventListener('click', () => {
                estado.seleccionados.delete(id);
                renderizarMapa();
                actualizarResumenOrden();
            });
        }

        dom.ordenAsientosSeleccionados.appendChild(chip);
    }

    const formatoPrecio = sumaBase.toLocaleString('es-CO', { minimumFractionDigits: 2 });
    dom.resumenSubtotal.textContent = `$${formatoPrecio}`;

    if (!estado.ordenActiva) {
        dom.resumenTotal.textContent = `$${formatoPrecio}`;
        dom.btnIniciarCompra.disabled = false;
    }
}

/** Inicia compra y obtiene desglose oficial del backend */
async function iniciarCompra() {
    dom.btnIniciarCompra.disabled = true;
    dom.btnIniciarCompra.innerHTML = '<span>Bloqueando asientos...</span>';

    const asientosId = Array.from(estado.seleccionados);

    try {
        // 1. Iniciar reserva (bloqueo en SQLite + guardado en Mongo)
        const inicio = await api.iniciarCompra({
            eventoId: estado.eventoId,
            clienteId: estado.clienteId,
            asientosId: asientosId,
        });

        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.compra, 201);

        // 2. Traer desglose de tarifas oficial del backend (reglas del dominio)
        const desglose = await api.seleccionarAsientos(inicio.ordenId, asientosId);
        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.desglose, 200);

        estado.ordenActiva = {
            ordenId: inicio.ordenId,
            codigoPago: inicio.codigoPago,
            expiraEn: inicio.expiraEn,
            desglose: desglose,
            asientosId: asientosId,
        };

        // Actualizar UI con valores oficiales del backend
        dom.btnIniciarCompra.style.display = 'none';
        dom.btnNuevaCompra.style.display = 'inline-block';
        dom.pagoCodigoBadge.style.display = 'flex';
        dom.valorCodigoPago.textContent = inicio.codigoPago;

        // Mostrar filas de tarifa y descuento
        dom.filaTarifa.style.display = 'table-row';
        dom.filaDescuento.style.display = 'table-row';

        const subtotalNum = Number(desglose.asientos.reduce((acc, a) => acc + Number(a.precio), 0));
        dom.resumenSubtotal.textContent = `$${subtotalNum.toLocaleString('es-CO', { minimumFractionDigits: 2 })}`;
        dom.resumenTarifa.textContent = `+$${Number(desglose.tarifaServicio).toLocaleString('es-CO', { minimumFractionDigits: 2 })}`;
        dom.resumenDescuento.textContent = `-$${Number(desglose.descuentoAplicado).toLocaleString('es-CO', { minimumFractionDigits: 2 })}`;
        dom.resumenTotal.textContent = `$${Number(desglose.montoTotal).toLocaleString('es-CO', { minimumFractionDigits: 2 })}`;

        // Mostrar tarjeta de pago y temporizador
        dom.cajaTemporizador.style.display = 'flex';
        dom.cardPago.style.display = 'block';
        dom.cardPago.scrollIntoView({ behavior: 'smooth' });

        iniciarTemporizador(inicio.expiraEn);
        renderizarMapa();
        mostrarToast('Asientos reservados por 10 minutos. Procede a confirmar el pago.', 'exito');

    } catch (err) {
        dom.btnIniciarCompra.disabled = false;
        dom.btnIniciarCompra.innerHTML = '<span>Iniciar reserva y bloquear asientos</span>';
        mostrarToast(err.detail || 'No se pudo iniciar la compra.', 'alerta');
        registrarTrazaArquitectura(`POST /compras → ${err.status || 500} ${err.detail || 'Error'}`, err.status || 500);
    }
}

/** Temporizador regresivo de expiración */
function iniciarTemporizador(expiraEnIso) {
    if (estado.timerId) {
        clearInterval(estado.timerId);
    }

    const expiraMs = new Date(expiraEnIso).getTime();

    function actualizar() {
        const ahoraMs = Date.now();
        const restanteSegundos = Math.max(0, Math.floor((expiraMs - ahoraMs) / 1000));

        const minutos = Math.floor(restanteSegundos / 60);
        const segundos = restanteSegundos % 60;
        const textoMinutos = String(minutos).padStart(2, '0');
        const textoSegundos = String(segundos).padStart(2, '0');

        dom.contadorReloj.textContent = `${textoMinutos}:${textoSegundos}`;

        if (restanteSegundos < 60 && restanteSegundos > 0) {
            dom.contadorReloj.className = 'contador-reloj contador--alerta';
        } else if (restanteSegundos === 0) {
            dom.contadorReloj.className = 'contador-reloj contador--vencido';
            dom.contadorReloj.textContent = 'Ventana vencida';
            dom.btnConfirmarPago.disabled = true;
            clearInterval(estado.timerId);
            mostrarToast('La ventana de reserva de 10 minutos ha expirado.', 'alerta');
        } else {
            dom.contadorReloj.className = 'contador-reloj';
        }
    }

    actualizar();
    estado.timerId = setInterval(actualizar, 1000);
}

/** Confirmación de pago con pasarela mock */
async function confirmarPago() {
    dom.alertaPago.style.display = 'none';
    dom.btnConfirmarPago.disabled = true;
    dom.btnConfirmarPago.innerHTML = '<span>Verificando pasarela...</span>';

    const transaccion = dom.inputTransaccion.value.trim() || 'OK-777';

    try {
        const boleto = await api.confirmarPago({
            ordenId: estado.ordenActiva.ordenId,
            pasarela: estado.pasarela,
            identificadorTransaccion: transaccion,
        });

        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.pago, 200);

        if (estado.timerId) clearInterval(estado.timerId);
        estado.ultimoBoleto = boleto;

        dom.cardPago.style.display = 'none';
        dom.cajaTemporizador.style.display = 'none';

        mostrarBoletoEmitido(boleto);
        await cargarMapaAsientos(true);
        mostrarToast('Pago verificado y boleto emitido con éxito.', 'exito');

    } catch (err) {
        dom.btnConfirmarPago.disabled = false;
        dom.btnConfirmarPago.innerHTML = '<span>Confirmar pago</span>';

        dom.alertaPago.style.display = 'flex';
        dom.alertaPagoTitulo.textContent = `Error ${err.status || 402}: ${err.title || 'Pago no verificado'}`;
        dom.alertaPagoDetalle.textContent = err.detail || 'La transacción no fue aprobada por la pasarela.';
        dom.alertaPagoPista.style.display = (err.status === 402) ? 'block' : 'none';

        registrarTrazaArquitectura(`POST /pagos/confirmar → ${err.status || 402} (${err.detail || 'Rechazado'})`, err.status || 402);
    }
}

/** Muestra el e-ticket post-pago */
function mostrarBoletoEmitido(boleto) {
    dom.cardBoleto.style.display = 'block';
    dom.boletoCodigo.textContent = boleto.codigoRedencion;

    const qrUrl = `/qr/${encodeURIComponent(boleto.codigoRedencion)}.png`;
    dom.boletoQrImg.src = qrUrl;
    dom.boletoQrImg.style.display = 'block';
    dom.boletoQrFallback.style.display = 'none';

    dom.boletoQrImg.onerror = () => {
        dom.boletoQrImg.style.display = 'none';
        dom.boletoQrFallback.style.display = 'flex';
        dom.boletoQrFallback.textContent = `[QR: ${boleto.codigoRedencion}]`;
    };

    dom.boletoFirmaCorta.textContent = `${boleto.firmaHash.substring(0, 16)}...`;
    dom.boletoFirmaCorta.title = boleto.firmaHash;
    dom.boletoFecha.textContent = new Date(boleto.fechaEmision).toLocaleString('es-CO');

    dom.btnAtajoUltimoBoleto.style.display = 'inline-block';
    dom.btnAtajoUltimoBoleto.textContent = `Último (${boleto.codigoRedencion})`;
    dom.btnAtajoUltimoBoleto.dataset.codigo = boleto.codigoRedencion;

    dom.cardBoleto.scrollIntoView({ behavior: 'smooth' });
}

/** Simula webhook de pasarela (adaptador primario asíncrono no-op) */
async function simularWebhook() {
    const transaccion = dom.inputTransaccion.value.trim() || 'OK-777';
    try {
        const res = await api.simularWebhook(estado.pasarela, {
            identificadorTransaccion: transaccion,
            aprobado: true,
        });

        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.webhook(estado.pasarela), 200);
        mostrarToast(`Webhook registrado: recibido=${res.recibido}`, 'exito');
    } catch (err) {
        mostrarToast(`Fallo en webhook: ${err.message}`, 'alerta');
    }
}

/** Valida boleto en el tab de puerta */
async function validarEnPuerta(codigo) {
    const codigoLimpio = codigo.trim();
    if (!codigoLimpio) {
        mostrarToast('Ingresa un código de boleto para validar.', 'alerta');
        return;
    }

    dom.btnValidarPuerta.disabled = true;
    dom.btnValidarPuerta.textContent = 'Validando...';

    try {
        const resultado = await api.validarBoleto(codigoLimpio);
        registrarTrazaArquitectura(TRAZAS_ARQUITECTURA.validar(codigoLimpio), 200);

        if (resultado.valido) {
            dom.veredictoPuerta.innerHTML = `
                <div class="veredicto-card veredicto--valido">
                    <div class="veredicto__titulo">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                        <span>BOLETO VÁLIDO</span>
                    </div>
                    <div class="veredicto__motivo">${resultado.motivo || 'Boleto verificado y auténtico'}</div>
                    <div style="font-size: 0.82rem; font-family: var(--mono); color: var(--texto-2); margin-top: 6px;">
                        Código: ${codigoLimpio} · Acceso autorizado
                    </div>
                </div>
            `;
        } else {
            dom.veredictoPuerta.innerHTML = `
                <div class="veredicto-card veredicto--invalido">
                    <div class="veredicto__titulo">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                        <span>BOLETO INVÁLIDO</span>
                    </div>
                    <div class="veredicto__motivo">${resultado.motivo || 'Boleto no autorizado'}</div>
                    <div style="font-size: 0.82rem; font-family: var(--mono); color: var(--texto-2); margin-top: 6px;">
                        Código: ${codigoLimpio} · Acceso denegado
                    </div>
                </div>
            `;
        }
    } catch (err) {
        registrarTrazaArquitectura(`GET /boletos/${codigoLimpio} → ${err.status || 404} (${err.detail || 'No encontrado'})`, err.status || 404);
        dom.veredictoPuerta.innerHTML = `
            <div class="veredicto-card veredicto--invalido">
                <div class="veredicto__titulo">
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                    <span>BOLETO NO ENCONTRADO</span>
                </div>
                <div class="veredicto__motivo">${err.detail || 'El boleto no existe en la base de datos o los datos son inválidos'}</div>
                <div style="font-size: 0.82rem; font-family: var(--mono); color: var(--texto-2); margin-top: 6px;">
                    Código: ${codigoLimpio} · HTTP ${err.status || 404}
                </div>
            </div>
        `;
    } finally {
        dom.btnValidarPuerta.disabled = false;
        dom.btnValidarPuerta.textContent = 'Validar';
    }
}

/** Resetea compra a estado inicial */
function reiniciarCompra() {
    if (estado.timerId) clearInterval(estado.timerId);
    estado.ordenActiva = null;
    estado.seleccionados.clear();

    dom.btnNuevaCompra.style.display = 'none';
    dom.btnIniciarCompra.style.display = 'flex';
    dom.btnIniciarCompra.disabled = true;
    dom.btnIniciarCompra.innerHTML = '<span>Iniciar reserva y bloquear asientos</span>';

    dom.filaTarifa.style.display = 'none';
    dom.filaDescuento.style.display = 'none';
    dom.cajaTemporizador.style.display = 'none';
    dom.pagoCodigoBadge.style.display = 'none';
    dom.cardPago.style.display = 'none';
    dom.cardBoleto.style.display = 'none';
    dom.alertaPago.style.display = 'none';

    renderizarMapa();
    actualizarResumenOrden();
}

/** Configuración de Listeners y Eventos */
function configurarEventos() {
    dom.selectCliente.addEventListener('change', (e) => {
        estado.clienteId = e.target.value;
    });

    dom.selectRecinto.addEventListener('change', (e) => {
        estado.recintoId = e.target.value;
        reiniciarCompra();
        cargarMapaAsientos();
    });

    dom.btnActualizarMapa.addEventListener('click', () => cargarMapaAsientos());

    dom.btnIniciarCompra.addEventListener('click', iniciarCompra);
    dom.btnNuevaCompra.addEventListener('click', reiniciarCompra);

    dom.pasarelasOpciones.forEach(opcion => {
        opcion.addEventListener('click', () => {
            dom.pasarelasOpciones.forEach(o => o.setAttribute('aria-checked', 'false'));
            opcion.setAttribute('aria-checked', 'true');
            estado.pasarela = opcion.dataset.pasarela;
        });
        opcion.addEventListener('keydown', (e) => {
            if (e.key === ' ' || e.key === 'Enter') {
                e.preventDefault();
                opcion.click();
            }
        });
    });

    document.querySelectorAll('.chip-sim[data-fill]').forEach(chip => {
        chip.addEventListener('click', () => {
            dom.inputTransaccion.value = chip.dataset.fill;
        });
    });

    dom.btnConfirmarPago.addEventListener('click', confirmarPago);
    dom.btnSimularWebhook.addEventListener('click', simularWebhook);

    dom.btnCopiarFirma.addEventListener('click', async () => {
        if (!estado.ultimoBoleto) return;
        try {
            await navigator.clipboard.writeText(estado.ultimoBoleto.firmaHash);
            mostrarToast('Firma SHA-256 copiada al portapapeles', 'exito');
        } catch (_) {
            mostrarToast(`Firma: ${estado.ultimoBoleto.firmaHash}`, 'info');
        }
    });

    dom.btnIrValidar.addEventListener('click', () => {
        if (estado.ultimoBoleto) {
            dom.inputCodigoBoleto.value = estado.ultimoBoleto.codigoRedencion;
        }
        cambiarPestana('#puerta');
        dom.inputCodigoBoleto.focus();
    });

    dom.btnValidarPuerta.addEventListener('click', () => {
        validarEnPuerta(dom.inputCodigoBoleto.value);
    });

    dom.inputCodigoBoleto.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            validarEnPuerta(dom.inputCodigoBoleto.value);
        }
    });

    dom.btnAtajoUltimoBoleto.addEventListener('click', () => {
        if (estado.ultimoBoleto) {
            dom.inputCodigoBoleto.value = estado.ultimoBoleto.codigoRedencion;
            validarEnPuerta(estado.ultimoBoleto.codigoRedencion);
        }
    });

    document.querySelectorAll('[data-puerta-fill]').forEach(chip => {
        chip.addEventListener('click', () => {
            dom.inputCodigoBoleto.value = chip.dataset.puertaFill;
            validarEnPuerta(chip.dataset.puertaFill);
        });
    });
}

// Arranque de la aplicación
document.addEventListener('DOMContentLoaded', async () => {
    inicializarPestanas();
    configurarEventos();
    await comprobarBackendSilencioso();
    await cargarMapaAsientos();

    // Auto-refresh silencioso cada 15 segundos
    estado.autoRefreshId = setInterval(() => {
        cargarMapaAsientos(true);
    }, 15000);
});
