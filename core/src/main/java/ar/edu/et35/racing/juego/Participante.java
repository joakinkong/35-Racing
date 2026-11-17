package ar.edu.et35.racing.juego;

import java.util.List;

public class Participante {
    private final int id;
    private final String nombre;
    private final Auto auto;
    private final int cantidadCheckpoints;

    private int vueltas;
    private int siguienteCheckpoint;
    private float tiempoVuelta;
    private float mejorVuelta = Float.NaN;
    private float tiempoTotal = Float.NaN;
    private boolean terminado;
    private boolean desconectado;
    private NivelCruce nivelCruce = NivelCruce.FUERA;

    Participante(int id, String nombre, Auto auto, int cantidadCheckpoints) {
        this.id = id;
        this.nombre = nombre;
        this.auto = auto;
        this.cantidadCheckpoints = cantidadCheckpoints;
        this.siguienteCheckpoint = 1 % cantidadCheckpoints;
    }

    void actualizarNivelCruce(List<ZonaCruce> zonas) {
        nivelCruce = ZonaCruce.nivel(zonas, nivelCruce, auto.x(), auto.y());
    }

    public NivelCruce nivelCruce() {
        return nivelCruce;
    }

    void sumarTiempo(float dt) {
        if (!terminado) {
            tiempoVuelta += dt;
        }
    }

    void pasarPorCheckpoint(float tiempoCarrera, int vueltasTotales) {
        if (siguienteCheckpoint != 0) {
            siguienteCheckpoint = (siguienteCheckpoint + 1) % cantidadCheckpoints;
            return;
        }
        vueltas++;
        mejorVuelta = Float.isNaN(mejorVuelta) ? tiempoVuelta : Math.min(mejorVuelta, tiempoVuelta);
        tiempoVuelta = 0f;
        siguienteCheckpoint = 1 % cantidadCheckpoints;
        if (vueltas >= vueltasTotales) {
            terminado = true;
            tiempoTotal = tiempoCarrera;
        }
    }

    int progreso() {
        return vueltas * cantidadCheckpoints + (siguienteCheckpoint == 0 ? cantidadCheckpoints : siguienteCheckpoint);
    }

    public int id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public Auto auto() {
        return auto;
    }

    public int vueltas() {
        return vueltas;
    }

    public int siguienteCheckpoint() {
        return siguienteCheckpoint;
    }

    public float tiempoVuelta() {
        return tiempoVuelta;
    }

    public float mejorVuelta() {
        return mejorVuelta;
    }

    public float tiempoTotal() {
        return tiempoTotal;
    }

    public boolean terminado() {
        return terminado;
    }

    void marcarDesconectado() {
        desconectado = true;
    }

    public boolean desconectado() {
        return desconectado;
    }
}
