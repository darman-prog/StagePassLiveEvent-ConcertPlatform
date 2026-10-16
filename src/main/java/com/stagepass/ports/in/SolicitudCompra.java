package com.stagepass.ports.in;

import java.util.List;

/** Datos que manda el checkout para arrancar una compra. */
public record SolicitudCompra(String eventoId, String clienteId, List<String> asientosId) {}
