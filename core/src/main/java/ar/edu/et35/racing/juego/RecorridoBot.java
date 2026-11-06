package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;

/**
 * El camino que sigue un bot en un circuito y la velocidad a la que quiere ir en cada punto, una muestra por cada muestra
 * de la línea central. Se calcula una sola vez, al iniciar la carrera.
 *
 * <p><b>El camino.</b> La trazada ideal se busca como una banda elástica rígida: cada punto solo se puede correr de costado
 * dentro de la pista y se va acomodando donde la línea queda menos doblada. Así la trazada abre antes de la curva, toca la
 * cuerda y abre a la salida, sin que nadie le diga dónde queda cada curva. Primero se resuelve la forma grande (puntos
 * lejanos entre sí) y después los detalles.
 *
 * <p><b>La velocidad.</b> Primero, lo que permite la curva en cada punto, según las gomas del auto. Después se recorre el
 * circuito hacia adelante (no se puede acelerar más de lo que da el motor) y hacia atrás (hay que llegar frenado a cada curva).
 */
public final class RecorridoBot {
    /** Lo que queda libre entre el costado del auto y el borde del asfalto. */
    private static final float MARGEN_BORDE = 12f;
    /** Distancia entre los puntos que se acomodan juntos, en muestras: de lo grande a lo chico. */
    private static final int[] ESCALAS = {64, 32, 16, 8, 4};
    private static final int ITERACIONES_POR_ESCALA = 400;
    /** Muestras a cada lado para medir la curvatura: con el paso de 8 px son 40 px. */
    private static final int VENTANA_CURVATURA = 5;
    private static final float RADIO_RECTA = 1e6f;
    private static final float VELOCIDAD_MINIMA = 40f;
    /** Dos pasadas alrededor del circuito alcanzan para que el principio y el final de la vuelta empalmen. */
    private static final int VUELTAS_DE_PERFIL = 2;

    private final int muestras;
    private final float paso;
    private final float[] x;
    private final float[] y;
    private final float[] velocidad;
    private final float[] centroX;
    private final float[] centroY;
    private final float[] normalX;
    private final float[] normalY;
    private final float[] limite;

    public RecorridoBot(Circuito circuito, NivelBot nivel) {
        Circuito.Geometria g = circuito.geometria();
        muestras = g.muestras();
        float[] linea = g.linea();
        centroX = new float[muestras];
        centroY = new float[muestras];
        normalX = new float[muestras];
        normalY = new float[muestras];
        limite = new float[muestras];
        for (int i = 0; i < muestras; i++) {
            centroX[i] = linea[2 * i];
            centroY[i] = linea[2 * i + 1];
            limite[i] = Math.max(0f, g.anchos()[i] / 2f - ParametrosAuto.RADIO_COLISION - MARGEN_BORDE);
        }
        paso = (float) Math.hypot(centroX[1] - centroX[0], centroY[1] - centroY[0]);
        calcularNormales();

        float[] corrimiento = trazadaIdeal();
        x = new float[muestras];
        y = new float[muestras];
        for (int i = 0; i < muestras; i++) {
            float lado = corrimiento[i] * nivel.usoDeTrazada();
            x[i] = centroX[i] + normalX[i] * lado;
            y[i] = centroY[i] + normalY[i] * lado;
        }
        velocidad = calcularVelocidades(nivel);
    }

    private void calcularNormales() {
        for (int i = 0; i < muestras; i++) {
            int antes = indice(i - 2);
            int despues = indice(i + 2);
            float tx = centroX[despues] - centroX[antes];
            float ty = centroY[despues] - centroY[antes];
            float largo = (float) Math.hypot(tx, ty);
            normalX[i] = largo > 0f ? -ty / largo : 0f;
            normalY[i] = largo > 0f ? tx / largo : 0f;
        }
    }

    /** Devuelve, para cada muestra, cuánto se corre del centro. */
    private float[] trazadaIdeal() {
        float[] corrimiento = new float[muestras];
        for (int escala : ESCALAS) {
            for (int vuelta = 0; vuelta < ITERACIONES_POR_ESCALA; vuelta++) {
                for (int i = 0; i < muestras; i++) {
                    corrimiento[i] = lugarMenosDoblado(corrimiento, i, escala);
                }
            }
        }
        return corrimiento;
    }

    /**
     * El corrimiento de una muestra que deja la línea menos doblada respecto de sus vecinas. El promedio con esos pesos
     * (4 los cercanos, -1 los lejanos) es el mínimo de la energía de doblado de una viga, no solo el camino más corto,
     * que haría pasar al auto con un quiebre por la cuerda.
     */
    private float lugarMenosDoblado(float[] corrimiento, int i, int escala) {
        int a = indice(i - 2 * escala);
        int b = indice(i - escala);
        int c = indice(i + escala);
        int d = indice(i + 2 * escala);
        float objetivoX = (-puntoX(corrimiento, a) + 4f * puntoX(corrimiento, b) + 4f * puntoX(corrimiento, c) - puntoX(corrimiento, d)) / 6f;
        float objetivoY = (-puntoY(corrimiento, a) + 4f * puntoY(corrimiento, b) + 4f * puntoY(corrimiento, c) - puntoY(corrimiento, d)) / 6f;
        float lado = (objetivoX - centroX[i]) * normalX[i] + (objetivoY - centroY[i]) * normalY[i];
        return Math.max(-limite[i], Math.min(limite[i], lado));
    }

    private float puntoX(float[] corrimiento, int i) {
        return centroX[i] + normalX[i] * corrimiento[i];
    }

    private float puntoY(float[] corrimiento, int i) {
        return centroY[i] + normalY[i] * corrimiento[i];
    }

    private float[] calcularVelocidades(NivelBot nivel) {
        float[] v = new float[muestras];
        float tope = ParametrosAuto.VELOCIDAD_MAX * nivel.tope();
        for (int i = 0; i < muestras; i++) {
            float radio = radioEn(i);
            float porAgarre = (float) Math.sqrt(nivel.margenAgarre() * ParametrosAuto.ACELERACION_LATERAL * radio);
            // Con poco radio, lo que limita ya no son las gomas sino lo rápido que puede girar el auto.
            float porGiro = ParametrosAuto.GIRO_MAX * MathUtils.degreesToRadians * radio;
            v[i] = Math.max(VELOCIDAD_MINIMA, Math.min(tope, Math.min(porAgarre, Math.max(porGiro, ParametrosAuto.VELOCIDAD_GIRO_COMPLETO))));
        }
        float frenado = ParametrosAuto.FRENADO * nivel.margenFreno();
        for (int vuelta = 0; vuelta < VUELTAS_DE_PERFIL; vuelta++) {
            for (int k = 0; k < muestras; k++) {
                int i = indice(k);
                int siguiente = indice(k + 1);
                float aceleracion = ParametrosAuto.ACELERACION * (1f - ParametrosAuto.PERDIDA_ACELERACION
                    * Math.min(1f, v[i] / ParametrosAuto.VELOCIDAD_MAX));
                v[siguiente] = Math.min(v[siguiente], (float) Math.sqrt(v[i] * v[i] + 2f * aceleracion * paso));
            }
            for (int k = muestras - 1; k >= 0; k--) {
                int i = indice(k);
                int siguiente = indice(k + 1);
                v[i] = Math.min(v[i], (float) Math.sqrt(v[siguiente] * v[siguiente] + 2f * frenado * paso));
            }
        }
        return v;
    }

    /** Radio de la circunferencia que pasa por tres puntos del camino. */
    private float radioEn(int i) {
        int a = indice(i - VENTANA_CURVATURA);
        int c = indice(i + VENTANA_CURVATURA);
        float abX = x[i] - x[a];
        float abY = y[i] - y[a];
        float acX = x[c] - x[a];
        float acY = y[c] - y[a];
        float doble = Math.abs(abX * acY - abY * acX);
        if (doble < 1e-3f) {
            return RADIO_RECTA;
        }
        float ab = (float) Math.hypot(abX, abY);
        float bc = (float) Math.hypot(x[c] - x[i], y[c] - y[i]);
        float ac = (float) Math.hypot(acX, acY);
        return Math.min(RADIO_RECTA, ab * bc * ac / (2f * doble));
    }

    int indice(int i) {
        return Math.floorMod(i, muestras);
    }

    int muestras() {
        return muestras;
    }

    float paso() {
        return paso;
    }

    float x(int i) {
        return x[i];
    }

    float y(int i) {
        return y[i];
    }

    float velocidad(int i) {
        return velocidad[i];
    }

    /** El punto a esa distancia de costado del centro de la pista, sin salirse del asfalto. */
    float xCorrido(int i, float lado) {
        return centroX[i] + normalX[i] * Math.max(-limite[i], Math.min(limite[i], lado));
    }

    float yCorrido(int i, float lado) {
        return centroY[i] + normalY[i] * Math.max(-limite[i], Math.min(limite[i], lado));
    }

    /** Cuánto está corrido un punto respecto del centro de la pista. */
    float ladoDe(int i, float px, float py) {
        return (px - centroX[i]) * normalX[i] + (py - centroY[i]) * normalY[i];
    }

    /** Lo máximo que puede correrse de costado en esa muestra. */
    float limite(int i) {
        return limite[i];
    }
}
