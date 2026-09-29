package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Auto;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;

/**
 * Dibuja un auto como un monoplaza hecho con rectángulos (alerones, ruedas, cuerpo y casco), mientras no
 * haya sprites. Mira hacia +X cuando el ángulo es 0.
 */
public class VistaAuto implements Disposable {
    private static final Color NEGRO = new Color(0.06f, 0.06f, 0.08f, 1f);
    private static final Color CASCO = Color.WHITE;

    private final ShapeRenderer formas = new ShapeRenderer();

    /** Dibuja el auto interpolado entre el tick anterior y el actual (alfa entre 0 y 1). */
    public void dibujar(Auto auto, float alfa, Color color, OrthographicCamera camara) {
        float x = auto.xInterpolada(alfa);
        float y = auto.yInterpolada(alfa);
        float grados = auto.anguloInterpolado(alfa);

        formas.setProjectionMatrix(camara.combined);
        formas.begin(ShapeType.Filled);
        // Ruedas
        pieza(x, y, grados, 7f, 6f, 6f, 3f, NEGRO);
        pieza(x, y, grados, 7f, -6f, 6f, 3f, NEGRO);
        pieza(x, y, grados, -8f, 6f, 6f, 3f, NEGRO);
        pieza(x, y, grados, -8f, -6f, 6f, 3f, NEGRO);
        // Alerones
        pieza(x, y, grados, 14f, 0f, 3f, 13f, NEGRO);
        pieza(x, y, grados, -13f, 0f, 4f, 12f, NEGRO);
        // Cuerpo y trompa
        pieza(x, y, grados, -1f, 0f, 22f, 7f, color);
        pieza(x, y, grados, 10f, 0f, 8f, 3f, color);
        // Casco del piloto
        pieza(x, y, grados, -2f, 0f, 4f, 4f, CASCO);
        formas.end();
    }

    /** Rectángulo de ancho x alto centrado en (dx, dy) respecto del auto, rotado con el auto. */
    private void pieza(float x, float y, float grados, float dx, float dy, float ancho, float alto, Color color) {
        formas.setColor(color);
        // El origen de la rotación es el centro del auto: queda a (-dx + ancho/2, -dy + alto/2) de la esquina.
        formas.rect(x + dx - ancho / 2f, y + dy - alto / 2f, -dx + ancho / 2f, -dy + alto / 2f,
            ancho, alto, 1f, 1f, grados);
    }

    @Override
    public void dispose() {
        formas.dispose();
    }
}
