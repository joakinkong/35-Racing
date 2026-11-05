package ar.edu.et35.racing.juego;

/**
 * Valores de la física del auto. Unidades: píxeles de mundo, segundos y grados. Para ajustar la
 * sensación de manejo se toca solo esta clase.
 */
public final class ParametrosAuto {
    private ParametrosAuto() {
    }

    // Velocidad
    /** Velocidad máxima hacia adelante en pista (px/s). */
    public static final float VELOCIDAD_MAX = 450f;
    /** Velocidad máxima en reversa (px/s). */
    public static final float VELOCIDAD_REVERSA = 112.5f;
    /** Cuánto acelera con el pedal estando casi parado (px/s²). Después baja con la velocidad, ver PERDIDA_ACELERACION. */
    public static final float ACELERACION = 300f;
    /**
     * Fracción de la aceleración que se pierde al llegar a VELOCIDAD_MAX (0 = aceleración constante; 0,7 = a la
     * velocidad máxima queda el 30 %). Hace que los últimos px/s cuesten: una mala salida de curva se paga en la recta.
     */
    public static final float PERDIDA_ACELERACION = 0.7f;
    /** Cuánto frena con el freno (px/s²). */
    public static final float FRENADO = 650f;
    /** Aceleración en reversa (px/s²). */
    public static final float ACELERACION_REVERSA = 150f;
    /** Por debajo de esta velocidad hacia adelante, frenar pasa a ser reversa (px/s). */
    public static final float UMBRAL_PARADO = 5f;

    // Rozamiento
    /** Desaceleración natural al soltar el acelerador (px/s²). */
    public static final float ROZAMIENTO = 112.5f;
    /** Rozamiento extra sobre el pasto cuando no se toca ningún pedal (px/s²). */
    public static final float ROZAMIENTO_PASTO = 375f;
    /** Fracción de la velocidad máxima que se permite sobre el pasto. */
    public static final float FACTOR_VELOCIDAD_PASTO = 0.45f;
    /** Desaceleración cuando se supera la velocidad máxima permitida, por ejemplo al entrar al pasto (px/s²). */
    public static final float DESACELERACION_EXCESO = 562.5f;

    // Giro
    /** Velocidad de giro máxima (grados/s): la que se tiene a baja velocidad. */
    public static final float GIRO_MAX = 135f;
    /**
     * Aceleración lateral máxima que aguantan las gomas (px/s²). Por encima de ACELERACION_LATERAL / GIRO_MAX
     * (unos 220 px/s) la velocidad de giro baja como ACELERACION_LATERAL / velocidad, o sea que el radio de giro
     * mínimo crece con el cuadrado de la velocidad: radio = velocidad² / ACELERACION_LATERAL.
     */
    public static final float ACELERACION_LATERAL = 520f;
    /**
     * Girar cuesta velocidad: por cada rad/s de giro se pierde esta fracción de la velocidad por segundo. Con 0,08, a
     * giro máximo en una horquilla (unos 2,4 rad/s) se pierde cerca del 19 % por segundo; en una curva rápida (1 rad/s),
     * cerca del 8 %.
     */
    public static final float PERDIDA_POR_GIRO = 0.08f;
    /** Velocidad (px/s) a partir de la cual el giro llega a su máximo; por debajo, gira proporcionalmente menos. */
    public static final float VELOCIDAD_GIRO_COMPLETO = 120f;
    /** Cuánta velocidad lateral se pierde por segundo: más alto = más agarre, menos derrape. */
    public static final float AGARRE = 5f;

    // Colisiones
    /** Radio del círculo de colisión del auto (px). */
    public static final float RADIO_COLISION = 10f;
    /** Fracción de velocidad que vuelve rebotando contra el muro (0 = se frena, 1 = rebota entera). */
    public static final float REBOTE = 0.35f;
    /** Fracción de velocidad que se pierde por raspar el muro en el eje paralelo. */
    public static final float PERDIDA_POR_ROCE = 0.15f;
    /** Fracción de la velocidad relativa que rebota cuando chocan dos autos (0 = se pegan, 1 = rebote perfecto). */
    public static final float RESTITUCION_CHOQUE = 0.5f;
}
