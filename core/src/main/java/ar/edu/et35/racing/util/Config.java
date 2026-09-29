package ar.edu.et35.racing.util;

/** Constantes globales del juego. Nada de números mágicos fuera de esta clase. */
public final class Config {
    private Config() {
    }

    // Reglas de la partida
    public static final int MAX_JUGADORES = 5;
    public static final int VUELTAS = 3;
    /** Segundos que tiene el resto para terminar después de que el primero completa la carrera. */
    public static final float TIEMPO_CIERRE = 30f;
    public static final int SEGUNDOS_CUENTA_REGRESIVA = 3;
    /** Cuánto se muestra el cartel "¡YA!" después de largar. */
    public static final float SEGUNDOS_CARTEL_YA = 1f;
    /** Pausa con la carrera terminada antes de pasar a Resultados. */
    public static final float SEGUNDOS_FIN_A_RESULTADOS = 2.5f;

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
