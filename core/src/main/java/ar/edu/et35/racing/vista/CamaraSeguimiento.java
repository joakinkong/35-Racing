package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;

public class CamaraSeguimiento {
    private static final float ADELANTO = 0.3f;
    private static final float SUAVIZADO = 6f;

    private final OrthographicCamera camara;
    private final Circuito circuito;
    private float centroX;
    private float centroY;

    public CamaraSeguimiento(OrthographicCamera camara, Circuito circuito, float x, float y, float zoom) {
        this.camara = camara;
        this.circuito = circuito;
        this.centroX = x;
        this.centroY = y;
        camara.zoom = zoom;
        aplicar();
    }

    public void actualizar(float x, float y, float vx, float vy, float delta) {
        float objetivoX = x + vx * ADELANTO;
        float objetivoY = y + vy * ADELANTO;
        float k = 1f - (float) Math.exp(-SUAVIZADO * delta);
        centroX += (objetivoX - centroX) * k;
        centroY += (objetivoY - centroY) * k;
        aplicar();
    }

    private void aplicar() {
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;
        float x = MathUtils.clamp(centroX, mitadAncho, circuito.ancho() - mitadAncho);
        float y = MathUtils.clamp(centroY, mitadAlto, circuito.alto() - mitadAlto);
        camara.position.set(Math.round(x), Math.round(y), 0f);
        camara.update();
    }
}
