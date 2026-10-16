package com.stagepass.ports.out;

import com.stagepass.domain.model.Cliente;

/**
 * Puerto agregado al implementar los casos de uso: la orden compone al Cliente y las
 * notificaciones usan su correo, asi que el nucleo necesita recuperarlo por id.
 * En el sistema real lo resuelve el catalogo legacy; en pruebas, un adaptador en memoria.
 */
public interface ClienteRepository {

    Cliente buscarPorId(String clienteId);
}
