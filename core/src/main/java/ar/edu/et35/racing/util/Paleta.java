package ar.edu.et35.racing.util;

import com.badlogic.gdx.graphics.Color;

/** Colores del estilo visual (carbono, rojo de largada y blanco, como la Fórmula 1). */
public final class Paleta {
    private Paleta() {
    }

    public static final Color CARBONO = Color.valueOf("15151EFF");
    public static final Color GRIS_OSCURO = Color.valueOf("23232CFF");
    public static final Color GRIS = Color.valueOf("38383FFF");
    public static final Color GRIS_CLARO = Color.valueOf("949498FF");
    public static final Color ASFALTO = Color.valueOf("2B2B30FF");
    public static final Color ROJO = Color.valueOf("E10600FF");
    public static final Color ROJO_OSCURO = Color.valueOf("8E0400FF");
    public static final Color BLANCO = Color.WHITE;
    public static final Color VERDE = Color.valueOf("3ED26CFF");

    /** Un color por auto (según su id), para distinguir hasta MAX_JUGADORES autos. */
    public static final Color[] COLORES_AUTOS = {
        ROJO,
        Color.valueOf("3671C6FF"),
        Color.valueOf("FFC800FF"),
        Color.valueOf("00D2BEFF"),
        Color.valueOf("FF8700FF"),
    };

    /** Nombre de cada color de auto, en el mismo orden que {@link #COLORES_AUTOS}. */
    public static final String[] NOMBRES_AUTOS = {"ROJO", "AZUL", "AMARILLO", "TURQUESA", "NARANJA"};
}
