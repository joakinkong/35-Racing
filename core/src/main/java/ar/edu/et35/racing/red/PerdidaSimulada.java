package ar.edu.et35.racing.red;

import ar.edu.et35.racing.util.Config;
import java.util.concurrent.ThreadLocalRandom;

public final class PerdidaSimulada {
    private static final float[] NIVELES = {0f, 0.2f, 0.5f, 1f};
    private static volatile float fraccion = Config.PERDIDA_UDP_SIMULADA;

    private PerdidaSimulada() {
    }

    public static float fraccion() {
        return fraccion;
    }

    public static void fijar(float nueva) {
        fraccion = Math.max(0f, Math.min(1f, nueva));
    }

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

    public static boolean descartar() {
        float f = fraccion;
        return f > 0f && ThreadLocalRandom.current().nextFloat() < f;
    }
}
