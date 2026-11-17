package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;

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

    public static PartidaLocal dosJugadoresSinBots() {
        return new PartidaLocal(List.of(0, 2), 0, NivelBot.MEDIO);
    }

    public static int maxBots(int jugadores) {
        return Config.MAX_JUGADORES - jugadores;
    }

    public int jugadores() {
        return pilotosJugadores.size();
    }

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
