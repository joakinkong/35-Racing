package ar.edu.et35.racing.juego;

/** En qué nivel de un cruce está un auto: por el tramo de abajo, por el de arriba o lejos de cualquier cruce. */
public enum NivelCruce {
    FUERA,
    ABAJO,
    ARRIBA;

    /** Dos autos en niveles distintos de un cruce pasan uno por encima del otro: no se chocan ni se esquivan. */
    public boolean seCruzaCon(NivelCruce otro) {
        return this != FUERA && otro != FUERA && this != otro;
    }
}
