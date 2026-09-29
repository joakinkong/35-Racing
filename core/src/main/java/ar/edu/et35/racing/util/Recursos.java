package ar.edu.et35.racing.util;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.badlogic.gdx.utils.Disposable;

/**
 * Punto único donde se crean y liberan los recursos compartidos: el AssetManager, el SpriteBatch,
 * las fuentes y el Skin de la interfaz. El Skin se arma por código, sin archivos externos.
 */
public class Recursos implements Disposable {
    private static final int LADO_CUADRO = 8;
    private static final float ESCALA_TITULO = 3f;

    public final AssetManager assets = new AssetManager();
    public final SpriteBatch batch = new SpriteBatch();
    public final Skin skin = new Skin();

    public Recursos() {
        assets.setLoader(TiledMap.class, new TmxMapLoader());
        crearTexturas();
        crearFuentes();
        crearEstilos();
    }

    /** Carga un mapa de Tiled (y su tileset) y espera a que termine. Hay que liberarlo con {@link #liberar}. */
    public TiledMap cargarMapa(String ruta) {
        assets.load(ruta, TiledMap.class);
        assets.finishLoadingAsset(ruta);
        return assets.get(ruta, TiledMap.class);
    }

    public void liberar(String ruta) {
        assets.unload(ruta);
    }

    /** Drawable de un color liso. */
    public Drawable color(Color color) {
        return skin.newDrawable("blanco", color);
    }

    /** Franja de cuadros blanco y negro, como la bandera de llegada. */
    public TiledDrawable cuadros() {
        return new TiledDrawable(skin.getRegion("cuadros"));
    }

    /** Franja de pianos rojo y blanco, como las curbas de un circuito. */
    public TiledDrawable piano() {
        return new TiledDrawable(skin.getRegion("piano"));
    }

    private void crearTexturas() {
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        skin.add("blanco", nueva(pixel));

        int lado = LADO_CUADRO;
        Pixmap cuadros = new Pixmap(lado * 2, lado * 2, Pixmap.Format.RGBA8888);
        cuadros.setColor(Color.WHITE);
        cuadros.fill();
        cuadros.setColor(Color.BLACK);
        cuadros.fillRectangle(lado, 0, lado, lado);
        cuadros.fillRectangle(0, lado, lado, lado);
        skin.add("cuadros", nueva(cuadros));

        Pixmap piano = new Pixmap(lado * 2, lado, Pixmap.Format.RGBA8888);
        piano.setColor(Paleta.ROJO);
        piano.fillRectangle(0, 0, lado, lado);
        piano.setColor(Color.WHITE);
        piano.fillRectangle(lado, 0, lado, lado);
        skin.add("piano", nueva(piano));
    }

    /** Convierte el Pixmap en textura (con filtro Nearest, para pixel art) y libera el Pixmap. */
    private Texture nueva(Pixmap pixmap) {
        Texture textura = new Texture(pixmap);
        pixmap.dispose();
        textura.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return textura;
    }

    private void crearFuentes() {
        BitmapFont normal = new BitmapFont();
        BitmapFont titulo = new BitmapFont();
        titulo.getData().setScale(ESCALA_TITULO);
        for (BitmapFont fuente : new BitmapFont[] {normal, titulo}) {
            for (TextureRegion region : fuente.getRegions()) {
                region.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            }
        }
        skin.add("normal", normal, BitmapFont.class);
        skin.add("titulo", titulo, BitmapFont.class);
    }

    private void crearEstilos() {
        BitmapFont normal = skin.getFont("normal");
        BitmapFont titulo = skin.getFont("titulo");

        skin.add("default", new LabelStyle(normal, Paleta.BLANCO));
        skin.add("titulo", new LabelStyle(titulo, Paleta.BLANCO));
        skin.add("gris", new LabelStyle(normal, Paleta.GRIS_CLARO));
        skin.add("rojo", new LabelStyle(normal, Paleta.ROJO));
        skin.add("verde", new LabelStyle(normal, Paleta.VERDE));

        TextButtonStyle boton = new TextButtonStyle(color(Paleta.GRIS), color(Paleta.ROJO_OSCURO), null, normal);
        boton.over = color(Paleta.ROJO);
        boton.fontColor = Paleta.BLANCO;
        boton.disabled = color(Paleta.GRIS_OSCURO);
        boton.disabledFontColor = Paleta.GRIS_CLARO;
        skin.add("default", boton);

        Drawable cursor = color(Paleta.BLANCO);
        Drawable seleccion = color(Paleta.ROJO_OSCURO);
        Drawable fondo = color(Paleta.GRIS_OSCURO);
        if (fondo instanceof BaseDrawable) {
            BaseDrawable base = (BaseDrawable) fondo;
            base.setLeftWidth(6);
            base.setRightWidth(6);
        }
        TextFieldStyle campo = new TextFieldStyle(normal, Paleta.BLANCO, cursor, seleccion, fondo);
        campo.messageFont = normal;
        campo.messageFontColor = Paleta.GRIS_CLARO;
        skin.add("default", campo);
    }

    @Override
    public void dispose() {
        // El Skin libera las texturas y fuentes que se le agregaron.
        skin.dispose();
        batch.dispose();
        assets.dispose();
    }
}
