package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import java.util.List;

public record FotoCarrera(EstadoCarrera estado, float reloj, float cierreRestante, List<FotoAuto> autos) {

    public FotoCarrera {
        autos = List.copyOf(autos);
    }

    public FotoAuto auto(int id) {
        for (FotoAuto a : autos) {
            if (a.id() == id) {
                return a;
            }
        }
        return null;
    }

    public int segundosCuentaRegresiva() {
        return Math.max(0, (int) Math.ceil(-reloj));
    }

    public boolean mostrarYa() {
        return estado == EstadoCarrera.EN_CURSO && reloj < Config.SEGUNDOS_CARTEL_YA;
    }

    public boolean cierreActivo() {
        return !Float.isNaN(cierreRestante);
    }

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
