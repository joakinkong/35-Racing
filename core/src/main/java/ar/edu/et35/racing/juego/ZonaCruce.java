package ar.edu.et35.racing.juego;

import java.util.ArrayList;
import java.util.List;

/**
 * La zona alrededor de un lugar donde la pista pasa por encima de sí misma. Dice qué muestras forman el puente (lo que
 * se dibuja encima de los autos de abajo) y en qué nivel está un auto. Es solo un cálculo sobre la línea central: no
 * cambia la grilla de colisión, así que contra los muros el cruce sigue siendo una sola superficie.
 *
 * El nivel se decide una sola vez, al entrar a la zona, según el tramo más cercano, y se mantiene hasta salir: en el
 * centro los dos tramos se pisan y la posición ya no dice por cuál viene el auto. Como solo usa la posición, el cliente
 * lo calcula con lo que ya recibe en ESTADO, sin cambiar el protocolo.
 */
public final class ZonaCruce {
    /** Muestras que se miran a cada lado del cruce (480 px): sobra para salir de la zona donde los tramos se tocan. */
    private static final int MUESTRAS_BUSQUEDA = 60;
    /** Muestras que el puente sigue después de dejar de tocar el tramo de abajo. */
    private static final int MUESTRAS_EXTREMO = 3;

    private final float[] linea;
    private final float x;
    private final float y;
    private final float radio;
    private final float alcance;
    private final int[] muestrasPuente;
    private final int[] tramoAbajo;
    private final int[] tramoArriba;

    private ZonaCruce(Circuito.Cruce cruce, Circuito.Geometria g) {
        linea = g.linea();
        int n = g.muestras();
        x = (linea[2 * cruce.tunel()] + linea[2 * cruce.puente()]) / 2f;
        y = (linea[2 * cruce.tunel() + 1] + linea[2 * cruce.puente() + 1]) / 2f;
        tramoAbajo = tramo(cruce.tunel(), n);
        tramoArriba = tramo(cruce.puente(), n);

        // El puente es lo que toca el tramo de abajo, un poco más: va sin pasto, con el muro pegado al asfalto.
        int atras = muestrasQueTocan(cruce.puente(), -1, g) + MUESTRAS_EXTREMO;
        int adelante = muestrasQueTocan(cruce.puente(), 1, g) + MUESTRAS_EXTREMO;
        muestrasPuente = new int[atras + adelante + 1];
        float lejos = 0f;
        float alcanceTotal = 0f;
        for (int k = 0; k < muestrasPuente.length; k++) {
            int i = Math.floorMod(cruce.puente() + k - atras, n);
            muestrasPuente[k] = i;
            float distancia = (float) Math.hypot(linea[2 * i] - x, linea[2 * i + 1] - y);
            lejos = Math.max(lejos, distancia);
            alcanceTotal = Math.max(alcanceTotal, distancia + radioExterior(g, i));
        }
        radio = lejos;
        alcance = alcanceTotal;
    }

    /** Una zona por cada cruce de la geometría. */
    static List<ZonaCruce> de(Circuito.Geometria g) {
        List<ZonaCruce> zonas = new ArrayList<>();
        for (Circuito.Cruce cruce : g.cruces()) {
            zonas.add(new ZonaCruce(cruce, g));
        }
        return List.copyOf(zonas);
    }

    /**
     * El nivel de un auto después de moverse a (autoX, autoY), a partir del que tenía antes.
     *
     * @param actual el nivel anterior; {@link NivelCruce#FUERA} para un auto recién aparecido
     */
    public static NivelCruce nivel(List<ZonaCruce> zonas, NivelCruce actual, float autoX, float autoY) {
        for (ZonaCruce zona : zonas) {
            if (zona.contiene(autoX, autoY)) {
                return actual != NivelCruce.FUERA ? actual : zona.nivelAlEntrar(autoX, autoY);
            }
        }
        return NivelCruce.FUERA;
    }

    private boolean contiene(float autoX, float autoY) {
        float dx = autoX - x;
        float dy = autoY - y;
        return dx * dx + dy * dy <= radio * radio;
    }

    private NivelCruce nivelAlEntrar(float autoX, float autoY) {
        return distancia2AlTramo(tramoAbajo, autoX, autoY) < distancia2AlTramo(tramoArriba, autoX, autoY)
            ? NivelCruce.ABAJO : NivelCruce.ARRIBA;
    }

    private float distancia2AlTramo(int[] tramo, float autoX, float autoY) {
        float minima = Float.MAX_VALUE;
        for (int i : tramo) {
            float dx = linea[2 * i] - autoX;
            float dy = linea[2 * i + 1] - autoY;
            minima = Math.min(minima, dx * dx + dy * dy);
        }
        return minima;
    }

    private static int[] tramo(int centro, int n) {
        int[] tramo = new int[2 * MUESTRAS_BUSQUEDA + 1];
        for (int k = 0; k < tramo.length; k++) {
            tramo[k] = Math.floorMod(centro + k - MUESTRAS_BUSQUEDA, n);
        }
        return tramo;
    }

    /** Cuántas muestras seguidas del puente, en ese sentido, tocan con su asfalto y muro al tramo de abajo con su pasto y muro. */
    private int muestrasQueTocan(int puente, int sentido, Circuito.Geometria g) {
        int n = g.muestras();
        int cuenta = 0;
        while (cuenta < MUESTRAS_BUSQUEDA && tocaTramoAbajo(Math.floorMod(puente + sentido * (cuenta + 1), n), g)) {
            cuenta++;
        }
        return cuenta;
    }

    private boolean tocaTramoAbajo(int i, Circuito.Geometria g) {
        float radioPuente = g.anchos()[i] / 2f + g.muro();
        for (int j : tramoAbajo) {
            float dx = linea[2 * i] - linea[2 * j];
            float dy = linea[2 * i + 1] - linea[2 * j + 1];
            float suma = radioPuente + radioExterior(g, j);
            if (dx * dx + dy * dy < suma * suma) {
                return true;
            }
        }
        return false;
    }

    private static float radioExterior(Circuito.Geometria g, int i) {
        return g.anchos()[i] / 2f + g.escapes()[i] + g.muro();
    }

    /** Muestras de la línea central que forman el puente, en orden. No modificar. */
    public int[] muestrasPuente() {
        return muestrasPuente;
    }

    /** Si un punto cae dentro de lo que dibuja el puente, incluidos sus extremos. */
    public boolean alcanza(float puntoX, float puntoY) {
        return Math.hypot(puntoX - x, puntoY - y) <= alcance;
    }
}
