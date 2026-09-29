package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import java.util.List;

/**
 * Cámara que encuadra a los autos que se le indican, con un leve adelanto hacia donde van. Con un solo auto
 * lo sigue; con varios apunta al centro del grupo y aleja el zoom, hasta {@link #ZOOM_MAX}. Si el grupo no
 * entra ni con ese zoom, quien la use tiene que pasar a pantalla dividida (ver {@link #zoomNecesario}).
 */
public class CamaraSeguimiento {
    /** Alejamiento máximo (2 = se ve el doble de mundo). */
    public static final float ZOOM_MAX = 2f;

    /** Segundos de velocidad que la cámara se adelanta al auto. */
    private static final float ADELANTO = 0.3f;
    /** Cuánto más alto, más rápido alcanza la posición y el zoom objetivo. */
    private static final float SUAVIZADO = 6f;
    /** Espacio libre alrededor de los autos al encuadrarlos (px de mundo). */
    private static final float MARGEN = 90f;

    private final OrthographicCamera camara;
    private final Circuito circuito;
    private float centroX;
    private float centroY;
    /** Si no es NaN, el zoom queda fijo en este valor en vez de ajustarse a los autos. */
    private float zoomFijo = Float.NaN;

    public CamaraSeguimiento(OrthographicCamera camara, Circuito circuito, List<Auto> autos) {
        this.camara = camara;
        this.circuito = circuito;
        this.centroX = autos.get(0).x();
        this.centroY = autos.get(0).y();
        aplicar();
    }

    /**
     * Deja el zoom fijo (por ejemplo, en {@link #ZOOM_MAX} en pantalla dividida, para que el corte entre una y
     * dos pantallas no cambie la escala del mundo).
     */
    public void fijarZoom(float zoom) {
        zoomFijo = zoom;
        camara.zoom = zoom;
        aplicar();
    }

    /**
     * Zoom que haría falta para ver a todos los autos a la vez con la vista normal (640x360); 1 = sin alejar.
     * Si supera {@link #ZOOM_MAX}, el grupo no entra en una sola pantalla.
     */
    public static float zoomNecesario(List<Auto> autos, float alfa) {
        float[] limites = limites(autos, alfa);
        return Math.max((limites[2] - limites[0] + 2f * MARGEN) / Config.ANCHO_VIRTUAL,
            (limites[3] - limites[1] + 2f * MARGEN) / Config.ALTO_VIRTUAL);
    }

    /** Devuelve {minX, minY, maxX, maxY} de los autos, ya con el adelanto. */
    private static float[] limites(List<Auto> autos, float alfa) {
        float[] limites = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Auto auto : autos) {
            float x = auto.xInterpolada(alfa) + auto.velocidad().x * ADELANTO;
            float y = auto.yInterpolada(alfa) + auto.velocidad().y * ADELANTO;
            limites[0] = Math.min(limites[0], x);
            limites[1] = Math.min(limites[1], y);
            limites[2] = Math.max(limites[2], x);
            limites[3] = Math.max(limites[3], y);
        }
        return limites;
    }

    public void actualizar(List<Auto> autos, float alfa, float delta) {
        float[] limites = limites(autos, alfa);
        float objetivoX = (limites[0] + limites[2]) / 2f;
        float objetivoY = (limites[1] + limites[3]) / 2f;
        float objetivoZoom = Float.isNaN(zoomFijo) ? MathUtils.clamp(zoomNecesario(autos, alfa), 1f, ZOOM_MAX) : zoomFijo;

        float k = 1f - (float) Math.exp(-SUAVIZADO * delta);
        centroX += (objetivoX - centroX) * k;
        centroY += (objetivoY - centroY) * k;
        camara.zoom += (objetivoZoom - camara.zoom) * k;
        aplicar();
    }

    private void aplicar() {
        // El tamaño de la vista sale de la cámara: el de cada mitad es distinto en pantalla dividida.
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;
        float x = MathUtils.clamp(centroX, mitadAncho, circuito.ancho() - mitadAncho);
        float y = MathUtils.clamp(centroY, mitadAlto, circuito.alto() - mitadAlto);
        // Posición entera: con pixel art evita costuras entre tiles al mover la cámara.
        camara.position.set(Math.round(x), Math.round(y), 0f);
        camara.update();
    }
}
