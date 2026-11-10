package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La carrera simulada en esta misma PC (prueba local, sin red): paso fijo de 1 / TICKS_POR_SEGUNDO con un
 * acumulador, y la foto interpolada entre el último tick y el siguiente para dibujar sin saltos. Además de las personas
 * puede haber bots: su {@link PilotoBot} produce una {@link EntradaAuto} igual que lo haría una persona, como en el servidor.
 */
public class SimulacionLocal implements FuenteCarrera {
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    /** Tope de tiempo por cuadro, para que un tirón (por ejemplo al mover la ventana) no dispare cientos de ticks. */
    private static final float MAX_TIEMPO_CUADRO = 0.25f;

    private final Carrera carrera;
    private final List<Integer> ids = new ArrayList<>();
    /** El piloto del catálogo de cada auto, por id (las personas primero y después los bots). */
    private final List<Integer> pilotos = new ArrayList<>();
    private final Map<Integer, PilotoBot> pilotosBot = new HashMap<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();
    private float acumulador;
    private float alfa;

    /** Carrera con esa cantidad de jugadores en el mismo teclado, llamados "Jugador 1", "Jugador 2"... */
    public SimulacionLocal(Circuito circuito, int jugadores) {
        this(circuito, new PartidaLocal(jugadores == 1 ? List.of(0) : List.of(0, 2), 0, NivelBot.MEDIO));
    }

    public SimulacionLocal(Circuito circuito, PartidaLocal partida) {
        carrera = new Carrera(circuito, Config.VUELTAS);
        for (int i = 0; i < partida.jugadores(); i++) {
            carrera.agregarAuto(i, "Jugador " + (i + 1));
            ids.add(i);
            pilotos.add(partida.pilotosJugadores().get(i));
        }
        if (partida.bots() > 0) {
            // El recorrido solo depende del circuito y del nivel: todos los bots lo comparten.
            RecorridoBot recorrido = new RecorridoBot(circuito, partida.nivel());
            Random azar = new Random();
            int id = partida.jugadores();
            for (int piloto : partida.pilotosBots()) {
                carrera.agregarAuto(id, Catalogo.piloto(piloto).apellido());
                pilotos.add(piloto);
                pilotosBot.put(id, new PilotoBot(recorrido, partida.nivel(), azar.nextLong()));
                id++;
            }
        }
    }

    @Override
    public void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales) {
        acumulador += Math.min(delta, MAX_TIEMPO_CUADRO);
        while (acumulador >= PASO) {
            entradas.putAll(entradasLocales);
            decidirBots();
            carrera.actualizar(entradas, PASO);
            acumulador -= PASO;
        }
        alfa = acumulador / PASO;
    }

    /** Los bots solo manejan con la carrera en curso, igual que en el servidor. */
    private void decidirBots() {
        if (carrera.estado() != EstadoCarrera.EN_CURSO) {
            return;
        }
        for (Map.Entry<Integer, PilotoBot> bot : pilotosBot.entrySet()) {
            Participante participante = carrera.participante(bot.getKey());
            if (!participante.terminado()) {
                bot.getValue().decidir(participante, carrera.participantes(),
                    entradas.computeIfAbsent(bot.getKey(), id -> new EntradaAuto()), PASO);
            }
        }
    }

    @Override
    public FotoCarrera foto() {
        return carrera.foto(alfa);
    }

    @Override
    public List<Integer> idsLocales() {
        return ids;
    }

    @Override
    public String nombre(int id) {
        Participante p = carrera.participante(id);
        return p != null ? p.nombre() : "?";
    }

    @Override
    public int piloto(int id) {
        return id >= 0 && id < pilotos.size() ? pilotos.get(id) : 0;
    }

    @Override
    public int lugarDeLargada(int id) {
        return id; // se agregaron en orden de id
    }

    @Override
    public int vueltasTotales() {
        return carrera.vueltasTotales();
    }

    @Override
    public List<ResultadoJugador> resultados() {
        return carrera.estado() == EstadoCarrera.TERMINADA ? carrera.resultados() : null;
    }
}
