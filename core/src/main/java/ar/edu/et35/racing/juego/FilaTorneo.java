package ar.edu.et35.racing.juego;

/**
 * Un auto en la tabla del torneo.
 *
 * @param puntos        los acumulados en todo el torneo
 * @param puntosCarrera los que sumó en la última carrera corrida
 * @param victorias     carreras ganadas
 */
public record FilaTorneo(int id, String nombre, int puntos, int puntosCarrera, int victorias) {
}
