package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import java.util.List;
import java.util.Set;

public class MiniMapa extends Actor {
    private static final float LADO_MAXIMO = 200f;
    private static final float MARGEN = 12f;
    private static final float GROSOR_TRAZO = 3f;
    private static final float LARGO_SEGMENTO = 3f;
    private static final float LARGO_META = 11f;
    private static final float GROSOR_META = 3f;
    private static final float DIAMETRO_AUTO = 9f;
    private static final float DIAMETRO_PROPIO = 11f;
    private static final float BORDE_AUTO = 1.5f;
    private static final float BORDE_PROPIO = 2f;

    private final TextureRegion blanco;
    private final TextureRegion punto;
    private final FuenteCarrera fuente;
    private final Set<Integer> idsLocales;
    private final boolean soloPropios;
    private final float escala;
    private final float mundoX;
    private final float mundoY;
    private final float[] segmentos;
    private final float metaX;
    private final float metaY;
    private final float metaAngulo;
    private FotoCarrera foto;

    public MiniMapa(Circuito circuito, FuenteCarrera fuente, TextureRegion blanco, TextureRegion punto, boolean soloPropios) {
        this.soloPropios = soloPropios;
        this.fuente = fuente;
        this.blanco = blanco;
        this.punto = punto;
        this.idsLocales = Set.copyOf(fuente.idsLocales());
        setTouchable(Touchable.disabled);

        float[] linea = circuito.geometria().linea();
        int muestras = circuito.geometria().muestras();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < muestras; i++) {
            minX = Math.min(minX, linea[2 * i]);
            maxX = Math.max(maxX, linea[2 * i]);
            minY = Math.min(minY, linea[2 * i + 1]);
            maxY = Math.max(maxY, linea[2 * i + 1]);
        }
        mundoX = minX;
        mundoY = minY;
        escala = LADO_MAXIMO / Math.max(maxX - minX, maxY - minY);
        setSize((maxX - minX) * escala + 2 * MARGEN, (maxY - minY) * escala + 2 * MARGEN);

        float pasoEnPantalla = (float) Math.hypot(linea[2] - linea[0], linea[3] - linea[1]) * escala;
        int salto = Math.max(1, Math.round(LARGO_SEGMENTO / pasoEnPantalla));
        int cantidad = muestras / salto;
        segmentos = new float[cantidad * 4];
        for (int s = 0; s < cantidad; s++) {
            int desde = s * salto;
            int hasta = s == cantidad - 1 ? 0 : (s + 1) * salto;
            float x0 = panelX(linea[2 * desde]);
            float y0 = panelY(linea[2 * desde + 1]);
            float x1 = panelX(linea[2 * hasta]);
            float y1 = panelY(linea[2 * hasta + 1]);
            segmentos[4 * s] = x0;
            segmentos[4 * s + 1] = y0;
            segmentos[4 * s + 2] = (float) Math.hypot(x1 - x0, y1 - y0) + GROSOR_TRAZO / 4f;
            segmentos[4 * s + 3] = MathUtils.atan2(y1 - y0, x1 - x0) * MathUtils.radiansToDegrees;
        }
        metaX = panelX(linea[0]);
        metaY = panelY(linea[1]);
        metaAngulo = MathUtils.atan2(linea[3] - linea[1], linea[2] - linea[0]) * MathUtils.radiansToDegrees + 90f;
    }

    private float panelX(float mundo) {
        return MARGEN + (mundo - mundoX) * escala;
    }

    private float panelY(float mundo) {
        return MARGEN + (mundo - mundoY) * escala;
    }

    public void actualizar(FotoCarrera nueva) {
        foto = nueva;
    }

    @Override
    public void draw(Batch batch, float alfaPadre) {
        float colorOriginal = batch.getPackedColor();
        float x = getX();
        float y = getY();
        batch.setColor(Paleta.FONDO_HUD);
        batch.draw(blanco, x, y, getWidth(), getHeight());

        batch.setColor(Paleta.TEXTO_SUAVE);
        for (int s = 0; s < segmentos.length; s += 4) {
            batch.draw(blanco, x + segmentos[s], y + segmentos[s + 1] - GROSOR_TRAZO / 2f, 0f, GROSOR_TRAZO / 2f,
                segmentos[s + 2], GROSOR_TRAZO, 1f, 1f, segmentos[s + 3]);
        }
        batch.setColor(Paleta.BLANCO);
        batch.draw(blanco, x + metaX - LARGO_META / 2f, y + metaY - GROSOR_META / 2f, LARGO_META / 2f, GROSOR_META / 2f,
            LARGO_META, GROSOR_META, 1f, 1f, metaAngulo);

        if (foto != null) {
            dibujarAutos(batch, foto.autos());
        }
        batch.setPackedColor(colorOriginal);
    }

    private void dibujarAutos(Batch batch, List<FotoAuto> autos) {
        for (int i = autos.size() - 1; i >= 0; i--) {
            if (!soloPropios && !idsLocales.contains(autos.get(i).id())) {
                dibujarAuto(batch, autos.get(i), DIAMETRO_AUTO, BORDE_AUTO, Paleta.FONDO);
            }
        }
        for (FotoAuto auto : autos) {
            if (idsLocales.contains(auto.id())) {
                dibujarAuto(batch, auto, DIAMETRO_PROPIO, BORDE_PROPIO, Paleta.BLANCO);
            }
        }
    }

    private void dibujarAuto(Batch batch, FotoAuto auto, float diametro, float borde, Color colorBorde) {
        if (auto.desconectado()) {
            return;
        }
        float centroX = getX() + panelX(auto.x());
        float centroY = getY() + panelY(auto.y());
        circulo(batch, centroX, centroY, diametro + 2f * borde, colorBorde);
        circulo(batch, centroX, centroY, diametro, Paleta.colorDePiloto(fuente.piloto(auto.id())));
    }

    private void circulo(Batch batch, float centroX, float centroY, float diametro, Color color) {
        batch.setColor(color);
        batch.draw(punto, centroX - diametro / 2f, centroY - diametro / 2f, diametro, diametro);
    }
}
