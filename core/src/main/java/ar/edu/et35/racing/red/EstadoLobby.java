package ar.edu.et35.racing.red;

import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * Foto del lobby: quién es el host y qué jugadores hay, con su auto y si están listos. El servidor la arma y la
 * manda en el mensaje LOBBY; el cliente la lee y la muestra.
 *
 * <p>Formato del mensaje: {@code LOBBY;idHost;cantidad;id;nombre;auto;listo;id;nombre;auto;listo...}
 */
public record EstadoLobby(int idHost, List<JugadorLobby> jugadores) {

    public record JugadorLobby(int id, String nombre, int auto, boolean listo) {
    }

    public static EstadoLobby vacio() {
        return new EstadoLobby(-1, List.of());
    }

    public String aLinea() {
        StringBuilder linea = new StringBuilder(Protocolo.armar(Protocolo.LOBBY, idHost, jugadores.size()));
        for (JugadorLobby j : jugadores) {
            linea.append(';').append(j.id()).append(';').append(j.nombre()).append(';').append(j.auto())
                .append(';').append(j.listo() ? 1 : 0);
        }
        return linea.toString();
    }

    /** Lee un mensaje LOBBY. Si viene mal formado devuelve un lobby vacío en lugar de fallar. */
    public static EstadoLobby desde(Mensaje mensaje) {
        int cantidad = mensaje.entero(1, 0);
        List<JugadorLobby> lista = new ArrayList<>();
        for (int i = 0; i < cantidad && i < Config.MAX_JUGADORES; i++) {
            int base = 2 + i * 4;
            if (base + 3 >= mensaje.campos().size()) {
                break; // el mensaje trae menos jugadores de los que dice
            }
            lista.add(new JugadorLobby(mensaje.entero(base, -1), mensaje.campo(base + 1), mensaje.entero(base + 2, 0),
                mensaje.campo(base + 3).equals("1")));
        }
        return new EstadoLobby(mensaje.entero(0, -1), List.copyOf(lista));
    }

    public JugadorLobby jugador(int id) {
        for (JugadorLobby j : jugadores) {
            if (j.id() == id) {
                return j;
            }
        }
        return null;
    }

    /** El jugador que tiene ese auto, o null si está libre. */
    public JugadorLobby duenioDelAuto(int auto) {
        for (JugadorLobby j : jugadores) {
            if (j.auto() == auto) {
                return j;
            }
        }
        return null;
    }

    public boolean todosListos() {
        for (JugadorLobby j : jugadores) {
            if (!j.listo()) {
                return false;
            }
        }
        return !jugadores.isEmpty();
    }

    /** Hay jugadores suficientes y todos listos: el host ya puede iniciar. */
    public boolean sePuedeIniciar() {
        return jugadores.size() >= Config.MIN_JUGADORES && todosListos();
    }
}
