package ar.edu.et35.racing.util;

/** Constantes globales del juego. Nada de números mágicos fuera de esta clase. */
public final class Config {
    private Config() {
    }

    // Reglas de la partida
    /** Máximo de autos en pista (personas y bots): los 22 pilotos de la grilla. */
    public static final int MAX_JUGADORES = 22;
    /** Máximo de personas conectadas a la vez; el resto de los autos son bots. */
    public static final int MAX_PERSONAS = 8;
    /** Vueltas por defecto; el host puede cambiarlas en el lobby entre MIN_VUELTAS y MAX_VUELTAS. */
    public static final int VUELTAS = 3;
    public static final int MIN_VUELTAS = 1;
    public static final int MAX_VUELTAS = 10;
    /** Segundos que tiene el resto para terminar después de que el primero completa la carrera. */
    public static final float TIEMPO_CIERRE = 30f;
    /** Lo mismo en la clasificación: más largo, porque ahí un error no se recupera en la vuelta siguiente. */
    public static final float TIEMPO_CIERRE_CLASIFICACION = 60f;
    public static final int VUELTAS_CLASIFICACION = 1;
    /** Cuánto se ve la grilla de largada entre la clasificación y la carrera. */
    public static final int SEGUNDOS_GRILLA = 10;
    public static final int SEGUNDOS_CUENTA_REGRESIVA = 3;
    /**
     * Puntos del torneo contados desde el último: el último suma 1, el anteúltimo 2, y así. Es la tabla de la F1 al revés,
     * hasta donde alcanzan los autos: con 8, el ganador suma 15. Se usa con hasta MAX_AUTOS_TABLA_INVERSA autos.
     */
    public static final int[] PUNTOS_DESDE_EL_ULTIMO = {1, 2, 4, 6, 8, 10, 12, 15};
    public static final int MAX_AUTOS_TABLA_INVERSA = PUNTOS_DESDE_EL_ULTIMO.length;
    /** Con más autos que MAX_AUTOS_TABLA_INVERSA se usa la tabla de la F1: los primeros diez puntúan, el resto no. */
    public static final int[] PUNTOS_F1 = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
    /** Tope del calendario de un torneo. Solo acota el mensaje LOBBY y lo que entra en la sala. */
    public static final int MAX_CARRERAS_TORNEO = 32;
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
    /** Versión del formato de los mensajes; se manda en UNIRSE y si no coincide el servidor rechaza al cliente. */
    public static final int VERSION_PROTOCOLO = 9;
    /** Autos mínimos (jugadores más bots) para que el host pueda iniciar la carrera. */
    public static final int MIN_JUGADORES = 2;
    public static final int LARGO_MAXIMO_NOMBRE = 12;
    /** Cada cuánto manda un PING el cliente (ms). */
    public static final int INTERVALO_PING_MS = 2000;
    /** Sin recibir nada durante este tiempo, una conexión TCP se da por perdida (ms). */
    public static final int TIMEOUT_TCP_MS = 6000;
    /** Sin estados UDP durante este tiempo en plena carrera, se avisa y se vuelve al menú (s). */
    public static final float TIMEOUT_RED = 5f;
    /** Sin estados UDP durante este tiempo se muestra "conexión inestable" (s). */
    public static final float AVISO_RED_INESTABLE = 1f;
    /** Máximo para que el host cargue el circuito elegido al iniciar la carrera (s). */
    public static final int TIMEOUT_CARGA_CIRCUITO_S = 20;
    /** Máximo para conectarse al host (ms). */
    public static final int TIMEOUT_CONEXION_MS = 3000;
    /** Circuito por defecto: el que está elegido al crear la partida y el de la prueba local (nombre del .json). */
    public static final String CIRCUITO = "monza";
    /**
     * Lado de las celdas de la grilla de colisión de un circuito (px). Más chico = bordes más fieles a la geometría,
     * pero más memoria y más celdas por revisar en cada choque; con 4 px el error es menor que el ancho del muro.
     */
    public static final float CELDA_COLISION = 4f;
    /** Paquetes ENTRADA que manda cada cliente por segundo. */
    public static final int ENVIOS_ENTRADA_POR_SEGUNDO = 60;
    /** Paquetes ESTADO que manda el servidor a cada cliente por segundo (uno cada 2 ticks). */
    public static final int ENVIOS_ESTADO_POR_SEGUNDO = 30;
    /** Cuánto en el pasado dibuja el cliente, para tener siempre dos estados entre los que interpolar (s). */
    public static final float RETRASO_INTERPOLACION = 0.1f;
    /** Sin entradas de un jugador durante este tiempo, el servidor usa controles neutros (ms). */
    public static final int TIMEOUT_ENTRADA_UDP_MS = 1000;
    /**
     * Fracción de paquetes UDP (de 0 a 1) que los receptores descartan a propósito, para mostrar que el juego lo
     * tolera. Con DEBUG_RED activo se puede cambiar en plena carrera con F8.
     */
    public static final float PERDIDA_UDP_SIMULADA = 0f;

    // Pantalla: lo que se ve del mundo, resolución de la interfaz y ventana inicial
    /** Tamaño base de la cámara de la carrera. No cambia con la ventana, para que todos vean lo mismo de la pista. */
    public static final int ANCHO_MUNDO = 640;
    public static final int ALTO_MUNDO = 360;
    /** Resolución virtual de los menús y el HUD: es la que da texto nítido en la ventana inicial. */
    public static final int ANCHO_INTERFAZ = 1280;
    public static final int ALTO_INTERFAZ = 720;
    public static final int VENTANA_ANCHO = 1280;
    public static final int VENTANA_ALTO = 720;
    /** Muestras de antialiasing (MSAA) de la ventana: 0 lo apaga; 4 alcanza para que los bordes diagonales salgan lisos. */
    public static final int MUESTRAS_ANTIALIASING = 4;
    /** Zoom de la cámara de cada jugador: 2 = se ve el doble de mundo que con la resolución virtual. */
    public static final float ZOOM_CAMARA = 1.25f;
}
