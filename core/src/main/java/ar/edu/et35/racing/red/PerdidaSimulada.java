package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Descarta a propósito una fracción de los paquetes UDP recibidos, para mostrar que el juego tolera la pérdida
 * (lo prometió la propuesta). Arranca con {@link Config#PERDIDA_UDP_SIMULADA}; en la carrera, con DEBUG_RED
 * activo, F8 la cambia en vivo. Afecta a todos los receptores UDP de esta PC.
 */
public final class PerdidaSimulada {
    private static final float[] NIVELES = {0f, 0.2f, 0.5f};
    /** La leen los hilos receptores y la cambia el hilo de render: volatile para que vean el último valor. */
    private static volatile float fraccion = Config.PERDIDA_UDP_SIMULADA;

    private PerdidaSimulada() {
    }

    public static float fraccion() {
        return fraccion;
    }

    public static void fijar(float nueva) {
        fraccion = Math.max(0f, Math.min(1f, nueva));
    }

    /** Pasa al siguiente nivel: 0 % -> 20 % -> 50 % -> 0 %. */
    public static void siguienteNivel() {
        float actual = fraccion;
        for (int i = 0; i < NIVELES.length; i++) {
            if (actual < NIVELES[i] + 0.001f) {
                fijar(NIVELES[(i + 1) % NIVELES.length]);
                return;
            }
        }
        fijar(0f);
    }

    /** Decide al azar si este paquete se descarta. */
    public static boolean descartar() {
        float f = fraccion;
        return f > 0f && ThreadLocalRandom.current().nextFloat() < f;
    }
}
