import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Escuderia;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.vista.VistaAuto;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.List;

/** Dibuja un auto de cada escudería sobre el color del asfalto, grandes y a la escala de la carrera, en un PNG. */
public class CapturaAutos extends ApplicationAdapter {
    private static final int ANCHO = 1280;
    private static final int ALTO = 720;
    private final String salida;

    CapturaAutos(String salida) {
        this.salida = salida;
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("autos");
        c.setInitialVisible(false);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        c.setWindowedMode(ANCHO, ALTO);
        new Lwjgl3Application(new CapturaAutos(args[0]), c);
    }

    @Override
    public void create() {
        VistaAuto vista = new VistaAuto();
        OrthographicCamera camara = new OrthographicCamera(ANCHO / 3f, ALTO / 3f);
        camara.position.set(ANCHO / 6f, ALTO / 6f, 0f);
        camara.update();
        Gdx.gl.glViewport(0, 0, ANCHO, ALTO);
        ScreenUtils.clear(Paleta.PISTA_ASFALTO);
        List<Escuderia> escuderias = Catalogo.ESCUDERIAS;
        for (int i = 0; i < escuderias.size(); i++) {
            Escuderia e = escuderias.get(i);
            Color cuerpo = new Color(e.colorAuto() << 8 | 0xFF);
            Color detalle = new Color(e.colorDetalle() << 8 | 0xFF);
            float x = 45f + (i % 4) * 95f;
            float y = ALTO / 3f - 45f - (i / 4) * 50f;
            vista.dibujar(x, y, 0f, cuerpo, detalle, camara);
        }
        // Abajo, en tamaño de carrera: se dibujan a un tercio de la escala de arriba.
        OrthographicCamera chica = new OrthographicCamera(ANCHO / 1.6f, ALTO / 1.6f);
        chica.position.set(ANCHO / 3.2f, ALTO / 3.2f, 0f);
        chica.update();
        for (int i = 0; i < escuderias.size(); i++) {
            Escuderia e = escuderias.get(i);
            vista.dibujar(40f + i * 66f, 40f, 0f, new Color(e.colorAuto() << 8 | 0xFF), new Color(e.colorDetalle() << 8 | 0xFF), chica);
        }
        Gdx.gl.glFinish();
        Pixmap leido = Pixmap.createFromFrameBuffer(0, 0, ANCHO, ALTO);
        Pixmap pixmap = new Pixmap(ANCHO, ALTO, leido.getFormat());
        for (int y = 0; y < ALTO; y++) {
            pixmap.drawPixmap(leido, 0, y, 0, ALTO - 1 - y, ANCHO, 1);
        }
        PixmapIO.writePNG(Gdx.files.absolute(salida + "/autos.png"), pixmap);
        Gdx.app.exit();
    }
}
