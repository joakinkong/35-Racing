package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** Menú principal, con las luces rojas de largada bajo el título. */
public class MenuPrincipal extends PantallaBase {
    private static final int LUCES = 5;
    private static final float LADO_LUZ = 14f;
    private static final float ANCHO_BOTON = 220f;
    private static final float ALTO_BOTON = 30f;

    public MenuPrincipal(Main juego) {
        super(juego);

        contenido.add(new Label("35 RACING", skin, "titulo")).row();
        contenido.add(new Label("F1 ARCADE  ·  RED LOCAL", skin, "gris")).padBottom(8).row();
        contenido.add(luces()).padBottom(12).row();

        agregarBoton("CREAR PARTIDA", () -> juego.irA(new Lobby(juego)));
        agregarBoton("UNIRSE A UNA PARTIDA", () -> juego.irA(new UnirsePartida(juego)));
        agregarBoton("PRUEBA LOCAL", () -> juego.irA(new PantallaCarrera(juego, 2)));
        agregarBoton("SALIR", Gdx.app::exit);
    }

    private void agregarBoton(String texto, Runnable accion) {
        contenido.add(boton(texto, accion)).width(ANCHO_BOTON).height(ALTO_BOTON).pad(3).row();
    }

    private Table luces() {
        Table luces = new Table();
        for (int i = 0; i < LUCES; i++) {
            luces.add(new Image(recursos.color(Paleta.ROJO))).size(LADO_LUZ).pad(3);
        }
        return luces;
    }
}
