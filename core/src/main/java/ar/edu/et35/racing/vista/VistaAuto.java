package ar.edu.et35.racing.vista;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * Dibuja un auto como un monoplaza visto desde arriba, con polígonos y el mismo antialiasing que la pista: el color
 * principal y el de acento salen de la escudería, así que hay uno distinto por equipo sin ninguna imagen. Mira hacia +X
 * cuando el ángulo es 0. Mide lo mismo que antes (unos 32 x 17 px), acorde al radio de colisión.
 */
public class VistaAuto implements Disposable {
    private static final Color NEGRO = new Color(0.06f, 0.06f, 0.08f, 1f);
    private static final Color CARBONO = new Color(0.15f, 0.15f, 0.17f, 1f);
    private static final Color CASCO = Color.WHITE;
    /** La marca del segundo piloto de cada escudería: verde fluo, sobre el techo (la toma de aire). */
    private static final Color VERDE_FLUO = new Color(0.22f, 1f, 0.08f, 1f);

    /** El cuerpo, de la cola a la trompa: x y medio ancho en cada estación. */
    private static final float[][] CUERPO = {
        {-14f, 2f}, {-10f, 3.6f}, {-5f, 4.8f}, {0f, 4.8f}, {3f, 3f}, {8f, 2f}, {14f, 1.4f}, {16f, 0.9f}};
    /** La franja de acento sobre la trompa. */
    private static final float[][] FRANJA = {{3f, 0.9f}, {8f, 0.8f}, {15.5f, 0.5f}};
    /** La cubierta del motor, de la cola a la cabina. */
    private static final float[][] CUBIERTA = {{-14f, 0.7f}, {-5.5f, 1f}};
    private static final int LADOS_CASCO = 10;

    private final ShapeRenderer formas = new ShapeRenderer();
    private float cos;
    private float sen;
    private float origenX;
    private float origenY;

    /**
     * Dibuja un auto en esa posición y ángulo (grados). Recibe datos simples y no un {@code Auto}, porque en red el
     * cliente no tiene la física del auto: solo lo que le manda el servidor.
     */
    public void dibujar(float x, float y, float grados, Color color, Color detalle, boolean segundoPiloto, OrthographicCamera camara) {
        cos = MathUtils.cosDeg(grados);
        sen = MathUtils.sinDeg(grados);
        origenX = x;
        origenY = y;
        formas.setProjectionMatrix(camara.combined);
        formas.begin(ShapeType.Filled);
        // Ruedas: las de atrás, más anchas
        rectangulo(-13f, 4.8f, -6f, 8.6f, NEGRO);
        rectangulo(-13f, -8.6f, -6f, -4.8f, NEGRO);
        rectangulo(6f, 3.8f, 11.5f, 7f, NEGRO);
        rectangulo(6f, -7f, 11.5f, -3.8f, NEGRO);
        // Brazos de la suspensión
        rectangulo(7.8f, -4f, 9.2f, 4f, CARBONO);
        rectangulo(-10.2f, -5f, -8.8f, 5f, CARBONO);
        // Alerones
        rectangulo(-16.5f, -7f, -14f, 7f, color);
        rectangulo(-16.5f, 7f, -14f, 7.8f, NEGRO);
        rectangulo(-16.5f, -7.8f, -14f, -7f, NEGRO);
        rectangulo(14.5f, -7.8f, 16.5f, 7.8f, detalle);
        // Cuerpo, franja y cabina
        tira(CUERPO, color);
        tira(FRANJA, detalle);
        tira(CUBIERTA, detalle);
        // Pontones: es lo que más se ve de arriba, y lo que separa a dos equipos de la misma carrocería
        rectangulo(-8f, 3.5f, -1.5f, 4.8f, detalle);
        rectangulo(-8f, -4.8f, -1.5f, -3.5f, detalle);
        rectangulo(-5.5f, -1.9f, 2.5f, 1.9f, NEGRO);
        circulo(-1.5f, 0f, 1.5f, CASCO);
        if (segundoPiloto) {
            rectangulo(-8.5f, -1.6f, -5.3f, 1.6f, VERDE_FLUO);
        }
        formas.end();
    }

    private void tira(float[][] estaciones, Color color) {
        for (int i = 0; i + 1 < estaciones.length; i++) {
            cuadrilatero(estaciones[i][0], estaciones[i][1], estaciones[i + 1][0], estaciones[i + 1][1], color);
        }
    }

    private void cuadrilatero(float x1, float medio1, float x2, float medio2, Color color) {
        formas.setColor(color);
        triangulo(x1, -medio1, x2, -medio2, x2, medio2);
        triangulo(x1, -medio1, x2, medio2, x1, medio1);
    }

    private void rectangulo(float x1, float y1, float x2, float y2, Color color) {
        formas.setColor(color);
        triangulo(x1, y1, x2, y1, x2, y2);
        triangulo(x1, y1, x2, y2, x1, y2);
    }

    private void circulo(float cx, float cy, float radio, Color color) {
        formas.setColor(color);
        for (int k = 0; k < LADOS_CASCO; k++) {
            float a1 = MathUtils.PI2 * k / LADOS_CASCO;
            float a2 = MathUtils.PI2 * (k + 1) / LADOS_CASCO;
            triangulo(cx, cy, cx + MathUtils.cos(a1) * radio, cy + MathUtils.sin(a1) * radio,
                cx + MathUtils.cos(a2) * radio, cy + MathUtils.sin(a2) * radio);
        }
    }

    /** Un triángulo en coordenadas del auto: se rota con el auto y se lleva a su posición. */
    private void triangulo(float x1, float y1, float x2, float y2, float x3, float y3) {
        formas.triangle(mundoX(x1, y1), mundoY(x1, y1), mundoX(x2, y2), mundoY(x2, y2), mundoX(x3, y3), mundoY(x3, y3));
    }

    private float mundoX(float x, float y) {
        return origenX + x * cos - y * sen;
    }

    private float mundoY(float x, float y) {
        return origenY + x * sen + y * cos;
    }

    @Override
    public void dispose() {
        formas.dispose();
    }
}
