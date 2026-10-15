package com.stagepass.domain.model;

/**
 * Comprador de boletas. El correo se usa para notificaciones y facturacion; su validacion
 * vive aca para no repetirla en cada adaptador.
 */
public class Cliente {

    private final String id;
    private final String nombreCompleto;
    private final String correoElectronico;
    private final String telefonoMovil;
    private final String nacionalidad;

    public Cliente(String id, String nombreCompleto, String correoElectronico,
                   String telefonoMovil, String nacionalidad) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.correoElectronico = correoElectronico;
        this.telefonoMovil = telefonoMovil;
        this.nacionalidad = nacionalidad;
    }

    /**
     * Extra fuera del enunciado: valida la forma minima usuario@dominio.ext para no
     * aceptar cadenas vacias ni correos sin arroba o sin dominio.
     */
    public boolean correoEsValido() {
        // Algo antes del @, algo despues y un punto con extension final: nada de espacios.
        return correoElectronico != null
                && correoElectronico.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public String getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public String getTelefonoMovil() {
        return telefonoMovil;
    }

    public String getNacionalidad() {
        return nacionalidad;
    }
}
