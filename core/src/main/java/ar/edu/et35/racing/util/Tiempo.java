package ar.edu.et35.racing.util;

/** Formato de tiempos de carrera. */
public final class Tiempo {
    private static final String SIN_TIEMPO = "--:--.---";

    private Tiempo() {
    }

    /** Convierte segundos a m:ss.mmm. Un valor negativo o NaN (sin tiempo todavía) se muestra como guiones. */
    public static String formato(float segundos) {
        if (Float.isNaN(segundos) || Float.isInfinite(segundos) || segundos < 0f) {
            return SIN_TIEMPO;
        }
        int milis = Math.round(segundos * 1000f);
        int minutos = milis / 60000;
        int resto = milis % 60000;
        return String.format("%d:%02d.%03d", minutos, resto / 1000, resto % 1000);
    }
}
