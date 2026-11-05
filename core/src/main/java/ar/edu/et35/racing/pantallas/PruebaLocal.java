package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.InfoCircuito;
import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * Prueba local (sin red, dos jugadores en el mismo teclado): se elige el circuito entre los del catálogo, con los
 * botones o con las flechas izquierda y derecha, y se corre con CORRER o ENTER.
 */
public class PruebaLocal extends PantallaBase {
    private static final int JUGADORES_LOCALES = 2;
    private static final float ANCHO_FLECHA = 30f;
    private static final float ANCHO_ETIQUETA = 120f;
    private static final float ANCHO_BOTON = 220f;
    private static final float ALTO_BOTON = 30f;

    private final Label gp = new Label("", skin);
    private final Label datos = new Label("", skin, "gris");
    private final Label posicion = new Label("", skin);
    private int indice;

    public PruebaLocal(Main juego) {
        this(juego, Config.CIRCUITO);
    }

    /** @param archivo circuito elegido al abrir la pantalla (nombre del archivo); si no existe, el primero del catálogo */
    public PruebaLocal(Main juego, String archivo) {
        super(juego);
        indice = Math.max(0, Catalogo.indiceDeCircuito(archivo));

        contenido.add(new Label("PRUEBA LOCAL", skin, "titulo")).padBottom(2).row();
        contenido.add(new Label("2 JUGADORES  ·  J1: WASD  ·  J2: FLECHAS", skin, "gris")).padBottom(10).row();
        contenido.add(gp).row();
        contenido.add(datos).padBottom(8).row();
        contenido.add(selector()).padBottom(10).row();
        contenido.add(boton("CORRER", this::correr)).width(ANCHO_BOTON).height(ALTO_BOTON).pad(3).row();
        contenido.add(boton("VOLVER", this::volver)).width(ANCHO_BOTON).height(ALTO_BOTON).pad(3).row();
        actualizarTextos();
    }

    private Table selector() {
        int total = Catalogo.CIRCUITOS.size();
        Table fila = new Table();
        fila.add(new Label("CIRCUITO", skin, "gris")).width(ANCHO_ETIQUETA).left();
        fila.add(boton("<", () -> cambiar(Math.floorMod(indice - 1, total)))).width(ANCHO_FLECHA).height(ALTO_BOTON);
        fila.add(posicion).width(ANCHO_ETIQUETA / 2f);
        fila.add(boton(">", () -> cambiar(Math.floorMod(indice + 1, total)))).width(ANCHO_FLECHA).height(ALTO_BOTON);
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
