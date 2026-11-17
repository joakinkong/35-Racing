package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Torneo {
    private static final class Fila {
        final int id;
        final String nombre;
        int puntos;
        int puntosCarrera;
        final int[] llegadas = new int[Config.MAX_JUGADORES];

        Fila(int id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    private final List<Integer> calendario;
    private final Map<Integer, Fila> filas = new LinkedHashMap<>();
    private int indice;

    public Torneo(List<Integer> calendario) {
        if (calendario.isEmpty()) {
            throw new IllegalArgumentException("Un torneo necesita al menos una carrera");
        }
        this.calendario = List.copyOf(calendario);
    }

    public void inscribir(int id, String nombre) {
        filas.putIfAbsent(id, new Fila(id, nombre));
    }

    public static int puntosPorPosicion(int posicion, int autos) {
        if (autos <= Config.MAX_AUTOS_TABLA_INVERSA) {
            return Config.PUNTOS_DESDE_EL_ULTIMO[autos - posicion];
        }
        return posicion <= Config.PUNTOS_F1.length ? Config.PUNTOS_F1[posicion - 1] : 0;
    }

    public void sumarCarrera(List<ResultadoJugador> resultados, Set<Integer> abandonaron) {
        for (Fila fila : filas.values()) {
            fila.puntosCarrera = 0;
        }
        for (ResultadoJugador resultado : resultados) {
            Fila fila = filas.get(resultado.id());
            if (fila == null || abandonaron.contains(resultado.id())) {
                continue;
            }
            fila.puntosCarrera = puntosPorPosicion(resultado.posicion(), resultados.size());
            fila.puntos += fila.puntosCarrera;
            fila.llegadas[resultado.posicion() - 1]++;
        }
    }

    public List<FilaTorneo> tabla() {
        List<Fila> orden = new ArrayList<>(filas.values());
        orden.sort(Comparator.<Fila>comparingInt(f -> -f.puntos).thenComparing((a, b) -> Arrays.compare(b.llegadas, a.llegadas)));
        List<FilaTorneo> tabla = new ArrayList<>();
        for (Fila f : orden) {
            tabla.add(new FilaTorneo(f.id, f.nombre, f.puntos, f.puntosCarrera, f.llegadas[0]));
        }
        return tabla;
    }

    public int circuitoActual() {
        return calendario.get(indice);
    }

    public int numeroCarrera() {
        return indice + 1;
    }

    public int totalCarreras() {
        return calendario.size();
    }

    public boolean hayMasCarreras() {
        return indice + 1 < calendario.size();
    }

    public int circuitoSiguiente() {
        return hayMasCarreras() ? calendario.get(indice + 1) : -1;
    }

    public void pasarALaSiguiente() {
        if (hayMasCarreras()) {
            indice++;
        }
    }
}
