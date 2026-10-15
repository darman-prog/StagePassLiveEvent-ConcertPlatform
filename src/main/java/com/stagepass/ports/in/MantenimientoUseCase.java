package com.stagepass.ports.in;

/** Puerto primario del barrido periodico: expira reservas vencidas y dice cuantas libero. */
public interface MantenimientoUseCase {

    int expirarReservasVencidas();
}
