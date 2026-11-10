package ar.edu.et35.racing.red;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.ModoGrilla;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Foto del lobby: quién es el host, qué circuito y cuántas vueltas están elegidos, si hay clasificación, si es un torneo y con
 * qué calendario, y qué jugadores hay, con su piloto y si están listos.
 * El servidor la arma y la manda en el mensaje LOBBY; el cliente la lee y la muestra.
 *
 * <p>Formato del mensaje: {@code LOBBY;idHost;circuito;vueltas;modoGrilla;torneo;calendario;capacidad;cantidad;id;nombre;piloto;listo;nivelBot;id;nombre...}
 * donde {@code circuito} y {@code piloto} son índices de {@link Catalogo#CIRCUITOS} y {@link Catalogo#PILOTOS},
 * {@code calendario} son índices de circuitos separados por comas, y
 * {@code nivelBot} es -1 en un jugador de verdad y el número de {@link NivelBot} en un bot.
 *
 * @param torneo     si el host eligió torneo en lugar de carrera única
 * @param calendario los circuitos del torneo en orden; se conserva aunque se vuelva a carrera única
 * @param modoGrilla  0 a mano, 1 clasificación, 2 al azar (ver {@link ModoGrilla}); los autos de {@code jugadores} van en el orden de la grilla
 * @param capacidad  máximo de autos (personas y bots) de esta sala; lo elige el host al crearla
 */
public record EstadoLobby(int idHost, int circuito, int vueltas, ModoGrilla modoGrilla, boolean torneo, List<Integer> calendario,
    int capacidad, List<JugadorLobby> jugadores) {
    private static final int CAMPOS_POR_JUGADOR = 5;
    private static final int CAMPOS_FIJOS = 8;
    private static final String SEPARADOR_CALENDARIO = ",";

    /** @param nivelBot -1 si es una persona; si es un bot, el número de su {@link NivelBot} */
    public record JugadorLobby(int id, String nombre, int piloto, boolean listo, int nivelBot) {
        public boolean esBot() {
            return nivelBot >= 0;
        }
    }

    public static EstadoLobby vacio() {
        return new EstadoLobby(-1, 0, Config.VUELTAS, ModoGrilla.MANUAL, false, List.of(), Config.MAX_JUGADORES, List.of());
    }

    public String aLinea() {
        StringJoiner circuitos = new StringJoiner(SEPARADOR_CALENDARIO);
        for (int indice : calendario) {
            circuitos.add(String.valueOf(indice));
        }
        StringBuilder linea = new StringBuilder(Protocolo.armar(Protocolo.LOBBY, idHost, circuito, vueltas, modoGrilla.ordinal(), torneo ? 1 : 0,
            circuitos, capacidad, jugadores.size()));
        for (JugadorLobby j : jugadores) {
            linea.append(';').append(j.id()).append(';').append(j.nombre()).append(';').append(j.piloto())
                .append(';').append(j.listo() ? 1 : 0).append(';').append(j.nivelBot());
        }
        return linea.toString();
    }

    /** Lee un mensaje LOBBY. Si viene mal formado devuelve un lobby vacío en lugar de fallar. */
    public static EstadoLobby desde(Mensaje mensaje) {
        int circuito = mensaje.entero(1, 0);
        int vueltas = mensaje.entero(2, Config.VUELTAS);
        int capacidad = Math.max(Config.MIN_JUGADORES, Math.min(Config.MAX_JUGADORES, mensaje.entero(6, Config.MAX_JUGADORES)));
        int cantidad = mensaje.entero(7, 0);
        List<JugadorLobby> lista = new ArrayList<>();
        for (int i = 0; i < cantidad && i < Config.MAX_JUGADORES; i++) {
            int base = CAMPOS_FIJOS + i * CAMPOS_POR_JUGADOR;
            if (base + CAMPOS_POR_JUGADOR - 1 >= mensaje.campos().size()) {
                break; // el mensaje trae menos jugadores de los que dice
            }
            lista.add(new JugadorLobby(mensaje.entero(base, -1), mensaje.campo(base + 1), mensaje.entero(base + 2, 0),
                mensaje.campo(base + 3).equals("1"), mensaje.entero(base + 4, -1)));
        }
        return new EstadoLobby(mensaje.entero(0, -1), Catalogo.circuitoValido(circuito) ? circuito : 0,
            Math.max(Config.MIN_VUELTAS, Math.min(Config.MAX_VUELTAS, vueltas)), ModoGrilla.desde(mensaje.entero(3, 0)), mensaje.campo(4).equals("1"),
            leerCalendario(mensaje.campo(5)), capacidad, List.copyOf(lista));
    }

    private static List<Integer> leerCalendario(String campo) {
        List<Integer> calendario = new ArrayList<>();
        for (String parte : campo.split(SEPARADOR_CALENDARIO)) {
            try {
                int indice = Integer.parseInt(parte.trim());
                if (Catalogo.circuitoValido(indice)) {
                    calendario.add(indice);
                }
            } catch (NumberFormatException e) {
                // Calendario vacío o campo mal formado: se saltea.
            }
        }
        return List.copyOf(calendario);
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

    /** Si antes de la carrera hay clasificación. */
    public boolean clasificacion() {
        return modoGrilla == ModoGrilla.CLASIFICACION;
    }

    /** Cuántos de los autos son personas (los bots no cuentan). */
    public int cantidadDePersonas() {
        int personas = 0;
        for (JugadorLobby j : jugadores) {
            personas += j.esBot() ? 0 : 1;
        }
        return personas;
    }

    /** Si todavía entra un auto más en la sala. */
    public boolean hayLugarParaBot() {
        return jugadores.size() < capacidad;
    }

    public boolean todosListos() {
        for (JugadorLobby j : jugadores) {
            if (!j.listo()) {
                return false;
            }
        }
        return !jugadores.isEmpty();
    }

    /** Hay autos suficientes (contando los bots, que siempre están listos) y todos listos: el host ya puede iniciar. */
    public boolean sePuedeIniciar() {
        return jugadores.size() >= Config.MIN_JUGADORES && todosListos();
    }
}
