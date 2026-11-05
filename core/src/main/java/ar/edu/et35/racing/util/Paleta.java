package ar.edu.et35.racing.util;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Escuderia;
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

    /** Color principal de cada escudería, en el orden de {@link Catalogo#ESCUDERIAS}. */
    private static final Color[] COLORES_ESCUDERIAS = colores(false);
    /** Color de acento de cada escudería, en el mismo orden. */
    private static final Color[] DETALLES_ESCUDERIAS = colores(true);

    private static Color[] colores(boolean detalle) {
        Color[] colores = new Color[Catalogo.ESCUDERIAS.size()];
        for (int i = 0; i < colores.length; i++) {
            Escuderia e = Catalogo.ESCUDERIAS.get(i);
            colores[i] = new Color((detalle ? e.colorDetalle() : e.color()) << 8 | 0xFF);
        }
        return colores;
    }

    /** Color principal de la escudería de ese piloto. No modificar el Color devuelto: es compartido. */
    public static Color colorDePiloto(int piloto) {
        return COLORES_ESCUDERIAS[Catalogo.piloto(piloto).escuderia()];
    }

    /** Color de acento de la escudería de ese piloto. No modificar el Color devuelto: es compartido. */
    public static Color detalleDePiloto(int piloto) {
        return DETALLES_ESCUDERIAS[Catalogo.piloto(piloto).escuderia()];
    }

    /** Color de la escudería por su índice en {@link Catalogo#ESCUDERIAS}. No modificar el Color devuelto. */
    public static Color colorDeEscuderia(int escuderia) {
        return COLORES_ESCUDERIAS[escuderia];
    }

    /** Un color de texto que se lea sobre ese fondo: oscuro sobre fondos claros (Cadillac, Haas) y blanco sobre oscuros. */
    public static Color textoSobre(Color fondo) {
        float luminosidad = 0.299f * fondo.r + 0.587f * fondo.g + 0.114f * fondo.b;
        return luminosidad > 0.6f ? CARBONO : BLANCO;
    }
}
