package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

/** Pantalla de carrera. Por ahora es un fondo de asfalto con pianos y un texto. */
public class Carrera extends PantallaBase {
    private static final float ALTO_PIANO = 8f;

    public Carrera(Main juego) {
        super(juego);

        raiz.setBackground(recursos.color(Paleta.ASFALTO));
        raiz.getCell(contenido).grow();

        contenido.add(new Image(recursos.piano())).growX().height(ALTO_PIANO).row();
        contenido.add(new Label("CARRERA", skin, "titulo")).expand().row();
        contenido.add(new Label("Acá va el circuito", skin, "gris")).padBottom(8).row();
        // Botón provisorio para poder navegar a Resultados hasta que exista la lógica de carrera.
        contenido.add(boton("FINALIZAR CARRERA", () -> juego.irA(new Resultados(juego))))
            .width(180).height(28).padBottom(8).row();
        contenido.add(new Image(recursos.piano())).growX().height(ALTO_PIANO);
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
