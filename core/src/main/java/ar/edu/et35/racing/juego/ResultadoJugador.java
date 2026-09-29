package ar.edu.et35.racing.juego;

/**
 * Fila de la tabla de resultados. Si el jugador no terminó, tiempoTotal es NaN; si no completó ninguna
 * vuelta, mejorVuelta es NaN.
 */
public record ResultadoJugador(int posicion, int id, String nombre, float tiempoTotal, float mejorVuelta) {
}
