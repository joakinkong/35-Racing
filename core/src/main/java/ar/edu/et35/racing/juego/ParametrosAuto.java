package ar.edu.et35.racing.juego;

public final class ParametrosAuto {
    private ParametrosAuto() {
    }

    public static final float VELOCIDAD_MAX = 450f;
    public static final float VELOCIDAD_REVERSA = 112.5f;
    public static final float ACELERACION = 300f;
    public static final float PERDIDA_ACELERACION = 0.7f;
    public static final float FRENADO = 650f;
    public static final float ACELERACION_REVERSA = 150f;
    public static final float UMBRAL_PARADO = 5f;

    public static final float ROZAMIENTO = 112.5f;
    public static final float ROZAMIENTO_PASTO = 375f;
    public static final float FACTOR_VELOCIDAD_PASTO = 0.45f;
    public static final float DESACELERACION_EXCESO = 562.5f;

    public static final float GIRO_MAX = 135f;
    public static final float ACELERACION_LATERAL = 520f;
    public static final float PERDIDA_POR_GIRO = 0.08f;
    public static final float VELOCIDAD_GIRO_COMPLETO = 120f;
    public static final float AGARRE = 5f;

    public static final float RADIO_COLISION = 10f;
    public static final float REBOTE = 0.35f;
    public static final float PERDIDA_POR_ROCE = 0.15f;
    public static final float RESTITUCION_CHOQUE = 0.5f;
}
