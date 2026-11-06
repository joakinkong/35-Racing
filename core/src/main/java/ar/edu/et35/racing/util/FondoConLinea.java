package ar.edu.et35.racing.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/** Fondo de un color con una línea de otro color en un borde: las tarjetas, solapas y encabezados de PIT WALL. */
public class FondoConLinea extends BaseDrawable {
    public enum Lado { ARRIBA, ABAJO, IZQUIERDA }

    private final TextureRegion blanco;
    private final Color fondo;
    private final Color linea;
    private final Lado lado;
    private final float grosor;

    /** @param blanco región de una textura blanca; no la libera esta clase */
    public FondoConLinea(TextureRegion blanco, Color fondo, Color linea, Lado lado, float grosor) {
        this.blanco = blanco;
        this.fondo = fondo;
        this.linea = linea;
        this.lado = lado;
        this.grosor = grosor;
    }

    @Override
    public void draw(Batch batch, float x, float y, float ancho, float alto) {
        float color = batch.getPackedColor();
        batch.setColor(fondo);
        batch.draw(blanco, x, y, ancho, alto);
        batch.setColor(linea);
        switch (lado) {
            case ARRIBA:
                batch.draw(blanco, x, y + alto - grosor, ancho, grosor);
                break;
            case ABAJO:
                batch.draw(blanco, x, y, ancho, grosor);
                break;
            case IZQUIERDA:
            default:
                batch.draw(blanco, x, y, grosor, alto);
                break;
        }
        batch.setPackedColor(color);
    }
}
