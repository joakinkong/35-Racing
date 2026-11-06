package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.ZonaCruce;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calcula los pianos de un circuito a partir de la línea central: franjas rojas y blancas sobre el borde del asfalto, en
 * el interior de cada curva cerrada y en el exterior de su salida. Son solo dibujo: la grilla de colisión no los conoce,
 * así que no cambian cómo se maneja, y como se calculan al cargar, los JSON de los circuitos no se tocan.
 */
final class Pianos {
    /** Una curva lleva piano si su radio es menor que este (px). */
    private static final float RADIO_MAXIMO = 500f;
    /** Muestras antes y después con las que se mide la curvatura (48 px): más corto, el ruido del trazado parece curva. */
    private static final int VENTANA_CURVATURA = 6;
    /** Dos tramos de curva hacia el mismo lado separados por hasta estas muestras son la misma curva. */
    private static final int HUECO_MAXIMO = 5;
    /** Una curva de menos muestras que estas no lleva piano: quedaría una mancha de dos o tres franjas. */
    private static final int MUESTRAS_MINIMAS = 8;
    /** Parte final de la curva (la salida) que lleva piano del lado de afuera. */
    private static final float FRACCION_SALIDA = 0.4f;
    /** Muestras que el piano de afuera sigue después del final de la curva. */
    private static final int MUESTRAS_DESPUES_DE_SALIDA = 6;
    /** Ancho de la cinta (px). */
    private static final float ANCHO = 10f;
    /** Muestras de cada franja de color: 2 muestras son 16 px. */
    private static final int MUESTRAS_POR_FRANJA = 2;
    /** Muestras vecinas contra las que se comprueba que un punto sea borde del asfalto (320 px). */
    private static final int VECINAS_BORDE = 40;
    /** Cuánto puede quedar un punto adentro de otro círculo y seguir contando como borde (px). */
    private static final float TOLERANCIA_BORDE = 2f;
    /** Un pedazo de cinta de menos muestras que estas no se dibuja: en una esquina viva quedaría una mancha. */
    private static final int PEDAZO_MINIMO = 4;

    /** Los triángulos de un color de una cinta, con coordenadas de mundo. */
    record Franjas(float[] vertices, short[] triangulos, boolean rojas) {
    }

    private Pianos() {
    }

    static List<Franjas> calcular(Circuito circuito) {
        Circuito.Geometria g = circuito.geometria();
        boolean[] prohibida = muestrasEnCruces(circuito);
        int[] curva = marcarCurvas(g, prohibida);
        List<Franjas> resultado = new ArrayList<>();
        int n = g.muestras();
        int inicio = 0;
        while (inicio < n && curva[inicio] != 0) {
            inicio++;
        }
        if (inicio == n) {
            return resultado; // Toda la vuelta es una sola curva: no pasa en ningún circuito real.
        }
        for (int k = 0; k < n; ) {
            int i = (inicio + k) % n;
            if (curva[i] == 0) {
                k++;
                continue;
            }
            int largo = 0;
            while (k + largo < n && curva[(inicio + k + largo) % n] == curva[i]) {
                largo++;
            }
            if (largo >= MUESTRAS_MINIMAS) {
                agregarCinta(g, i, largo, curva[i], resultado);
                int salida = Math.round(largo * FRACCION_SALIDA);
                int desde = i + largo - salida;
                int largoAfuera = salida;
                while (largoAfuera < salida + MUESTRAS_DESPUES_DE_SALIDA && !prohibida[(desde + largoAfuera) % n]) {
                    largoAfuera++;
                }
                agregarCinta(g, desde, largoAfuera, -curva[i], resultado);
            }
            k += largo;
        }
        return resultado;
    }

    /** Donde hay un puente no van pianos: quedarían tapados a medias, o encima del tramo de abajo. */
    private static boolean[] muestrasEnCruces(Circuito circuito) {
        Circuito.Geometria g = circuito.geometria();
        boolean[] prohibida = new boolean[g.muestras()];
        for (ZonaCruce zona : circuito.zonasCruce()) {
            for (int i = 0; i < prohibida.length; i++) {
                prohibida[i] |= zona.alcanza(g.linea()[2 * i], g.linea()[2 * i + 1]);
            }
        }
        return prohibida;
    }

    /** Para cada muestra: 1 si es parte de una curva cerrada a la izquierda, -1 a la derecha, 0 si no. */
    private static int[] marcarCurvas(Circuito.Geometria g, boolean[] prohibida) {
        int n = g.muestras();
        float[] l = g.linea();
        int[] curva = new int[n];
        for (int i = 0; i < n; i++) {
            if (prohibida[i]) {
                continue;
            }
            int a = Math.floorMod(i - VENTANA_CURVATURA, n);
            int c = (i + VENTANA_CURVATURA) % n;
            double antes = Math.atan2(l[2 * i + 1] - l[2 * a + 1], l[2 * i] - l[2 * a]);
            double despues = Math.atan2(l[2 * c + 1] - l[2 * i + 1], l[2 * c] - l[2 * i]);
            double giro = Math.IEEEremainder(despues - antes, 2 * Math.PI);
            double largo = Math.hypot(l[2 * c] - l[2 * i], l[2 * c + 1] - l[2 * i + 1]);
            if (Math.abs(giro) / largo > 1.0 / RADIO_MAXIMO) {
                curva[i] = giro > 0 ? 1 : -1;
            }
        }
        // Se cierran los huecos chicos dentro de una misma curva.
        for (int i = 0; i < n; i++) {
            if (curva[i] == 0 || curva[(i + 1) % n] != 0) {
                continue;
            }
            for (int salto = 2; salto <= HUECO_MAXIMO + 1; salto++) {
                int j = (i + salto) % n;
                if (prohibida[j]) {
                    break;
                }
                if (curva[j] != 0) {
                    if (curva[j] == curva[i]) {
                        for (int h = 1; h < salto; h++) {
                            curva[(i + h) % n] = curva[i];
                        }
                    }
                    break;
                }
            }
        }
        return curva;
    }

    /**
     * Una cinta sobre el borde del asfalto.
     *
     * @param lado 1 = a la izquierda del sentido de la carrera, -1 = a la derecha
     */
    private static void agregarCinta(Circuito.Geometria g, int desde, int largo, int lado, List<Franjas> resultado) {
        boolean[] visible = pedazosVisibles(g, desde, largo, lado);
        for (int color = 0; color < 2; color++) {
            List<float[]> cuadrados = new ArrayList<>();
            for (int k = 0; k < largo; k++) {
                if (visible[k] && (k / MUESTRAS_POR_FRANJA) % 2 == color) {
                    cuadrados.add(cuadrado(g, desde + k, lado));
                }
            }
            if (!cuadrados.isEmpty()) {
                resultado.add(armar(cuadrados, color == 0));
            }
        }
    }

    /**
     * Qué pedazos de la cinta van sobre el borde real del asfalto. En una esquina más cerrada que el ancho de la pista,
     * el punto corrido desde la línea central cae adentro de la propia curva: dibujarlo plegaría la cinta sobre sí misma.
     */
    private static boolean[] pedazosVisibles(Circuito.Geometria g, int desde, int largo, int lado) {
        boolean[] visible = new boolean[largo];
        for (int k = 0; k < largo; k++) {
            visible[k] = esBorde(g, desde + k, lado) && esBorde(g, desde + k + 1, lado);
        }
        for (int k = 0; k < largo; ) {
            int fin = k;
            while (fin < largo && visible[fin] == visible[k]) {
                fin++;
            }
            if (visible[k] && fin - k < PEDAZO_MINIMO) {
                Arrays.fill(visible, k, fin, false);
            }
            k = fin;
        }
        return visible;
    }

    /** Si el punto del borde de esta muestra no queda adentro del círculo de otra muestra vecina. */
    private static boolean esBorde(Circuito.Geometria g, int muestra, int lado) {
        float[] v = new float[4];
        borde(g, muestra, lado, v, 0);
        int n = g.muestras();
        for (int d = -VECINAS_BORDE; d <= VECINAS_BORDE; d++) {
            int j = Math.floorMod(muestra + d, n);
            float radio = g.anchos()[j] / 2f - TOLERANCIA_BORDE;
            float dx = v[0] - g.linea()[2 * j];
            float dy = v[1] - g.linea()[2 * j + 1];
            if (d != 0 && dx * dx + dy * dy < radio * radio) {
                return false;
            }
        }
        return true;
    }

    /** El pedazo de cinta entre una muestra y la siguiente. */
    private static float[] cuadrado(Circuito.Geometria g, int muestra, int lado) {
        float[] v = new float[8];
        borde(g, muestra, lado, v, 0);
        borde(g, muestra + 1, lado, v, 4);
        // Se invierte el segundo par para recorrer el contorno en orden.
        float x = v[4];
        float y = v[5];
        v[4] = v[6];
        v[5] = v[7];
        v[6] = x;
        v[7] = y;
        return v;
    }

    /** Los dos puntos de la cinta en una muestra: el borde del asfalto y el que está {@link #ANCHO} px adentro. */
    private static void borde(Circuito.Geometria g, int muestra, int lado, float[] v, int desde) {
        int n = g.muestras();
        float[] l = g.linea();
        int i = Math.floorMod(muestra, n);
        int a = Math.floorMod(i - 2, n);
        int b = (i + 2) % n;
        float tx = l[2 * b] - l[2 * a];
        float ty = l[2 * b + 1] - l[2 * a + 1];
        float largo = (float) Math.hypot(tx, ty);
        float nx = -ty / largo * lado;
        float ny = tx / largo * lado;
        float afuera = g.anchos()[i] / 2f;
        float adentro = afuera - ANCHO;
        v[desde] = l[2 * i] + nx * afuera;
        v[desde + 1] = l[2 * i + 1] + ny * afuera;
        v[desde + 2] = l[2 * i] + nx * adentro;
        v[desde + 3] = l[2 * i + 1] + ny * adentro;
    }

    private static Franjas armar(List<float[]> cuadrados, boolean rojas) {
        float[] vertices = new float[cuadrados.size() * 8];
        short[] triangulos = new short[cuadrados.size() * 6];
        for (int c = 0; c < cuadrados.size(); c++) {
            System.arraycopy(cuadrados.get(c), 0, vertices, c * 8, 8);
            int base = c * 4;
            int t = c * 6;
            triangulos[t] = (short) base;
            triangulos[t + 1] = (short) (base + 1);
            triangulos[t + 2] = (short) (base + 2);
            triangulos[t + 3] = (short) base;
            triangulos[t + 4] = (short) (base + 2);
            triangulos[t + 5] = (short) (base + 3);
        }
        return new Franjas(vertices, triangulos, rojas);
    }
}
