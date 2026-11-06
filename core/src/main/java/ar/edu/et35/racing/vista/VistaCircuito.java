package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el circuito a partir de su geometría, con las mismas reglas que usa la colisión: cada punto de la línea
 * central pinta un círculo, y la pista es la unión de todos. Hay tres capas, de afuera hacia adentro: el muro (una
 * línea de {@code muro} px más allá del pasto), el pasto de escape y el asfalto. Si no sobra lugar para el pasto, el
 * muro queda pegado al asfalto. Los círculos se arman una sola vez, agrupados en bloques de puntos seguidos para
 * dibujar solo los que entran en la cámara.
 *
 * Como cada capa es de un solo color, alcanza con teñir una textura blanca de 1 x 1 con el color del batch.
 */
public class VistaCircuito implements Disposable {
    /** Lados de cada círculo: con 24 el borde se aparta de un círculo perfecto menos de 1 px en el radio máximo. */
    private static final int LADOS = 24;
    /** Puntos de la línea central por bloque: con 24 vértices por círculo son unos 6.400 vértices, lejos del tope del batch. */
    private static final int PUNTOS_POR_BLOQUE = 256;
    private static final int MAX_VERTICES_BATCH = 8192;
    private static final float GROSOR_META = 8f;

    /** Un grupo de círculos seguidos de una capa, con el rectángulo que los contiene. */
    private record Bloque(PolygonRegion region, float izquierda, float abajo, float derecha, float arriba) {
    }

    private final Circuito circuito;
    private final TextureRegion blanco;
    private final PolygonSpriteBatch batch = new PolygonSpriteBatch(MAX_VERTICES_BATCH);
    /** Bloques por capa: 0 = muro, 1 = pasto, 2 = asfalto. */
    private final List<List<Bloque>> capas = new ArrayList<>();

    /** @param blanco región de una textura blanca de 1 x 1; no la libera esta clase */
    public VistaCircuito(Circuito circuito, TextureRegion blanco) {
        this.circuito = circuito;
        this.blanco = blanco;
        for (int capa = 0; capa < 3; capa++) {
            capas.add(armarCapa(capa));
        }
    }

    /** Radio de una capa en una muestra: la mitad del ancho, más el escape, más el muro. */
    private static float radio(Circuito.Geometria g, int i, int capa) {
        float medio = g.anchos()[i] / 2f;
        return capa == 2 ? medio : capa == 1 ? medio + g.escapes()[i] : medio + g.escapes()[i] + g.muro();
    }

    private List<Bloque> armarCapa(int capa) {
        Circuito.Geometria g = circuito.geometria();
        List<Bloque> bloques = new ArrayList<>();
        for (int desde = 0; desde < g.muestras(); desde += PUNTOS_POR_BLOQUE) {
            int hasta = Math.min(g.muestras(), desde + PUNTOS_POR_BLOQUE);
            int cantidad = hasta - desde;
            float[] vertices = new float[cantidad * (LADOS + 1) * 2];
            short[] triangulos = new short[cantidad * LADOS * 3];
            float izquierda = Float.MAX_VALUE;
            float abajo = Float.MAX_VALUE;
            float derecha = -Float.MAX_VALUE;
            float arriba = -Float.MAX_VALUE;
            int v = 0;
            int t = 0;
            for (int i = desde; i < hasta; i++) {
                float cx = g.linea()[2 * i];
                float cy = g.linea()[2 * i + 1];
                float r = radio(g, i, capa);
                izquierda = Math.min(izquierda, cx - r);
                abajo = Math.min(abajo, cy - r);
                derecha = Math.max(derecha, cx + r);
                arriba = Math.max(arriba, cy + r);
                int centro = v / 2;
                vertices[v++] = cx;
                vertices[v++] = cy;
                for (int k = 0; k < LADOS; k++) {
                    double angulo = 2 * Math.PI * k / LADOS;
                    vertices[v++] = cx + (float) Math.cos(angulo) * r;
                    vertices[v++] = cy + (float) Math.sin(angulo) * r;
                }
                for (int k = 0; k < LADOS; k++) {
                    triangulos[t++] = (short) centro;
                    triangulos[t++] = (short) (centro + 1 + k);
                    triangulos[t++] = (short) (centro + 1 + (k + 1) % LADOS);
                }
            }
            bloques.add(new Bloque(new PolygonRegion(blanco, vertices, triangulos), izquierda, abajo, derecha, arriba));
        }
        return bloques;
    }

    public void dibujar(OrthographicCamera camara) {
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;
        float izquierda = camara.position.x - mitadAncho;
        float derecha = camara.position.x + mitadAncho;
        float abajo = camara.position.y - mitadAlto;
        float arriba = camara.position.y + mitadAlto;

        batch.setProjectionMatrix(camara.combined);
        batch.begin();
        batch.setColor(Paleta.PISTA_FONDO);
        batch.draw(blanco, izquierda, abajo, mitadAncho * 2f, mitadAlto * 2f);
        for (int capa = 0; capa < 3; capa++) {
            batch.setColor(capa == 0 ? Paleta.PISTA_MURO : capa == 1 ? Paleta.PISTA_PASTO : Paleta.PISTA_ASFALTO);
            for (Bloque bloque : capas.get(capa)) {
                if (bloque.derecha() >= izquierda && bloque.izquierda() <= derecha
                    && bloque.arriba() >= abajo && bloque.abajo() <= arriba) {
                    batch.draw(bloque.region(), 0f, 0f);
                }
            }
        }
        meta();
        batch.end();
    }

    /** La línea de meta: una tira blanca cruzando la pista en la muestra 0. */
    private void meta() {
        Circuito.Geometria g = circuito.geometria();
        float x = g.linea()[0];
        float y = g.linea()[1];
        float x2 = g.linea()[2];
        float y2 = g.linea()[3];
        float largo = g.anchos()[0];
        // La tira va a lo ancho de la pista: su lado largo es perpendicular al sentido de la carrera.
        float grados = (float) Math.toDegrees(Math.atan2(y2 - y, x2 - x));
        batch.setColor(Paleta.PISTA_META);
        batch.draw(blanco, x - GROSOR_META / 2f, y - largo / 2f, GROSOR_META / 2f, largo / 2f, GROSOR_META, largo, 1f, 1f, grados);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
