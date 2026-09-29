package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.List;

/**
 * La carrera en un instante: lo que dibuja la pantalla, venga de una simulación local o del servidor. Es la
 * cabecera y los autos del paquete ESTADO (docs/PROTOCOLO.md, sección 4).
 *
 * @param reloj          segundos de carrera; negativo durante la cuenta regresiva
 * @param cierreRestante segundos que le quedan al resto para terminar, o NaN si todavía nadie terminó
 * @param autos          ordenados por posición (el puntero primero)
 */
public record FotoCarrera(EstadoCarrera estado, float reloj, float cierreRestante, List<FotoAuto> autos) {

    public FotoCarrera {
        autos = List.copyOf(autos);
    }

    /** El auto con ese id, o null si no está. */
    public FotoAuto auto(int id) {
        for (FotoAuto a : autos) {
            if (a.id() == id) {
                return a;
            }
        }
        return null;
    }

    /** Número que se muestra en la cuenta regresiva (3, 2, 1). */
    public int segundosCuentaRegresiva() {
        return Math.max(0, (int) Math.ceil(-reloj));
    }

    /** Verdadero durante el instante en que se muestra el cartel "¡YA!". */
    public boolean mostrarYa() {
        return estado == EstadoCarrera.EN_CURSO && reloj < Config.SEGUNDOS_CARTEL_YA;
    }

    public boolean cierreActivo() {
        return !Float.isNaN(cierreRestante);
    }

    /** Autos que siguen en la pista (sin contar a los que abandonaron). */
    public int autosEnCarrera() {
        int cantidad = 0;
        for (FotoAuto a : autos) {
            if (!a.desconectado()) {
                cantidad++;
            }
        }
        return cantidad;
    }
}
