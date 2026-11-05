package ar.edu.et35.racing.red;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * Foto del lobby: quién es el host, qué circuito y cuántas vueltas están elegidos y qué jugadores hay, con su piloto
 * y si están listos.
 * El servidor la arma y la manda en el mensaje LOBBY; el cliente la lee y la muestra.
 *
 * <p>Formato del mensaje: {@code LOBBY;idHost;circuito;vueltas;cantidad;id;nombre;piloto;listo;id;nombre;piloto;listo...}
 * donde {@code circuito} y {@code piloto} son índices de {@link Catalogo#CIRCUITOS} y {@link Catalogo#PILOTOS}.
 */
public record EstadoLobby(int idHost, int circuito, int vueltas, List<JugadorLobby> jugadores) {
    private static final int CAMPOS_POR_JUGADOR = 4;
    private static final int CAMPOS_FIJOS = 4;

    public record JugadorLobby(int id, String nombre, int piloto, boolean listo) {
    }

    public static EstadoLobby vacio() {
        return new EstadoLobby(-1, 0, Config.VUELTAS, List.of());
    }

    public String aLinea() {
        StringBuilder linea = new StringBuilder(Protocolo.armar(Protocolo.LOBBY, idHost, circuito, vueltas, jugadores.size()));
        for (JugadorLobby j : jugadores) {
            linea.append(';').append(j.id()).append(';').append(j.nombre()).append(';').append(j.piloto())
                .append(';').append(j.listo() ? 1 : 0);
        }
        return linea.toString();
    }

    /** Lee un mensaje LOBBY. Si viene mal formado devuelve un lobby vacío en lugar de fallar. */
    public static EstadoLobby desde(Mensaje mensaje) {
        int circuito = mensaje.entero(1, 0);
        int vueltas = mensaje.entero(2, Config.VUELTAS);
        int cantidad = mensaje.entero(3, 0);
        List<JugadorLobby> lista = new ArrayList<>();
        for (int i = 0; i < cantidad && i < Config.MAX_JUGADORES; i++) {
            int base = CAMPOS_FIJOS + i * CAMPOS_POR_JUGADOR;
            if (base + CAMPOS_POR_JUGADOR - 1 >= mensaje.campos().size()) {
                break; // el mensaje trae menos jugadores de los que dice
            }
            lista.add(new JugadorLobby(mensaje.entero(base, -1), mensaje.campo(base + 1), mensaje.entero(base + 2, 0),
                mensaje.campo(base + 3).equals("1")));
        }
        return new EstadoLobby(mensaje.entero(0, -1), Catalogo.circuitoValido(circuito) ? circuito : 0,
            Math.max(Config.MIN_VUELTAS, Math.min(Config.MAX_VUELTAS, vueltas)), List.copyOf(lista));
    }

    public JugadorLobby jugador(int id) {
        for (JugadorLobby j : jugadores) {
            if (j.id() == id) {
                return j;
            }
        }
        return null;
    }

    /** El jugador que tiene ese piloto, o null si está libre. */
    public JugadorLobby duenioDelPiloto(int piloto) {
        for (JugadorLobby j : jugadores) {
            if (j.piloto() == piloto) {
                return j;
            }
        }
        return null;
    }

    /** El jugador que corre para esa escudería (con cualquiera de sus dos pilotos), o null si está libre. */
    public JugadorLobby duenioDeEscuderia(int escuderia) {
        for (JugadorLobby j : jugadores) {
            if (Catalogo.piloto(j.piloto()).escuderia() == escuderia) {
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
