package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Las reglas de un torneo: el calendario, qué carrera toca y la tabla de puntos. No sabe nada de red ni de la pista: el
 * servidor le pasa los resultados de cada carrera.
 *
 * <p>Los puntos se cuentan desde el último: es la tabla de la F1 al revés (ver {@link Config#PUNTOS_DESDE_EL_ULTIMO}).
 * Con 4 autos, por ejemplo, se reparten 6, 4, 2 y 1.
 */
public final class Torneo {
    private static final class Fila {
        final int id;
        final String nombre;
        int puntos;
        int puntosCarrera;
        /** Cuántas veces llegó en cada posición: en [0], las victorias; en [1], los segundos puestos; y así. */
        final int[] llegadas = new int[Config.MAX_JUGADORES];

        Fila(int id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    private final List<Integer> calendario;
    private final Map<Integer, Fila> filas = new LinkedHashMap<>();
    private int indice;

    /** @param calendario índices de {@link Catalogo#CIRCUITOS} en el orden en que se corren; pueden repetirse */
    public Torneo(List<Integer> calendario) {
        if (calendario.isEmpty()) {
            throw new IllegalArgumentException("Un torneo necesita al menos una carrera");
        }
        this.calendario = List.copyOf(calendario);
    }

    /** Suma un auto a la tabla, con 0 puntos. Si dos quedan iguales en todo, va primero el que se inscribió antes. */
    public void inscribir(int id, String nombre) {
        filas.putIfAbsent(id, new Fila(id, nombre));
    }

    /** Lo que suma quien termina en esa posición (desde 1) en una carrera que largaron esos autos. */
    public static int puntosPorPosicion(int posicion, int autos) {
        return Config.PUNTOS_DESDE_EL_ULTIMO[autos - posicion];
    }

    /**
     * Anota una carrera. Los puntos dependen de cuántos autos largaron, que son todos los de los resultados.
     *
     * @param abandonaron ids de los que se fueron sin terminar: conservan lo que ya tenían, pero esta carrera no les suma
     */
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

    /**
     * De mayor a menor por puntos. Un empate se define por las llegadas: más victorias, si no más segundos puestos, si no más
     * terceros, y así. El ordenamiento es estable: los iguales en todo quedan en el orden en que se inscribieron.
     */
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

    /** El número de la carrera actual, desde 1. */
    public int numeroCarrera() {
        return indice + 1;
    }

    public int totalCarreras() {
        return calendario.size();
    }

    public boolean hayMasCarreras() {
        return indice + 1 < calendario.size();
    }

    /** El circuito de la próxima carrera, o -1 si esta es la última. */
    public int circuitoSiguiente() {
        return hayMasCarreras() ? calendario.get(indice + 1) : -1;
    }

    public void pasarALaSiguiente() {
        if (hayMasCarreras()) {
            indice++;
        }
    }
}
