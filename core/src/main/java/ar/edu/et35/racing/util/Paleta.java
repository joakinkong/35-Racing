package ar.edu.et35.racing.util;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Escuderia;
import com.badlogic.gdx.graphics.Color;
import java.util.function.ToIntFunction;

public final class Paleta {
    private Paleta() {
    }

    public static final Color FONDO = Color.valueOf("121314FF");
    public static final Color SUPERFICIE_MINIMA = Color.valueOf("0D0E0FFF");
    public static final Color SUPERFICIE_BAJA = Color.valueOf("1B1C1DFF");
    public static final Color SUPERFICIE_ALTA = Color.valueOf("292A2BFF");
    public static final Color SUPERFICIE_MAXIMA = Color.valueOf("343536FF");
    public static final Color LINEA_TENUE = new Color(1f, 1f, 1f, 0.05f);
    public static final Color FONDO_HUD = new Color(0.07f, 0.075f, 0.08f, 0.88f);
    public static final Color FONDO_HUD_PROPIO = new Color(0.2f, 0.21f, 0.21f, 0.94f);

    public static final Color TEXTO = Color.valueOf("E3E2E3FF");
    public static final Color TEXTO_SUAVE = Color.valueOf("A1A1AAFF");
    public static final Color TEXTO_APAGADO = Color.valueOf("71717AFF");

    public static final Color PRIMARIO = Color.valueOf("FFB4A7FF");
    public static final Color SECUNDARIO = Color.valueOf("47EFDAFF");
    public static final Color ROJO = Color.valueOf("DF1300FF");
    public static final Color ROJO_CLARO = Color.valueOf("F5361FFF");
    public static final Color ROJO_OSCURO = Color.valueOf("A30E00FF");
    public static final Color DORADO = Color.valueOf("FFD700FF");
    public static final Color BLANCO = Color.WHITE;

    public static final Color PISTA_FONDO = FONDO;
    public static final Color PISTA_MURO = ROJO;
    public static final Color PISTA_PASTO = Color.valueOf("24592FFF");
    public static final Color PISTA_ASFALTO = SUPERFICIE_MAXIMA;
    public static final Color PISTA_META = TEXTO;
    public static final Color PISTA_PIANO_ROJO = ROJO;
    public static final Color PISTA_PIANO_BLANCO = TEXTO;

    private static final Color[] COLORES_ESCUDERIAS = colores(Escuderia::color);
    private static final Color[] DETALLES_ESCUDERIAS = colores(Escuderia::colorDetalle);
    private static final Color[] AUTOS_ESCUDERIAS = colores(Escuderia::colorAuto);

    private static Color[] colores(ToIntFunction<Escuderia> campo) {
        Color[] colores = new Color[Catalogo.ESCUDERIAS.size()];
        for (int i = 0; i < colores.length; i++) {
            Escuderia e = Catalogo.ESCUDERIAS.get(i);
            colores[i] = new Color(campo.applyAsInt(e) << 8 | 0xFF);
        }
        return colores;
    }

    public static Color colorDePiloto(int piloto) {
        return COLORES_ESCUDERIAS[Catalogo.piloto(piloto).escuderia()];
    }

    public static Color autoDePiloto(int piloto) {
        return AUTOS_ESCUDERIAS[Catalogo.piloto(piloto).escuderia()];
    }

    public static Color detalleDePiloto(int piloto) {
        return DETALLES_ESCUDERIAS[Catalogo.piloto(piloto).escuderia()];
    }

    public static Color colorDeEscuderia(int escuderia) {
        return COLORES_ESCUDERIAS[escuderia];
    }

    public static Color textoSobre(Color fondo) {
        float luminosidad = 0.299f * fondo.r + 0.587f * fondo.g + 0.114f * fondo.b;
        return luminosidad > 0.6f ? FONDO : BLANCO;
    }
}
