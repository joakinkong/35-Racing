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
    public static final float VELOCIDAD_MAX = 360f;
    /** Velocidad máxima en reversa (px/s). */
    public static final float VELOCIDAD_REVERSA = 90f;
    /** Cuánto acelera con el pedal (px/s²). */
    public static final float ACELERACION = 220f;
    /** Cuánto frena con el freno (px/s²). */
    public static final float FRENADO = 520f;
    /** Aceleración en reversa (px/s²). */
    public static final float ACELERACION_REVERSA = 120f;
    /** Por debajo de esta velocidad hacia adelante, frenar pasa a ser reversa (px/s). */
    public static final float UMBRAL_PARADO = 5f;

    // Rozamiento
    /** Desaceleración natural al soltar el acelerador (px/s²). */
    public static final float ROZAMIENTO = 90f;
    /** Rozamiento extra sobre el pasto cuando no se toca ningún pedal (px/s²). */
    public static final float ROZAMIENTO_PASTO = 300f;
    /** Fracción de la velocidad máxima que se permite sobre el pasto. */
    public static final float FACTOR_VELOCIDAD_PASTO = 0.45f;
    /** Desaceleración cuando se supera la velocidad máxima permitida, por ejemplo al entrar al pasto (px/s²). */
    public static final float DESACELERACION_EXCESO = 450f;

    // Giro
    /** Velocidad de giro máxima (grados/s). */
    public static final float GIRO_MAX = 135f;
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
