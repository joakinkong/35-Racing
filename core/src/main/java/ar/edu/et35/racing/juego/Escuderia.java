package ar.edu.et35.racing.juego;

/**
 * Una escudería de la grilla. Los colores son enteros RGB (0xRRGGBB) y no {@code Color} de LibGDX, porque el
 * paquete {@code juego} no usa clases gráficas.
 *
 * @param color        color principal del auto
 * @param colorDetalle color del acento (franja de la trompa, ficha del lobby)
 */
public record Escuderia(String nombre, int color, int colorDetalle) {
}
