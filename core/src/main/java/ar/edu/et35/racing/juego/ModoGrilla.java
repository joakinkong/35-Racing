package ar.edu.et35.racing.juego;

public enum ModoGrilla {
    MANUAL("A MANO"),
    CLASIFICACION("CLASIFICACIÓN"),
    AL_AZAR("AL AZAR");

    private final String nombre;

    ModoGrilla(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public static ModoGrilla desde(int numero) {
        return numero >= 0 && numero < values().length ? values()[numero] : MANUAL;
    }

    public ModoGrilla siguiente() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ModoGrilla anterior() {
        return values()[Math.floorMod(ordinal() - 1, values().length)];
    }
}
