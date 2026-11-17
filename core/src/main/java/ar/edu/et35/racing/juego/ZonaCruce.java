package ar.edu.et35.racing.juego;

import java.util.ArrayList;
import java.util.List;

public final class ZonaCruce {
    private static final int MUESTRAS_BUSQUEDA = 60;
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

    static List<ZonaCruce> de(Circuito.Geometria g) {
        List<ZonaCruce> zonas = new ArrayList<>();
        for (Circuito.Cruce cruce : g.cruces()) {
            zonas.add(new ZonaCruce(cruce, g));
        }
        return List.copyOf(zonas);
    }

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

    public int[] muestrasPuente() {
        return muestrasPuente;
    }

    public boolean alcanza(float puntoX, float puntoY) {
        return Math.hypot(puntoX - x, puntoY - y) <= alcance;
    }
}
