package ar.edu.et35.racing;

import ar.edu.et35.racing.pantallas.MenuPrincipal;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

/** Punto de entrada compartido por todas las plataformas. Maneja la pantalla activa. */
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

    /** La partida en red que hay abierta (cliente y, si esta PC es el host, servidor), o null si no hay ninguna. */
    public SesionRed sesion() {
        return sesion;
    }

    public void setSesion(SesionRed nueva) {
        cerrarSesion();
        sesion = nueva;
    }

    /** Cierra la partida en red si hay una abierta: avisa que se va, cierra sockets y detiene los hilos de red. */
    public void cerrarSesion() {
        if (sesion != null) {
            sesion.cerrar();
            sesion = null;
        }
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
        // Cada paso va por separado: si uno falla, los demás igual se hacen (sobre todo cerrar sockets e hilos).
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
