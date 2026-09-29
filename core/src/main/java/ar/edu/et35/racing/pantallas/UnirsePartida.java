package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;

/** Formulario para unirse a una partida: nombre del jugador y dirección IP del anfitrión. */
public class UnirsePartida extends PantallaBase {
    private static final float ANCHO_CAMPO = 220f;
    private static final float ALTO_CAMPO = 26f;

    private final TextField campoNombre;
    private final TextField campoIp;

    public UnirsePartida(Main juego) {
        super(juego);

        campoNombre = new TextField("", skin);
        campoNombre.setMessageText("Nombre");
        campoIp = new TextField("", skin);
        campoIp.setMessageText("IP del anfitrión");

        contenido.add(new Label("UNIRSE", skin, "titulo")).padBottom(12).row();
        contenido.add(campoNombre).width(ANCHO_CAMPO).height(ALTO_CAMPO).pad(3).row();
        contenido.add(campoIp).width(ANCHO_CAMPO).height(ALTO_CAMPO).pad(3).row();
        // Por ahora Conectar no abre ningún socket: solo navega al Lobby.
        contenido.add(boton("CONECTAR", () -> juego.irA(new Lobby(juego))))
            .width(ANCHO_CAMPO).height(30).padTop(8).row();
        contenido.add(new Label("ESC para volver", skin, "gris")).padTop(8);
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
