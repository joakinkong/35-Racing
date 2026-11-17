package ar.edu.et35.racing.juego;

import java.util.List;
import java.util.Map;

public interface FuenteCarrera {

    void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales);

    FotoCarrera foto();

    List<Integer> idsLocales();

    String nombre(int id);

    int piloto(int id);

    int lugarDeLargada(int id);

    int vueltasTotales();

    List<ResultadoJugador> resultados();
}
