package ar.edu.et35.racing.juego;

import java.util.List;
import java.util.Map;

/**
 * De dónde saca la pantalla de carrera lo que dibuja. Hay dos: {@link SimulacionLocal} (la prueba local, que simula
 * la carrera en esta PC) y la carrera en red (que no simula: dibuja lo que manda el servidor). Así la misma pantalla
 * sirve para las dos y la prueba local sigue andando sin red.
 */
public interface FuenteCarrera {

    /**
     * Avanza delta segundos con lo que están apretando los jugadores de esta PC.
     *
     * @param entradasLocales la entrada de cada jugador local, por id
     */
    void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales);

    /** Lo que hay que dibujar ahora, o null si todavía no hay nada (en red, antes del primer ESTADO). */
    FotoCarrera foto();

    /** Ids de los autos que se manejan desde esta PC, en orden (J1, J2). */
    List<Integer> idsLocales();

    String nombre(int id);

    /** Índice del color del auto en {@code Paleta.COLORES_AUTOS}. */
    int color(int id);

    /** Lugar en la grilla de largada (0 = primero); sirve para ubicar la cámara antes de que haya una foto. */
    int lugarDeLargada(int id);

    int vueltasTotales();

    /** Resultados finales, o null mientras la carrera no terminó. */
    List<ResultadoJugador> resultados();
}
