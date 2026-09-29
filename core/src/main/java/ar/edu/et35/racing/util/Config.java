package ar.edu.et35.racing.util;

/** Constantes globales del juego. Nada de números mágicos fuera de esta clase. */
public final class Config {
    private Config() {
    }

    // Reglas de la partida
    public static final int MAX_JUGADORES = 5;
    public static final int VUELTAS = 3;

    // Simulación
    public static final int TICKS_POR_SEGUNDO = 60;

    // Red
    public static final int PUERTO_TCP = 7777;
    public static final int PUERTO_UDP = 7778;
    public static final boolean DEBUG_RED = true;

    // Pantalla: resolución virtual (pixel art) y ventana inicial
    public static final int ANCHO_VIRTUAL = 640;
    public static final int ALTO_VIRTUAL = 360;
    public static final int VENTANA_ANCHO = 1280;
    public static final int VENTANA_ALTO = 720;
}
