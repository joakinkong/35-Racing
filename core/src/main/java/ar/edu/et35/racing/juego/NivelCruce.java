package ar.edu.et35.racing.juego;

public enum NivelCruce {
    FUERA,
    ABAJO,
    ARRIBA;

    public boolean seCruzaCon(NivelCruce otro) {
        return this != FUERA && otro != FUERA && this != otro;
    }
}
