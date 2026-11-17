package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

public class VistaAuto implements Disposable {
    private static final Color NEGRO = new Color(0.06f, 0.06f, 0.08f, 1f);
    private static final Color CARBONO = new Color(0.15f, 0.15f, 0.17f, 1f);
    private static final Color CASCO = Color.WHITE;
    private static final Color VERDE_FLUO = new Color(0.22f, 1f, 0.08f, 1f);

    private static final float[][] CUERPO = {
        {-14f, 2f}, {-10f, 3.6f}, {-5f, 4.8f}, {0f, 4.8f}, {3f, 3f}, {8f, 2f}, {14f, 1.4f}, {16f, 0.9f}};
    private static final float[][] FRANJA = {{3f, 0.9f}, {8f, 0.8f}, {15.5f, 0.5f}};
    private static final float[][] CUBIERTA = {{-14f, 0.7f}, {-5.5f, 1f}};
    private static final int LADOS_CASCO = 10;

    private final ShapeRenderer formas = new ShapeRenderer();
    private float cos;
    private float sen;
    private float origenX;
    private float origenY;

    public void dibujar(float x, float y, float grados, Color color, Color detalle, boolean segundoPiloto, OrthographicCamera camara) {
        cos = MathUtils.cosDeg(grados);
        sen = MathUtils.sinDeg(grados);
        origenX = x;
        origenY = y;
        formas.setProjectionMatrix(camara.combined);
        formas.begin(ShapeType.Filled);
        rectangulo(-13f, 4.8f, -6f, 8.6f, NEGRO);
        rectangulo(-13f, -8.6f, -6f, -4.8f, NEGRO);
        rectangulo(6f, 3.8f, 11.5f, 7f, NEGRO);
        rectangulo(6f, -7f, 11.5f, -3.8f, NEGRO);
        rectangulo(7.8f, -4f, 9.2f, 4f, CARBONO);
        rectangulo(-10.2f, -5f, -8.8f, 5f, CARBONO);
        rectangulo(-16.5f, -7f, -14f, 7f, color);
        rectangulo(-16.5f, 7f, -14f, 7.8f, NEGRO);
        rectangulo(-16.5f, -7.8f, -14f, -7f, NEGRO);
        rectangulo(14.5f, -7.8f, 16.5f, 7.8f, detalle);
        tira(CUERPO, color);
        tira(FRANJA, detalle);
        tira(CUBIERTA, detalle);
        rectangulo(-8f, 3.5f, -1.5f, 4.8f, detalle);
        rectangulo(-8f, -4.8f, -1.5f, -3.5f, detalle);
        rectangulo(-5.5f, -1.9f, 2.5f, 1.9f, NEGRO);
        circulo(-1.5f, 0f, 1.5f, CASCO);
        if (segundoPiloto) {
            rectangulo(-8.5f, -1.6f, -5.3f, 1.6f, VERDE_FLUO);
        }
        formas.end();
    }

    public void dibujarAureola(float x, float y, OrthographicCamera camara) {
        float latido = MathUtils.sin(TimeUtils.millis() / 1000f * Config.AUREOLA_FRECUENCIA * MathUtils.PI2);
        float radio = Config.AUREOLA_RADIO + latido * Config.AUREOLA_LATIDO;
        float grosor = Config.AUREOLA_GROSOR;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        formas.setProjectionMatrix(camara.combined);
        formas.begin(ShapeType.Filled);
        corona(x, y, radio + grosor / 2f, radio + grosor * 2f, Config.AUREOLA_OPACIDAD * Config.AUREOLA_OPACIDAD_HALO);
        corona(x, y, radio - grosor / 2f, radio + grosor / 2f, Config.AUREOLA_OPACIDAD);
        formas.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void corona(float cx, float cy, float interior, float exterior, float opacidad) {
        formas.setColor(1f, 1f, 1f, opacidad);
        for (int k = 0; k < Config.AUREOLA_LADOS; k++) {
            float a1 = MathUtils.PI2 * k / Config.AUREOLA_LADOS;
            float a2 = MathUtils.PI2 * (k + 1) / Config.AUREOLA_LADOS;
            float c1 = MathUtils.cos(a1);
            float s1 = MathUtils.sin(a1);
            float c2 = MathUtils.cos(a2);
            float s2 = MathUtils.sin(a2);
            formas.triangle(cx + c1 * interior, cy + s1 * interior, cx + c1 * exterior, cy + s1 * exterior,
                cx + c2 * exterior, cy + s2 * exterior);
            formas.triangle(cx + c1 * interior, cy + s1 * interior, cx + c2 * exterior, cy + s2 * exterior,
                cx + c2 * interior, cy + s2 * interior);
        }
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

    private void triangulo(float x1, float y1, float x2, float y2, float x3, float y3) {
        formas.triangle(mundoX(x1, y1), mundoY(x1, y1), mundoX(x2, y2), mundoY(x2, y2), mundoX(x3, y3), mundoY(x3, y3));
    }

    private float mundoX(float x, float y) {
        return origenX + (x * cos - y * sen) * Config.ESCALA_AUTO;
    }

    private float mundoY(float x, float y) {
        return origenY + (x * sen + y * cos) * Config.ESCALA_AUTO;
    }

    @Override
    public void dispose() {
        formas.dispose();
    }
}
