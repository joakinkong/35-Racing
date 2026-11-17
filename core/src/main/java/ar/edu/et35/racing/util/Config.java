package ar.edu.et35.racing.util;

public final class Config {
    private Config() {
    }

    public static final int MAX_JUGADORES = 22;
    public static final int MAX_PERSONAS = 8;
    public static final int VUELTAS = 3;
    public static final int MIN_VUELTAS = 1;
    public static final int MAX_VUELTAS = 10;
    public static final float TIEMPO_CIERRE = 30f;
    public static final float TIEMPO_CIERRE_CLASIFICACION = 60f;
    public static final int VUELTAS_CLASIFICACION = 1;
    public static final int SEGUNDOS_GRILLA = 10;
    public static final int SEGUNDOS_CUENTA_REGRESIVA = 3;
    public static final int[] PUNTOS_DESDE_EL_ULTIMO = {1, 2, 4, 6, 8, 10, 12, 15};
    public static final int MAX_AUTOS_TABLA_INVERSA = PUNTOS_DESDE_EL_ULTIMO.length;
    public static final int[] PUNTOS_F1 = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
    public static final int MAX_CARRERAS_TORNEO = 32;
    public static final float SEGUNDOS_CARTEL_YA = 1f;
    public static final float SEGUNDOS_FIN_A_RESULTADOS = 2.5f;

    public static final int TICKS_POR_SEGUNDO = 60;

    public static final int PUERTO_TCP = 7777;
    public static final int PUERTO_UDP = 7778;
    public static final boolean DEBUG_RED = true;
    public static final int VERSION_PROTOCOLO = 9;
    public static final int MIN_JUGADORES = 2;
    public static final int LARGO_MAXIMO_NOMBRE = 12;
    public static final int INTERVALO_PING_MS = 2000;
    public static final int TIMEOUT_TCP_MS = 6000;
    public static final float TIMEOUT_RED = 5f;
    public static final float AVISO_RED_INESTABLE = 1f;
    public static final int TIMEOUT_CARGA_CIRCUITO_S = 20;
    public static final int TIMEOUT_CONEXION_MS = 3000;
    public static final String CIRCUITO = "monza";
    public static final float CELDA_COLISION = 4f;
    public static final int ENVIOS_ENTRADA_POR_SEGUNDO = 60;
    public static final int ENVIOS_ESTADO_POR_SEGUNDO = 30;
    public static final float RETRASO_INTERPOLACION = 0.1f;
    public static final int TIMEOUT_ENTRADA_UDP_MS = 1000;
    public static final float PERDIDA_UDP_SIMULADA = 0f;

    public static final int ANCHO_MUNDO = 640;
    public static final int ALTO_MUNDO = 360;
    public static final int ANCHO_INTERFAZ = 1280;
    public static final int ALTO_INTERFAZ = 720;
    public static final int VENTANA_ANCHO = 1280;
    public static final int VENTANA_ALTO = 720;
    public static final int MUESTRAS_ANTIALIASING = 4;
    public static final float ZOOM_CAMARA = 1.25f;
    
    public static final float ESCALA_AUTO = 1f;
    
    public static final float AUREOLA_RADIO = 11f;
    public static final float AUREOLA_GROSOR = 2.5f;
    public static final float AUREOLA_LATIDO = 1f;
    public static final float AUREOLA_FRECUENCIA = 1.2f;
    public static final float AUREOLA_OPACIDAD = 0.55f;
    public static final float AUREOLA_OPACIDAD_HALO = 0.25f;
    public static final int AUREOLA_LADOS = 48;
}
