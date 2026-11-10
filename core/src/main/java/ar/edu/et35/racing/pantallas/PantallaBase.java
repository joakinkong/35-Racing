package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.FondoConLinea;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Base de todas las pantallas: escena Scene2D a 1280x720 con FitViewport y, arriba, la barra con la marca al estilo de
 * PIT WALL. Cada subclase arma su interfaz dentro de {@link #contenido}. También tiene las piezas que se repiten: el
 * título con su etiqueta de sección, las tarjetas y los botones.
 */
public abstract class PantallaBase extends ScreenAdapter {
    private static final float ALTO_BARRA = 60f;
    private static final float MARGEN_BARRA = 32f;
    private static final float PADDING_TARJETA = 18f;
    private static final float ANCHO_BARRITA = 5f;
    private static final float MARGEN_BOTON_CHICO = 6f;
    private static final float SEPARACION_BARRITAS = 2f;
    private static final float[] ALTOS_BARRITAS = {12f, 20f, 16f};

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
     * Con marco = true la pantalla tiene el fondo y la barra superior, y se arma dentro de {@link #contenido}. Con
     * marco = false la raíz queda vacía y transparente (por ejemplo, para dibujar el mundo de la carrera debajo de la
     * interfaz).
     */
    protected PantallaBase(Main juego, boolean marco) {
        this.juego = juego;
        this.recursos = juego.recursos();
        this.skin = recursos.skin;
        // El Stage comparte el SpriteBatch de Recursos, así no crea (ni destruye) uno propio por pantalla.
        this.escena = new Stage(new FitViewport(Config.ANCHO_INTERFAZ, Config.ALTO_INTERFAZ), recursos.batch);

        raiz.setFillParent(true);
        if (marco) {
            raiz.setBackground(recursos.color(Paleta.FONDO));
            raiz.add(barraSuperior()).growX().height(ALTO_BARRA).row();
            raiz.add(contenido).expand();
        }
        escena.addActor(raiz);
    }

    /** Se llama al principio de cada cuadro, antes de dibujar. Sirve para procesar lo que llegó por la red. */
    protected void actualizar(float delta) {
    }

    /** Se llama en cada cuadro después de limpiar la pantalla y antes de dibujar la interfaz. */
    protected void dibujarFondo(float delta) {
    }

    /** Pantalla a la que vuelve ESC. Por defecto no hace nada. */
    protected void volver() {
    }

    protected TextButton boton(String texto, Runnable accion) {
        return boton(texto, "default", accion);
    }

    /** El botón de la acción principal de la pantalla, en rojo. */
    protected TextButton botonPrimario(String texto, Runnable accion) {
        return boton(texto, "primario", accion);
    }

    /** Un botón con la letra chica, para las listas: si el texto no entra en ese ancho, lleva puntos suspensivos. */
    protected TextButton botonChico(String texto, float ancho, Runnable accion) {
        TextButton boton = boton(texto, "chico", accion);
        boton.getLabel().setEllipsis(true);
        boton.getLabelCell().width(ancho - 2 * MARGEN_BOTON_CHICO);
        return boton;
    }

    private TextButton boton(String texto, String estilo, Runnable accion) {
        TextButton boton = new TextButton(texto, skin, estilo);
        boton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent evento, Actor actor) {
                accion.run();
            }
        });
        return boton;
    }

    /** Título de pantalla: arriba la etiqueta de sección en turquesa, abajo el título grande. */
    /**
     * La tabla tal cual si entra en {@code altoMaximo}; si no (por ejemplo, con los 22 autos), dentro de un panel con
     * barra de desplazamiento de ese alto, para que los botones de abajo nunca queden fuera de la pantalla.
     */
    protected Table conDesplazamiento(Table tabla, float altoMaximo) {
        Table envoltorio = new Table();
        if (tabla.getPrefHeight() <= altoMaximo) {
            envoltorio.add(tabla).growX().top();
            return envoltorio;
        }
        ScrollPane desplazable = new ScrollPane(tabla, skin);
        desplazable.setScrollingDisabled(true, false);
        desplazable.setFadeScrollBars(false);
        desplazable.setOverscroll(false, false);
        envoltorio.add(desplazable).grow().height(altoMaximo).top();
        return envoltorio;
    }

    protected Table titular(String seccion, String titulo) {
        Table bloque = new Table();
        bloque.add(new Label(seccion, skin, "seccion")).left().padBottom(2).row();
        bloque.add(new Label(titulo, skin, "encabezado")).left();
        return bloque;
    }

    protected Table tarjeta() {
        return prepararTarjeta(recursos.color(Paleta.SUPERFICIE_BAJA));
    }

    /** Una tarjeta con la línea de color arriba. */
    protected Table tarjeta(Color acento) {
        return prepararTarjeta(recursos.conLinea(Paleta.SUPERFICIE_BAJA, acento, FondoConLinea.Lado.ARRIBA));
    }

    private Table prepararTarjeta(Drawable fondo) {
        Table tarjeta = new Table();
        tarjeta.setBackground(fondo);
        tarjeta.pad(PADDING_TARJETA);
        tarjeta.top().left();
        return tarjeta;
    }

    /** Un texto largo que se parte en varias líneas dentro del ancho de su celda. */
    protected Label textoLargo(String texto, String estilo) {
        Label label = new Label(texto, skin, estilo);
        label.setWrap(true);
        return label;
    }

    private Table barraSuperior() {
        Table barra = new Table();
        barra.setBackground(recursos.conLinea(Paleta.FONDO, Paleta.LINEA_TENUE, FondoConLinea.Lado.ABAJO));
        barra.pad(0f, MARGEN_BARRA, 0f, MARGEN_BARRA);
        Table marca = new Table();
        for (float alto : ALTOS_BARRITAS) {
            marca.add(new Image(recursos.color(Paleta.ROJO))).width(ANCHO_BARRITA).height(alto).bottom()
                .padRight(SEPARACION_BARRITAS);
        }
        barra.add(marca).padRight(10f);
        barra.add(new Label("35 RACING", skin, "marca")).left().expandX();
        return barra;
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
        actualizar(delta);
        ScreenUtils.clear(Paleta.FONDO);
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
