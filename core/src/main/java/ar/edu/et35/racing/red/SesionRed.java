package ar.edu.et35.racing.red;

import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;

/**
 * Lo que una PC tiene abierto para una partida en red: su cliente y, si es la del host, también el servidor.
 * Lo guarda {@code Main} para que lo usen las pantallas (lobby, carrera) y lo cierre al salir.
 */
public class SesionRed {
    private final ServidorPartida servidor; // null si esta PC no es el host
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

    /** Cierra el cliente y, si esta PC es el host, el servidor (que le avisa al resto que se terminó la partida). */
    public void cerrar() {
        cliente.cerrar();
        if (servidor != null) {
            servidor.cerrar();
        }
    }
}
