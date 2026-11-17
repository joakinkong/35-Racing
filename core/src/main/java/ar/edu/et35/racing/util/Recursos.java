package ar.edu.et35.racing.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;

public class Recursos implements Disposable {
    private static final String[] FUENTES = {
        "titulo", "marca", "encabezado", "subtitulo", "etiqueta", "numeros", "posicion", "cartel", "normal", "chico",
    };
    private static final float GROSOR_LINEA = 2f;
    private static final float MARGEN_CAMPO = 14f;
    private static final float ANCHO_CURSOR = 2f;
    private static final int LADO_PUNTO = 32;
    private static final float ANCHO_BARRA = 6f;

    public final AssetManager assets = new AssetManager();
    public final SpriteBatch batch = new SpriteBatch();
    public final Skin skin = new Skin();

    public Recursos() {
        crearTexturas();
        crearFuentes();
        crearEstilos();
    }

    public static String rutaCircuito(String nombre) {
        String seguro = nombre != null && nombre.matches("[a-z0-9_]+") ? nombre : Config.CIRCUITO;
        return "circuitos/" + seguro + ".json";
    }

    public Drawable color(Color color) {
        return skin.newDrawable("blanco", color);
    }

    public Drawable conLinea(Color fondo, Color linea, FondoConLinea.Lado lado) {
        return new FondoConLinea(skin.getRegion("blanco"), fondo, linea, lado, GROSOR_LINEA);
    }

    private void crearTexturas() {
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        Texture blanco = new Texture(pixel);
        pixel.dispose();
        skin.add("blanco", blanco);
        skin.add("punto", texturaDeCirculo());
    }

    private Texture texturaDeCirculo() {
        Pixmap circulo = new Pixmap(LADO_PUNTO, LADO_PUNTO, Pixmap.Format.RGBA8888);
        circulo.setBlending(Pixmap.Blending.None);
        float radio = LADO_PUNTO / 2f;
        for (int x = 0; x < LADO_PUNTO; x++) {
            for (int y = 0; y < LADO_PUNTO; y++) {
                float distancia = (float) Math.hypot(x + 0.5f - radio, y + 0.5f - radio);
                float alfa = Math.max(0f, Math.min(1f, radio - 0.5f - distancia + 0.5f));
                circulo.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alfa));
            }
        }
        Texture textura = new Texture(circulo);
        circulo.dispose();
        textura.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return textura;
    }

    private void crearFuentes() {
        for (String nombre : FUENTES) {
            BitmapFont fuente = new BitmapFont(Gdx.files.internal("fuentes/" + nombre + ".fnt"));
            for (TextureRegion region : fuente.getRegions()) {
                region.getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
            }
            skin.add(nombre, fuente, BitmapFont.class);
        }
    }

    private void crearEstilos() {
        estiloTexto("default", "normal", Paleta.TEXTO);
        estiloTexto("suave", "chico", Paleta.TEXTO_SUAVE);
        estiloTexto("apagado", "chico", Paleta.TEXTO_APAGADO);
        estiloTexto("alerta", "normal", Paleta.PRIMARIO);
        estiloTexto("titulo", "titulo", Paleta.PRIMARIO);
        estiloTexto("marca", "marca", Paleta.PRIMARIO);
        estiloTexto("encabezado", "encabezado", Paleta.TEXTO);
        estiloTexto("subtitulo", "subtitulo", Paleta.TEXTO);
        estiloTexto("etiqueta", "etiqueta", Paleta.TEXTO_APAGADO);
        estiloTexto("etiquetaSuave", "etiqueta", Paleta.TEXTO_SUAVE);
        estiloTexto("seccion", "etiqueta", Paleta.SECUNDARIO);
        estiloTexto("marcaChica", "etiqueta", Paleta.PRIMARIO);
        estiloTexto("numeros", "numeros", Paleta.BLANCO);
        estiloTexto("posicion", "posicion", Paleta.BLANCO);
        estiloTexto("cartel", "cartel", Paleta.BLANCO);

        BitmapFont etiqueta = skin.getFont("etiqueta");
        Drawable resaltado = conLinea(Paleta.SUPERFICIE_MAXIMA, Paleta.PRIMARIO, FondoConLinea.Lado.ABAJO);
        TextButtonStyle boton = new TextButtonStyle(color(Paleta.SUPERFICIE_ALTA), resaltado, null, etiqueta);
        boton.over = resaltado;
        boton.fontColor = Paleta.TEXTO;
        boton.overFontColor = Paleta.PRIMARIO;
        boton.downFontColor = Paleta.PRIMARIO;
        boton.disabled = color(Paleta.SUPERFICIE_BAJA);
        boton.disabledFontColor = Paleta.TEXTO_APAGADO;
        skin.add("default", boton);

        TextButtonStyle botonChico = new TextButtonStyle(boton);
        botonChico.font = skin.getFont("chico");
        skin.add("chico", botonChico);

        TextButtonStyle primario = new TextButtonStyle(color(Paleta.ROJO), color(Paleta.ROJO_OSCURO), null, etiqueta);
        primario.over = color(Paleta.ROJO_CLARO);
        primario.fontColor = Paleta.BLANCO;
        primario.disabled = color(Paleta.SUPERFICIE_BAJA);
        primario.disabledFontColor = Paleta.TEXTO_APAGADO;
        skin.add("primario", primario);

        Drawable cursor = color(Paleta.PRIMARIO);
        cursor.setMinWidth(ANCHO_CURSOR);
        Drawable seleccion = color(Paleta.ROJO_OSCURO);
        BaseDrawable fondo = (BaseDrawable) conLinea(Paleta.SUPERFICIE_MINIMA, Paleta.SUPERFICIE_MAXIMA, FondoConLinea.Lado.ABAJO);
        BaseDrawable enfocado = (BaseDrawable) conLinea(Paleta.SUPERFICIE_MINIMA, Paleta.PRIMARIO, FondoConLinea.Lado.ABAJO);
        for (BaseDrawable d : new BaseDrawable[] {fondo, enfocado}) {
            d.setLeftWidth(MARGEN_CAMPO);
            d.setRightWidth(MARGEN_CAMPO);
        }
        BitmapFont normal = skin.getFont("normal");
        TextFieldStyle campo = new TextFieldStyle(normal, Paleta.TEXTO, cursor, seleccion, fondo);
        campo.focusedBackground = enfocado;
        campo.messageFont = normal;
        campo.messageFontColor = Paleta.TEXTO_APAGADO;
        skin.add("default", campo);

        ScrollPaneStyle desplazamiento = new ScrollPaneStyle();
        Drawable carril = color(Paleta.SUPERFICIE_BAJA);
        carril.setMinWidth(ANCHO_BARRA);
        Drawable perilla = color(Paleta.TEXTO_APAGADO);
        perilla.setMinWidth(ANCHO_BARRA);
        desplazamiento.vScroll = carril;
        desplazamiento.vScrollKnob = perilla;
        skin.add("default", desplazamiento);
    }

    private void estiloTexto(String nombre, String fuente, Color color) {
        skin.add(nombre, new LabelStyle(skin.getFont(fuente), color));
    }

    @Override
    public void dispose() {
        skin.dispose();
        batch.dispose();
        assets.dispose();
    }
}
