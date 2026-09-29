package ar.edu.et35.racing.juego;

/**
 * Un auto en un instante de la carrera: lo que hace falta para dibujarlo y mostrar su HUD. Es lo mismo que viaja
 * en el paquete ESTADO (docs/PROTOCOLO.md, sección 4), salvo la velocidad (vx, vy), que no viaja: en red el
 * cliente la estima con la diferencia entre dos estados.
 *
 * @param angulo    grados (0 = derecha, 90 = arriba)
 * @param posicion  1 = puntero
 * @param tiempo    segundos de la vuelta actual; si terminó, el tiempo total
 * @param mejorVuelta segundos, o NaN si todavía no completó ninguna
 */
public record FotoAuto(int id, float x, float y, float angulo, float vx, float vy, int vueltas, int posicion,
                       boolean terminado, boolean desconectado, float tiempo, float mejorVuelta) {

    /** El mismo auto en otra posición, ángulo y velocidad (lo usa la interpolación). */
    public FotoAuto conMovimiento(float nuevoX, float nuevoY, float nuevoAngulo, float nuevaVx, float nuevaVy) {
        return new FotoAuto(id, nuevoX, nuevoY, nuevoAngulo, nuevaVx, nuevaVy, vueltas, posicion, terminado,
            desconectado, tiempo, mejorVuelta);
    }
}
