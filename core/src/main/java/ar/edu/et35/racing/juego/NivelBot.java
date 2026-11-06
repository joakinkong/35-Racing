package ar.edu.et35.racing.juego;

/**
 * Dificultad de un bot. Todos manejan el mismo auto con la misma física y los mismos controles que un jugador: lo que
 * cambia es qué tan cerca del límite van y cuánto se equivocan.
 */
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

    /**
     * @param usoDeTrazada         0 = va por el centro de la pista; 1 = usa todo el ancho, por la trazada ideal
     * @param margenAgarre         fracción de la aceleración lateral del auto que se anima a usar en las curvas
     * @param margenFreno          fracción de la capacidad de frenado que usa para calcular dónde empezar a frenar
     * @param tope                 fracción de la velocidad máxima a la que llega en las rectas
     * @param segundosEntreErrores tiempo medio entre errores; 0 = no se equivoca nunca
     * @param duracionError        cuánto dura cada error (s)
     * @param desvioError          cuánto se abre durante un error, como fracción del medio ancho de la pista
     */
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

    /** El nivel con ese número (el que viaja por la red), o null si no existe. */
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
