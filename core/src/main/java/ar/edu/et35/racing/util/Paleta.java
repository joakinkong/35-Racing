package ar.edu.et35.racing.util;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Escuderia;
import com.badlogic.gdx.graphics.Color;

/** Colores de la interfaz: los mismos de PIT WALL, para que el juego pueda integrarse a esa página como minijuego. */
public final class Paleta {
    private Paleta() {
    }

    public static final Color FONDO = Color.valueOf("121314FF");
    public static final Color SUPERFICIE_MINIMA = Color.valueOf("0D0E0FFF");
    public static final Color SUPERFICIE_BAJA = Color.valueOf("1B1C1DFF");
    public static final Color SUPERFICIE_ALTA = Color.valueOf("292A2BFF");
    public static final Color SUPERFICIE_MAXIMA = Color.valueOf("343536FF");
    /** Línea casi invisible que separa bloques (blanco al 5 %). */
    public static final Color LINEA_TENUE = new Color(1f, 1f, 1f, 0.05f);
    /** Fondo de los paneles sobre la pista: se sigue viendo el circuito debajo. */
    public static final Color FONDO_HUD = new Color(0.07f, 0.075f, 0.08f, 0.88f);
    public static final Color FONDO_HUD_PROPIO = new Color(0.2f, 0.21f, 0.21f, 0.94f);

    public static final Color TEXTO = Color.valueOf("E3E2E3FF");
    public static final Color TEXTO_SUAVE = Color.valueOf("A1A1AAFF");
    public static final Color TEXTO_APAGADO = Color.valueOf("71717AFF");

    /** Salmón: lo activo, lo seleccionado y la marca. */
    public static final Color PRIMARIO = Color.valueOf("FFB4A7FF");
    /** Turquesa: las etiquetas de sección y lo que está bien. */
    public static final Color SECUNDARIO = Color.valueOf("47EFDAFF");
    public static final Color ROJO = Color.valueOf("DF1300FF");
    public static final Color ROJO_CLARO = Color.valueOf("F5361FFF");
    public static final Color ROJO_OSCURO = Color.valueOf("A30E00FF");
    /** El primer puesto. */
    public static final Color DORADO = Color.valueOf("FFD700FF");
    public static final Color BLANCO = Color.WHITE;

    // La pista: los mismos tonos oscuros, para que el mundo y la interfaz se vean como una sola cosa.
    public static final Color PISTA_FONDO = FONDO;
    public static final Color PISTA_MURO = ROJO;
    public static final Color PISTA_PASTO = Color.valueOf("24592FFF");
    public static final Color PISTA_ASFALTO = SUPERFICIE_MAXIMA;
    public static final Color PISTA_META = TEXTO;

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
        return luminosidad > 0.6f ? FONDO : BLANCO;
    }
}
