package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.InfoCircuito;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

/**
 * Prueba local (sin red, dos jugadores en el mismo teclado): se elige el circuito entre los del catálogo, con los
 * botones o con las flechas izquierda y derecha, y se corre con CORRER o ENTER.
 */
public class PruebaLocal extends PantallaBase {
    private static final int JUGADORES_LOCALES = 2;
    private static final float ANCHO_COLUMNA = 620f;
    private static final float ANCHO_FLECHA = 56f;
    private static final float ANCHO_POSICION = 90f;
    private static final float ALTO_BOTON = 48f;

    private final Label gp = new Label("", skin, "subtitulo");
    private final Label datos = new Label("", skin, "suave");
    private final Label posicion = new Label("", skin, "numeros");
    private int indice;

    public PruebaLocal(Main juego) {
        this(juego, Config.CIRCUITO);
    }

    /** @param archivo circuito elegido al abrir la pantalla (nombre del archivo); si no existe, el primero del catálogo */
    public PruebaLocal(Main juego, String archivo) {
        super(juego);
        indice = Math.max(0, Catalogo.indiceDeCircuito(archivo));
        posicion.setAlignment(Align.center);

        Table circuito = tarjeta(Paleta.SECUNDARIO);
        circuito.add(new Label("CIRCUITO", skin, "etiqueta")).colspan(2).left().padBottom(8).row();
        circuito.add(gp).left().expandX().padBottom(2);
        circuito.add(selector()).right().padLeft(12f).row();
        circuito.add(datos).colspan(2).left();

        Table botones = new Table();
        botones.add(botonPrimario("CORRER", this::correr)).growX().uniformX().height(ALTO_BOTON).padRight(8);
        botones.add(boton("VOLVER", this::volver)).growX().uniformX().height(ALTO_BOTON);

        contenido.add(titular("SIN RED  ·  2 JUGADORES", "PRUEBA LOCAL")).left().padBottom(16).row();
        contenido.add(circuito).width(ANCHO_COLUMNA).padBottom(12).row();
        contenido.add(botones).width(ANCHO_COLUMNA).padBottom(14).row();
        contenido.add(new Label("J1: WASD   ·   J2: FLECHAS", skin, "etiqueta")).left();
        actualizarTextos();
    }

    private Table selector() {
        int total = Catalogo.CIRCUITOS.size();
        Table fila = new Table();
        fila.add(boton("<", () -> cambiar(Math.floorMod(indice - 1, total)))).size(ANCHO_FLECHA, ALTO_BOTON);
        fila.add(posicion).width(ANCHO_POSICION);
        fila.add(boton(">", () -> cambiar(Math.floorMod(indice + 1, total)))).size(ANCHO_FLECHA, ALTO_BOTON);
        return fila;
    }

    private void cambiar(int nuevo) {
        indice = nuevo;
        actualizarTextos();
    }

    private void actualizarTextos() {
        InfoCircuito info = Catalogo.circuito(indice);
        gp.setText(info.gp() + " - " + info.ciudad());
        datos.setText(info.nombre() + "  ·  " + String.valueOf(info.km()).replace('.', ',') + " km  ·  " + info.curvas() + " curvas");
        posicion.setText((indice + 1) + " / " + Catalogo.CIRCUITOS.size());
    }

    private void correr() {
        juego.irA(new PantallaCarrera(juego, JUGADORES_LOCALES, Catalogo.circuito(indice).archivo()));
    }

    @Override
    protected void actualizar(float delta) {
        int total = Catalogo.CIRCUITOS.size();
        if (Gdx.input.isKeyJustPressed(Keys.LEFT)) {
            cambiar(Math.floorMod(indice - 1, total));
        } else if (Gdx.input.isKeyJustPressed(Keys.RIGHT)) {
            cambiar(Math.floorMod(indice + 1, total));
        } else if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            correr();
        }
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
