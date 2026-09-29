package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** Sala de espera: una grilla de largada con un lugar por jugador, todavía vacía. */
public class Lobby extends PantallaBase {
    private static final float ANCHO_PANEL = 300f;
    private static final float ALTO_FILA = 20f;

    public Lobby(Main juego) {
        super(juego);

        contenido.add(new Label("GRILLA DE LARGADA", skin, "titulo")).padBottom(10).row();
        contenido.add(grilla()).width(ANCHO_PANEL).padBottom(10).row();
        contenido.add(boton("INICIAR", () -> juego.irA(new PantallaCarrera(juego, 1)))).width(160).height(30).row();
        contenido.add(new Label("ESC para volver", skin, "gris")).padTop(8);
    }

    private Table grilla() {
        Table grilla = new Table();
        grilla.setBackground(recursos.color(Paleta.GRIS_OSCURO));
        grilla.pad(6);
        for (int i = 1; i <= Config.MAX_JUGADORES; i++) {
            grilla.add(new Label("P" + i, skin, "rojo")).width(36).height(ALTO_FILA).left();
            grilla.add(new Label("libre", skin, "gris")).height(ALTO_FILA).left().expandX().row();
        }
        return grilla;
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
