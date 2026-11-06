import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Recursos;
import ar.edu.et35.racing.vista.VistaAuto;
import ar.edu.et35.racing.vista.VistaCircuito;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.HashMap;
import java.util.Map;

/** Prueba descartable: dibuja la VistaCircuito real en una ventana oculta (MSAA 4) y guarda capturas en PNG. */
public class CapturaVista extends ApplicationAdapter {
    final String carpetaCircuitos;
    final String salida;
    final String[] tomas;
    Recursos recursos;

    CapturaVista(String carpetaCircuitos, String salida, String[] tomas) {
        this.carpetaCircuitos = carpetaCircuitos;
        this.salida = salida;
        this.tomas = tomas;
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("captura");
        c.setInitialVisible(false);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        c.setWindowedMode(1280, 720);
        String[] tomas = new String[args.length - 2];
        System.arraycopy(args, 2, tomas, 0, tomas.length);
        new Lwjgl3Application(new CapturaVista(args[0], args[1], tomas), c);
    }

    @Override
    public void create() {
        recursos = new Recursos();
        Map<String, Circuito> circuitos = new HashMap<>();
        Map<String, VistaCircuito> vistas = new HashMap<>();
        VistaAuto auto = new VistaAuto();
        for (String toma : tomas) {
            String[] partes = toma.split(":");
            String nombre = partes[0];
            int indice = Integer.parseInt(partes[1]);
            float zoom = partes.length > 2 ? Float.parseFloat(partes[2]) : 1.25f;
            Circuito circuito = circuitos.computeIfAbsent(nombre, n ->
                CargadorCircuito.cargar(Gdx.files.absolute(carpetaCircuitos + "/" + n + ".json"), Config.MAX_JUGADORES));
            VistaCircuito vista = vistas.computeIfAbsent(nombre, n -> new VistaCircuito(circuito, recursos.skin.getRegion("blanco")));
            Circuito.Geometria g = circuito.geometria();
            OrthographicCamera camara = new OrthographicCamera(640, 360);
            camara.zoom = zoom;
            camara.position.set(g.linea()[2 * indice], g.linea()[2 * indice + 1], 0);
            camara.update();

            Gdx.gl.glViewport(0, 0, 1280, 720);
            ScreenUtils.clear(0f, 0f, 0f, 1f);
            vista.dibujar(camara);
            // Un auto en el centro, para tener una referencia de tamaño. Con "abajo" al final, pasa debajo del puente.
            boolean abajo = partes.length > 3 && partes[3].equals("abajo");
            if (abajo) {
                auto.dibujar(g.linea()[2 * indice], g.linea()[2 * indice + 1], 0f, Color.valueOf("E8002D"), Color.valueOf("FFEB00"), camara);
            }
            vista.dibujarPuentes(camara);
            if (!abajo) {
                auto.dibujar(g.linea()[2 * indice], g.linea()[2 * indice + 1], 0f, Color.valueOf("E8002D"), Color.valueOf("FFEB00"), camara);
            }
            Gdx.gl.glFinish();
            Pixmap leido = Pixmap.createFromFrameBuffer(0, 0, 1280, 720);
            // El framebuffer viene con el origen abajo: se da vuelta para que la imagen salga derecha.
            Pixmap pixmap = new Pixmap(1280, 720, leido.getFormat());
            for (int y = 0; y < 720; y++) {
                pixmap.drawPixmap(leido, 0, y, 0, 719 - y, 1280, 1);
            }
            PixmapIO.writePNG(Gdx.files.absolute(salida + "/" + nombre + "_" + indice + (partes.length > 2 ? "_" + partes[2] : "") + (abajo ? "_abajo" : "") + ".png"), pixmap);
            leido.dispose();
            pixmap.dispose();
        }
        Gdx.app.exit();
    }
}
