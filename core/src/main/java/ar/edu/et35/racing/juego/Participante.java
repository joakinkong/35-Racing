package ar.edu.et35.racing.juego;

/**
 * Un auto dentro de una carrera, con su progreso: vueltas, próximo checkpoint y tiempos.
 *
 * <p>Los checkpoints van de 0 a N-1 y el 0 es la meta. El auto sale detrás de la meta, así que el primero
 * que tiene que pasar es el 1; después de pasar el N-1 le toca la meta (0), que completa la vuelta.
 */
public class Participante {
    private final int id;
    private final String nombre;
    private final Auto auto;
    private final int cantidadCheckpoints;

    private int vueltas;
    private int siguienteCheckpoint;
    private float tiempoVuelta;
    private float mejorVuelta = Float.NaN;
    private float tiempoTotal = Float.NaN;
    private boolean terminado;
    private boolean desconectado;

    Participante(int id, String nombre, Auto auto, int cantidadCheckpoints) {
        this.id = id;
        this.nombre = nombre;
        this.auto = auto;
        this.cantidadCheckpoints = cantidadCheckpoints;
        this.siguienteCheckpoint = 1 % cantidadCheckpoints;
    }

    /** Suma tiempo a la vuelta en curso. Después de terminar el reloj se detiene. */
    void sumarTiempo(float dt) {
        if (!terminado) {
            tiempoVuelta += dt;
        }
    }

    /** El auto cruzó, hacia adelante, el checkpoint que le tocaba. */
    void pasarPorCheckpoint(float tiempoCarrera, int vueltasTotales) {
        if (siguienteCheckpoint != 0) {
            siguienteCheckpoint = (siguienteCheckpoint + 1) % cantidadCheckpoints;
            return;
        }
        // Cruzó la meta con todos los checkpoints en orden: vuelta completa.
        vueltas++;
        mejorVuelta = Float.isNaN(mejorVuelta) ? tiempoVuelta : Math.min(mejorVuelta, tiempoVuelta);
        tiempoVuelta = 0f;
        siguienteCheckpoint = 1 % cantidadCheckpoints;
        if (vueltas >= vueltasTotales) {
            terminado = true;
            tiempoTotal = tiempoCarrera;
        }
    }

    /** Avance total medido en checkpoints; sirve para ordenar posiciones. */
    int progreso() {
        return vueltas * cantidadCheckpoints + (siguienteCheckpoint == 0 ? cantidadCheckpoints : siguienteCheckpoint);
    }

    public int id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public Auto auto() {
        return auto;
    }

    public int vueltas() {
        return vueltas;
    }

    public int siguienteCheckpoint() {
        return siguienteCheckpoint;
    }

    /** Tiempo de la vuelta en curso (segundos). */
    public float tiempoVuelta() {
        return tiempoVuelta;
    }

    /** Mejor vuelta completada, o NaN si todavía no completó ninguna. */
    public float mejorVuelta() {
        return mejorVuelta;
    }

    /** Tiempo total de carrera, o NaN si todavía no terminó. */
    public float tiempoTotal() {
        return tiempoTotal;
    }

    public boolean terminado() {
        return terminado;
    }

    void marcarDesconectado() {
        desconectado = true;
    }

    /** El jugador se fue de la partida; su auto ya no está en la pista. */
    public boolean desconectado() {
        return desconectado;
    }
}
