package ar.edu.et35.racing.red;

import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;

public class SesionRed {
    private final ServidorPartida servidor;
    private final ClientePartida cliente;

    public SesionRed(ServidorPartida servidor, ClientePartida cliente) {
        this.servidor = servidor;
        this.cliente = cliente;
    }

    public ClientePartida cliente() {
        return cliente;
    }

    public boolean esAnfitrion() {
        return servidor != null;
    }

    public int puertoServidor() {
        return servidor != null ? servidor.puerto() : -1;
    }

    public void cerrar() {
        try {
            cliente.cerrar();
        } finally {
            if (servidor != null) {
                servidor.cerrar();
            }
        }
    }
}
