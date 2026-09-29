package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La carrera simulada en esta misma PC (prueba local, sin red): paso fijo de 1 / TICKS_POR_SEGUNDO con un
 * acumulador, y la foto interpolada entre el último tick y el siguiente para dibujar sin saltos.
 */
public class SimulacionLocal implements FuenteCarrera {
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    /** Tope de tiempo por cuadro, para que un tirón (por ejemplo al mover la ventana) no dispare cientos de ticks. */
    private static final float MAX_TIEMPO_CUADRO = 0.25f;

    private final Carrera carrera;
    private final List<Integer> ids = new ArrayList<>();
    private float acumulador;
    private float alfa;

    /** Carrera con esa cantidad de jugadores en el mismo teclado, llamados "Jugador 1", "Jugador 2"... */
    public SimulacionLocal(Circuito circuito, int jugadores) {
        carrera = new Carrera(circuito, Config.VUELTAS);
        for (int i = 0; i < jugadores; i++) {
            carrera.agregarAuto(i, "Jugador " + (i + 1));
            ids.add(i);
        }
    }

    @Override
    public void avanzar(float delta, Map<Integer, EntradaAuto> entradasLocales) {
        acumulador += Math.min(delta, MAX_TIEMPO_CUADRO);
        while (acumulador >= PASO) {
            carrera.actualizar(entradasLocales, PASO);
            acumulador -= PASO;
        }
        alfa = acumulador / PASO;
    }

    @Override
    public FotoCarrera foto() {
        return carrera.foto(alfa);
    }

    @Override
    public List<Integer> idsLocales() {
        return ids;
    }

    @Override
    public String nombre(int id) {
        Participante p = carrera.participante(id);
        return p != null ? p.nombre() : "?";
    }

    @Override
    public int color(int id) {
        return id;
    }

    @Override
    public int lugarDeLargada(int id) {
        return id; // se agregaron en orden de id
    }

    @Override
    public int vueltasTotales() {
        return carrera.vueltasTotales();
    }

    @Override
    public List<ResultadoJugador> resultados() {
        return carrera.estado() == EstadoCarrera.TERMINADA ? carrera.resultados() : null;
    }
}
