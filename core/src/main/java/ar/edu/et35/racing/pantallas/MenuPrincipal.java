package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

/** Menú principal: a la izquierda la marca con las luces rojas de largada, a la derecha las opciones. */
public class MenuPrincipal extends PantallaBase {
    private static final int LUCES = 5;
    private static final float LADO_LUZ = 26f;
    private static final float ANCHO_MARCA = 560f;
    private static final float ANCHO_OPCIONES = 400f;
    private static final float SEPARACION = 40f;
    private static final float ALTO_BOTON = 52f;

    public MenuPrincipal(Main juego) {
        this(juego, null);
    }

    /** @param aviso texto para mostrar bajo las opciones (por ejemplo, por qué se cortó una partida), o null */
    public MenuPrincipal(Main juego, String aviso) {
        super(juego);
        // En el menú nunca hay una partida en red abierta: si veníamos de una, se cierra acá.
        juego.cerrarSesion();

        Table marca = new Table();
        marca.left();
        marca.add(new Label("F1 ARCADE", skin, "seccion")).left().padBottom(6).row();
        marca.add(new Label("35 RACING", skin, "titulo")).left().padBottom(18).row();
        marca.add(luces()).left();

        Table opciones = tarjeta(Paleta.SECUNDARIO);
        opciones.add(new Label("MENÚ PRINCIPAL", skin, "etiqueta")).left().padBottom(12).row();
        agregarBoton(opciones, botonPrimario("CREAR PARTIDA", () -> juego.irA(new UnirsePartida(juego, true))));
        agregarBoton(opciones, boton("UNIRSE A UNA PARTIDA", () -> juego.irA(new UnirsePartida(juego, false))));
        agregarBoton(opciones, boton("PRUEBA LOCAL", () -> juego.irA(new PruebaLocal(juego))));
        agregarBoton(opciones, boton("SALIR", Gdx.app::exit));

        contenido.add(marca).width(ANCHO_MARCA).padRight(SEPARACION);
        contenido.add(opciones).width(ANCHO_OPCIONES).row();
        if (aviso != null) {
            contenido.add(textoLargo(aviso, "alerta")).colspan(2).width(ANCHO_MARCA + SEPARACION + ANCHO_OPCIONES).padTop(20);
        }
    }

    private void agregarBoton(Table opciones, TextButton boton) {
        opciones.add(boton).growX().height(ALTO_BOTON).padBottom(8).row();
    }

    private Table luces() {
        Table luces = new Table();
        for (int i = 0; i < LUCES; i++) {
            luces.add(new Image(recursos.color(Paleta.ROJO))).size(LADO_LUZ).padRight(10);
        }
        return luces;
    }
}
