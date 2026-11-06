package ar.edu.et35.racing.red;

import ar.edu.et35.racing.juego.FilaTorneo;
import ar.edu.et35.racing.juego.Torneo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.util.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * Foto del torneo: qué carrera es, cuál sigue y la tabla. El servidor la manda en el mensaje TORNEO al empezar cada carrera
 * y, antes de RESULTADOS, al terminarla: como TCP entrega en orden, cuando el cliente pasa a los resultados ya tiene la
 * tabla actualizada.
 *
 * <p>Formato: {@code TORNEO;carrera;total;siguiente;cantidad;id;nombre;puntos;puntosCarrera;victorias;id...} donde
 * {@code siguiente} es el índice de {@code Catalogo.CIRCUITOS} de la próxima carrera, o -1 si esta es la última.
 */
public record EstadoTorneo(int carrera, int total, int siguiente, List<FilaTorneo> tabla) {
    private static final int CAMPOS_FIJOS = 4;
    private static final int CAMPOS_POR_FILA = 5;

    public static String armar(Torneo torneo) {
        List<FilaTorneo> tabla = torneo.tabla();
        StringBuilder linea = new StringBuilder(Protocolo.armar(Protocolo.TORNEO, torneo.numeroCarrera(), torneo.totalCarreras(),
            torneo.circuitoSiguiente(), tabla.size()));
        for (FilaTorneo f : tabla) {
            linea.append(';').append(f.id()).append(';').append(f.nombre()).append(';').append(f.puntos())
                .append(';').append(f.puntosCarrera()).append(';').append(f.victorias());
        }
        return linea.toString();
    }

    public static EstadoTorneo desde(Mensaje mensaje) {
        int cantidad = Math.min(mensaje.entero(3, 0), Config.MAX_JUGADORES);
        List<FilaTorneo> tabla = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            int base = CAMPOS_FIJOS + i * CAMPOS_POR_FILA;
            if (base + CAMPOS_POR_FILA - 1 >= mensaje.campos().size()) {
                break; // el mensaje trae menos filas de las que dice
            }
            tabla.add(new FilaTorneo(mensaje.entero(base, -1), mensaje.campo(base + 1), mensaje.entero(base + 2, 0),
                mensaje.entero(base + 3, 0), mensaje.entero(base + 4, 0)));
        }
        return new EstadoTorneo(mensaje.entero(0, 1), mensaje.entero(1, 1), mensaje.entero(2, -1), List.copyOf(tabla));
    }

    public boolean esUltimaCarrera() {
        return siguiente < 0;
    }

    /** La fila de ese auto, o null si no está en el torneo. */
    public FilaTorneo fila(int id) {
        for (FilaTorneo fila : tabla) {
            if (fila.id() == id) {
                return fila;
            }
        }
        return null;
    }
}
