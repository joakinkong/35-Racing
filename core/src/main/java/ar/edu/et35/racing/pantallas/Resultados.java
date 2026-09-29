package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** Tabla de resultados. Los datos son de ejemplo hasta que haya carrera real. */
public class Resultados extends PantallaBase {
    private static final float[] ANCHOS = {40f, 130f, 100f, 110f};
    private static final float ALTO_FILA = 20f;
    private static final String[] ENCABEZADO = {"POS", "PILOTO", "TIEMPO", "MEJOR VUELTA"};
    private static final String[][] EJEMPLO = {
        {"1", "Jugador 1", "3:42.118", "1:09.870"},
        {"2", "Jugador 2", "3:43.322", "1:10.104"},
        {"3", "Jugador 3", "3:45.907", "1:10.551"},
        {"4", "Jugador 4", "3:47.030", "1:11.209"},
        {"5", "Jugador 5", "3:49.615", "1:11.874"},
    };

    public Resultados(Main juego) {
        super(juego);

        contenido.add(new Label("RESULTADOS", skin, "titulo")).padBottom(10).row();
        contenido.add(tabla()).padBottom(10).row();
        contenido.add(boton("VOLVER AL MENÚ", () -> juego.irA(new MenuPrincipal(juego)))).width(180).height(30);
    }

    private Table tabla() {
        Table tabla = new Table();
        tabla.setBackground(recursos.color(Paleta.GRIS_OSCURO));
        tabla.pad(6);
        agregarFila(tabla, ENCABEZADO, "gris");
        for (String[] fila : EJEMPLO) {
            agregarFila(tabla, fila, "default");
        }
        return tabla;
    }

    private void agregarFila(Table tabla, String[] textos, String estilo) {
        for (int i = 0; i < textos.length; i++) {
            // La posición del ganador va en rojo.
            String estiloCelda = (i == 0 && textos[0].equals("1")) ? "rojo" : estilo;
            tabla.add(new Label(textos[i], skin, estiloCelda)).width(ANCHOS[i]).height(ALTO_FILA).left();
        }
        tabla.row();
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
