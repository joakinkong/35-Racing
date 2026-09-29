package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Base de todas las pantallas: escena Scene2D a 640x360 con FitViewport, marco con bandera de cuadros
 * arriba y abajo, y ESC para volver. Cada subclase arma su interfaz dentro de {@link #contenido}.
 */
public abstract class PantallaBase extends ScreenAdapter {
    private static final float ALTO_FRANJA = 16f;

    protected final Main juego;
    protected final Recursos recursos;
    protected final Skin skin;
    protected final Stage escena;
    protected final Table raiz = new Table();
    protected final Table contenido = new Table();

    protected PantallaBase(Main juego) {
        this(juego, true);
    }

    /**
     * Con marco = true la pantalla tiene fondo carbono y la bandera de cuadros arriba y abajo, y se arma
     * dentro de {@link #contenido}. Con marco = false la raíz queda vacía y transparente (por ejemplo, para
     * dibujar el mundo de la carrera debajo de la interfaz).
     */
    protected PantallaBase(Main juego, boolean marco) {
        this.juego = juego;
        this.recursos = juego.recursos();
        this.skin = recursos.skin;
        // El Stage comparte el SpriteBatch de Recursos, así no crea (ni destruye) uno propio por pantalla.
        this.escena = new Stage(new FitViewport(Config.ANCHO_VIRTUAL, Config.ALTO_VIRTUAL), recursos.batch);

        raiz.setFillParent(true);
        if (marco) {
            raiz.setBackground(recursos.color(Paleta.CARBONO));
            raiz.add(franja()).growX().height(ALTO_FRANJA).row();
            raiz.add(contenido).expand().row();
            raiz.add(franja()).growX().height(ALTO_FRANJA);
        }
        escena.addActor(raiz);
    }

    /** Se llama en cada cuadro después de limpiar la pantalla y antes de dibujar la interfaz. */
    protected void dibujarFondo(float delta) {
    }

    /** Pantalla a la que vuelve ESC. Por defecto no hace nada. */
    protected void volver() {
    }

    protected TextButton boton(String texto, Runnable accion) {
        TextButton boton = new TextButton(texto, skin);
        boton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent evento, Actor actor) {
                accion.run();
            }
        });
        return boton;
    }

    private Image franja() {
        return new Image(recursos.cuadros());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(escena);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            volver();
        }
        ScreenUtils.clear(Paleta.CARBONO);
        dibujarFondo(delta);
        escena.getViewport().apply();
        escena.act(delta);
        escena.draw();
    }

    @Override
    public void resize(int ancho, int alto) {
        escena.getViewport().update(ancho, alto, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        escena.dispose();
    }
}
