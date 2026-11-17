package ar.edu.et35.racing.juego;

public enum NivelBot {
    FACIL("FÁCIL", 0f, 0.55f, 0.5f, 0.9f, 9f, 1f, 0.75f),
    MEDIO("MEDIO", 0.5f, 0.72f, 0.68f, 0.94f, 18f, 0.6f, 0.5f),
    DIFICIL("DIFÍCIL", 1f, 0.9f, 0.9f, 1f, 0f, 0f, 0f);

    private final String nombre;
    private final float usoDeTrazada;
    private final float margenAgarre;
    private final float margenFreno;
    private final float tope;
    private final float segundosEntreErrores;
    private final float duracionError;
    private final float desvioError;

    NivelBot(String nombre, float usoDeTrazada, float margenAgarre, float margenFreno, float tope,
             float segundosEntreErrores, float duracionError, float desvioError) {
        this.nombre = nombre;
        this.usoDeTrazada = usoDeTrazada;
        this.margenAgarre = margenAgarre;
        this.margenFreno = margenFreno;
        this.tope = tope;
        this.segundosEntreErrores = segundosEntreErrores;
        this.duracionError = duracionError;
        this.desvioError = desvioError;
    }

    public static NivelBot desde(int numero) {
        return numero >= 0 && numero < values().length ? values()[numero] : null;
    }

    public String nombre() {
        return nombre;
    }

    public float usoDeTrazada() {
        return usoDeTrazada;
    }

    public float margenAgarre() {
        return margenAgarre;
    }

    public float margenFreno() {
        return margenFreno;
    }

    public float tope() {
        return tope;
    }

    public float segundosEntreErrores() {
        return segundosEntreErrores;
    }

    public float duracionError() {
        return duracionError;
    }

    public float desvioError() {
        return desvioError;
    }
}
