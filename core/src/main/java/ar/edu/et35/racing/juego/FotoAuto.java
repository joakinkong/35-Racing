package ar.edu.et35.racing.juego;

public record FotoAuto(int id, float x, float y, float angulo, float vx, float vy, int vueltas, int posicion,
                       boolean terminado, boolean desconectado, float tiempo, float mejorVuelta) {

    public FotoAuto conMovimiento(float nuevoX, float nuevoY, float nuevoAngulo, float nuevaVx, float nuevaVy) {
        return new FotoAuto(id, nuevoX, nuevoY, nuevoAngulo, nuevaVx, nuevaVy, vueltas, posicion, terminado,
            desconectado, tiempo, mejorVuelta);
    }
}
