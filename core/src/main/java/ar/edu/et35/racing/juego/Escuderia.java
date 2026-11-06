package ar.edu.et35.racing.juego;

/**
 * Una escudería de la grilla. Los colores son enteros RGB (0xRRGGBB) y no {@code Color} de LibGDX, porque el
 * paquete {@code juego} no usa clases gráficas.
 *
 * @param color        color que identifica al equipo en la interfaz (torre, mapa, sala). Tiene que leerse sobre fondo oscuro,
 *                     por eso puede no ser el del auto: Mercedes y Red Bull corren casi negros
 * @param colorAuto    color principal de la carrocería
 * @param colorDetalle color de acento de la carrocería
 */
public record Escuderia(String nombre, int color, int colorAuto, int colorDetalle) {
}
