package ar.edu.et35.racing.juego;

/**
 * Tipo de terreno de una celda de la grilla de colisión de un circuito. El orden importa: {@link Circuito} guarda el
 * {@link #ordinal()} de cada celda.
 */
public enum Superficie {
    PISTA,
    PASTO,
    MURO
}
