package com.stagepass.application.dobles;

import com.stagepass.domain.model.Cliente;
import com.stagepass.ports.out.ClienteRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/** Padron de clientes de mentira, suficiente para armar la orden en los tests. */
public class ClienteRepositoryEnMemoria implements ClienteRepository {

    private final Map<String, Cliente> porId = new LinkedHashMap<>();

    public void agregar(Cliente cliente) {
        porId.put(cliente.getId(), cliente);
    }

    @Override
    public Cliente buscarPorId(String clienteId) {
        return porId.get(clienteId);
    }
}
