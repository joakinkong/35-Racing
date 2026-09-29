package ar.edu.et35.racing;

import ar.edu.et35.racing.pantallas.MenuPrincipal;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

/** Punto de entrada compartido por todas las plataformas. Maneja la pantalla activa. */
public class Main extends Game {
    private Recursos recursos;

    @Override
    public void create() {
        recursos = new Recursos();
        setScreen(new MenuPrincipal(this));
    }

    public Recursos recursos() {
        return recursos;
    }

    /**
     * Cambia de pantalla y libera la anterior. Se hace en postRunnable porque casi siempre se llama
     * desde un botón de la pantalla vieja: no hay que destruirla en medio de su propio evento.
     */
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
        super.dispose();
        if (actual != null) {
            actual.dispose();
        }
        recursos.dispose();
    }
}
