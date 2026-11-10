package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * Cómo se arma una partida local (sin red): cuántas personas comparten el teclado, con qué piloto corre cada una, cuántos
 * bots hay en pista y de qué nivel. Los bots toman los pilotos que no eligió nadie, en el orden del catálogo.
 *
 * @param pilotosJugadores un piloto (índice de {@link Catalogo#PILOTOS}) por persona; su tamaño es la cantidad de personas (1 o 2)
 * @param bots             cuántos bots corren; entre 0 y {@link #maxBots(int)}
 * @param nivel            nivel de todos los bots
 */
public record PartidaLocal(List<Integer> pilotosJugadores, int bots, NivelBot nivel) {
    public static final int MAX_JUGADORES_LOCALES = 2;

    public PartidaLocal {
        pilotosJugadores = List.copyOf(pilotosJugadores);
        if (pilotosJugadores.isEmpty() || pilotosJugadores.size() > MAX_JUGADORES_LOCALES) {
            throw new IllegalArgumentException("Jugadores locales: de 1 a " + MAX_JUGADORES_LOCALES);
        }
        if (bots < 0 || bots > maxBots(pilotosJugadores.size())) {
            throw new IllegalArgumentException("Bots: de 0 a " + maxBots(pilotosJugadores.size()));
        }
    }

    /** La partida de antes: dos personas, sin bots. */
    public static PartidaLocal dosJugadoresSinBots() {
        return new PartidaLocal(List.of(0, 2), 0, NivelBot.MEDIO);
    }

    /** Cuántos bots caben junto a esa cantidad de personas: el resto de los autos de la grilla. */
    public static int maxBots(int jugadores) {
        return Config.MAX_JUGADORES - jugadores;
    }

    public int jugadores() {
        return pilotosJugadores.size();
    }

    /** Los pilotos de los bots: los que no usan las personas, empezando por el primero del catálogo. */
    public List<Integer> pilotosBots() {
        List<Integer> libres = new ArrayList<>();
        for (int piloto = 0; piloto < Catalogo.PILOTOS.size() && libres.size() < bots; piloto++) {
            if (!pilotosJugadores.contains(piloto)) {
                libres.add(piloto);
            }
        }
        return libres;
    }
}
