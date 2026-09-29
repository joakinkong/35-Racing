package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import java.util.List;

/**
 * Cámara que encuadra a los autos que se le indican, con un leve adelanto hacia donde van. Con un solo auto
 * lo sigue; con varios apunta al centro del grupo y aleja el zoom hasta {@link #ZOOM_MAX} para que entren.
 * Si se separan más que eso, deja de encuadrar al grupo y sigue solo al primero de la lista.
 */
public class CamaraSeguimiento {
    /** Segundos de velocidad que la cámara se adelanta al auto. */
    private static final float ADELANTO = 0.3f;
    /** Cuánto más alto, más rápido alcanza la posición y el zoom objetivo. */
    private static final float SUAVIZADO = 6f;
    /** Alejamiento máximo (2 = se ve el doble de mundo). */
    private static final float ZOOM_MAX = 2f;
    /** Espacio libre alrededor de los autos al encuadrarlos (px de mundo). */
    private static final float MARGEN = 90f;

    private final OrthographicCamera camara;
    private final Circuito circuito;
    private float centroX;
    private float centroY;

    public CamaraSeguimiento(OrthographicCamera camara, Circuito circuito, List<Auto> autos) {
        this.camara = camara;
        this.circuito = circuito;
        this.centroX = autos.get(0).x();
        this.centroY = autos.get(0).y();
        aplicar();
    }

    /** El primer auto de la lista es el que se sigue si el grupo no entra en pantalla. */
    public void actualizar(List<Auto> autos, float alfa, float delta) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Auto auto : autos) {
            float x = auto.xInterpolada(alfa) + auto.velocidad().x * ADELANTO;
            float y = auto.yInterpolada(alfa) + auto.velocidad().y * ADELANTO;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        float zoomNecesario = Math.max((maxX - minX + 2f * MARGEN) / Config.ANCHO_VIRTUAL,
            (maxY - minY + 2f * MARGEN) / Config.ALTO_VIRTUAL);

        float objetivoX = (minX + maxX) / 2f;
        float objetivoY = (minY + maxY) / 2f;
        float objetivoZoom = MathUtils.clamp(zoomNecesario, 1f, ZOOM_MAX);
        if (zoomNecesario > ZOOM_MAX) {
            Auto lider = autos.get(0);
            objetivoX = lider.xInterpolada(alfa) + lider.velocidad().x * ADELANTO;
            objetivoY = lider.yInterpolada(alfa) + lider.velocidad().y * ADELANTO;
        }

        float k = 1f - (float) Math.exp(-SUAVIZADO * delta);
        centroX += (objetivoX - centroX) * k;
        centroY += (objetivoY - centroY) * k;
        camara.zoom += (objetivoZoom - camara.zoom) * k;
        aplicar();
    }

    private void aplicar() {
        float mitadAncho = Config.ANCHO_VIRTUAL * camara.zoom / 2f;
        float mitadAlto = Config.ALTO_VIRTUAL * camara.zoom / 2f;
        float x = MathUtils.clamp(centroX, mitadAncho, circuito.ancho() - mitadAncho);
        float y = MathUtils.clamp(centroY, mitadAlto, circuito.alto() - mitadAlto);
        // Posición entera: con pixel art evita costuras entre tiles al mover la cámara.
        camara.position.set(Math.round(x), Math.round(y), 0f);
        camara.update();
    }
}
