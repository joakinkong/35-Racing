package ar.edu.et35.racing;

import ar.edu.et35.racing.pantallas.MenuPrincipal;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

public class Main extends Game {
    private Recursos recursos;
    private SesionRed sesion;

    @Override
    public void create() {
        recursos = new Recursos();
        setScreen(new MenuPrincipal(this));
    }

    public Recursos recursos() {
        return recursos;
    }

    public SesionRed sesion() {
        return sesion;
    }

    public void setSesion(SesionRed nueva) {
        cerrarSesion();
        sesion = nueva;
    }

    public void cerrarSesion() {
        if (sesion != null) {
            sesion.cerrar();
            sesion = null;
        }
    }

    public void irA(Screen nueva) {
        Gdx.app.postRunnable(() -> {
            Screen vieja = getScreen();
            setScreen(nueva);
            if (vieja != null) {
                vieja.dispose();
            }
        });
    }

    @Override
    public void dispose() {
        Screen actual = getScreen();
        try {
            super.dispose();
        } catch (RuntimeException e) {
            System.err.println("Error al ocultar la pantalla: " + e);
        }
        try {
            cerrarSesion();
        } catch (RuntimeException e) {
            System.err.println("Error al cerrar la partida en red: " + e);
        }
        try {
            if (actual != null) {
                actual.dispose();
            }
        } catch (RuntimeException e) {
            System.err.println("Error al liberar la pantalla: " + e);
        }
        recursos.dispose();
    }
}
