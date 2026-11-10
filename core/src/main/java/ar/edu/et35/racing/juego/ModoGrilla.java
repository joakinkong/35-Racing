package ar.edu.et35.racing.juego;

/**
 * Cómo se arma la grilla de largada de una carrera. El número es el que viaja por la red.
 */
public enum ModoGrilla {
    /** Sin clasificación: se larga con el orden que armó el host en la sala (mover lugares o sortear). */
    MANUAL("A MANO"),
    /** Antes de cada carrera hay una clasificación de una vuelta y el orden de los tiempos arma la grilla. */
    CLASIFICACION("CLASIFICACIÓN"),
    /** Sin clasificación: la grilla se sortea otra vez antes de cada carrera (en un torneo, en todas). */
    AL_AZAR("AL AZAR");

    private final String nombre;

    ModoGrilla(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    /** El modo con ese número, o {@link #MANUAL} si no existe (un dato roto no tira el juego). */
    public static ModoGrilla desde(int numero) {
        return numero >= 0 && numero < values().length ? values()[numero] : MANUAL;
    }

    /** El que sigue en la lista, volviendo al primero después del último. */
    public ModoGrilla siguiente() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ModoGrilla anterior() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }
}
