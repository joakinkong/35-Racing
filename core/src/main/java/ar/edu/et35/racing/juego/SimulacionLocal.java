package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulacionLocal implements FuenteCarrera {
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    private static final float MAX_TIEMPO_CUADRO = 0.25f;

    private final Carrera carrera;
    private final List<Integer> ids = new ArrayList<>();
    private final List<Integer> pilotos = new ArrayList<>();
    private final Map<Integer, PilotoBot> pilotosBot = new HashMap<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();
    private float acumulador;
    private float alfa;

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
        return id;
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
