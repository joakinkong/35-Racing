package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.Circuito;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;

/**
 * Cámara de un auto: lo sigue con un leve adelanto hacia donde va, con el zoom fijo que se le indique, y sin
 * salirse del mapa. El tamaño de la vista sale de la cámara, así que sirve para pantalla completa o para una
 * mitad de pantalla dividida.
 */
public class CamaraSeguimiento {
    /** Segundos de velocidad que la cámara se adelanta al auto. */
    private static final float ADELANTO = 0.3f;
    /** Cuánto más alto, más rápido alcanza la posición objetivo. */
    private static final float SUAVIZADO = 6f;

    private final OrthographicCamera camara;
    private final Circuito circuito;
    private float centroX;
    private float centroY;

    /** @param zoom 1 = escala normal; 2 = se ve el doble de mundo. */
    public CamaraSeguimiento(OrthographicCamera camara, Circuito circuito, Auto auto, float zoom) {
        this.camara = camara;
        this.circuito = circuito;
        this.centroX = auto.x();
        this.centroY = auto.y();
        camara.zoom = zoom;
        aplicar();
    }

    public void actualizar(Auto auto, float alfa, float delta) {
        float objetivoX = auto.xInterpolada(alfa) + auto.velocidad().x * ADELANTO;
        float objetivoY = auto.yInterpolada(alfa) + auto.velocidad().y * ADELANTO;
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
        // Posición entera: con pixel art evita costuras entre tiles al mover la cámara.
        camara.position.set(Math.round(x), Math.round(y), 0f);
        camara.update();
    }
}
