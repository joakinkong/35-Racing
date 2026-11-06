package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.ZonaCruce;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.graphics.Color;
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
 * muro queda pegado al asfalto. Encima van los pianos ({@link Pianos}) y la meta. Los círculos se arman una sola vez,
 * agrupados en bloques de puntos seguidos para dibujar solo los que entran en la cámara.
 *
 * Los puentes se dibujan aparte ({@link #dibujarPuentes}), porque entre la pista y el puente van los autos de abajo.
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
    private static final int CAPAS = 3;

    /** Un grupo de polígonos de un color, con el rectángulo que los contiene. */
    private record Bloque(PolygonRegion region, float izquierda, float abajo, float derecha, float arriba) {
    }

    private final Circuito circuito;
    private final TextureRegion blanco;
    private final PolygonSpriteBatch batch = new PolygonSpriteBatch(MAX_VERTICES_BATCH);
    /** Bloques por capa: 0 = muro, 1 = pasto, 2 = asfalto. */
    private final List<List<Bloque>> capas = new ArrayList<>();
    private final List<Bloque> murosPuentes = new ArrayList<>();
    private final List<Bloque> asfaltoPuentes = new ArrayList<>();
    private final List<Bloque> pianosRojos = new ArrayList<>();
    private final List<Bloque> pianosBlancos = new ArrayList<>();

    /** @param blanco región de una textura blanca de 1 x 1; no la libera esta clase */
    public VistaCircuito(Circuito circuito, TextureRegion blanco) {
        this.circuito = circuito;
        this.blanco = blanco;
        for (int capa = 0; capa < CAPAS; capa++) {
            capas.add(armarCapa(capa));
        }
        for (ZonaCruce zona : circuito.zonasCruce()) {
            murosPuentes.add(armarCinta(zona.muestrasPuente(), circuito.geometria().muro()));
            asfaltoPuentes.add(armarCinta(zona.muestrasPuente(), 0f));
        }
        for (Pianos.Franjas franjas : Pianos.calcular(circuito)) {
            (franjas.rojas() ? pianosRojos : pianosBlancos).add(bloque(franjas.vertices(), franjas.triangulos()));
        }
    }

    /** Radio de una capa en una muestra: la mitad del ancho, más el escape, más el muro. */
    private static float radio(Circuito.Geometria g, int i, int capa) {
        float medio = g.anchos()[i] / 2f;
        return capa == 2 ? medio : capa == 1 ? medio + g.escapes()[i] : medio + g.escapes()[i] + g.muro();
    }

    /**
     * Una cinta a lo largo de esas muestras, de la mitad del ancho de la pista más {@code extra} a cada lado. A diferencia
     * de los círculos, termina en un corte recto: los extremos del puente no pisan el pasto del tramo que sigue.
     */
    private Bloque armarCinta(int[] muestras, float extra) {
        Circuito.Geometria g = circuito.geometria();
        int n = g.muestras();
        float[] l = g.linea();
        float[] vertices = new float[muestras.length * 4];
        short[] triangulos = new short[(muestras.length - 1) * 6];
        for (int m = 0; m < muestras.length; m++) {
            int i = muestras[m];
            int a = Math.floorMod(i - 2, n);
            int b = (i + 2) % n;
            float tx = l[2 * b] - l[2 * a];
            float ty = l[2 * b + 1] - l[2 * a + 1];
            float largo = (float) Math.hypot(tx, ty);
            float mitad = g.anchos()[i] / 2f + extra;
            vertices[4 * m] = l[2 * i] - ty / largo * mitad;
            vertices[4 * m + 1] = l[2 * i + 1] + tx / largo * mitad;
            vertices[4 * m + 2] = l[2 * i] + ty / largo * mitad;
            vertices[4 * m + 3] = l[2 * i + 1] - tx / largo * mitad;
            if (m > 0) {
                int t = (m - 1) * 6;
                int base = (m - 1) * 2;
                triangulos[t] = (short) base;
                triangulos[t + 1] = (short) (base + 1);
                triangulos[t + 2] = (short) (base + 3);
                triangulos[t + 3] = (short) base;
                triangulos[t + 4] = (short) (base + 3);
                triangulos[t + 5] = (short) (base + 2);
            }
        }
        return bloque(vertices, triangulos);
    }

    /** Los círculos de una capa, repartidos en bloques. */
    private List<Bloque> armarCapa(int capa) {
        Circuito.Geometria g = circuito.geometria();
        List<Bloque> bloques = new ArrayList<>();
        for (int desde = 0; desde < g.muestras(); desde += PUNTOS_POR_BLOQUE) {
            int hasta = Math.min(g.muestras(), desde + PUNTOS_POR_BLOQUE);
            int cantidad = hasta - desde;
            float[] vertices = new float[cantidad * (LADOS + 1) * 2];
            short[] triangulos = new short[cantidad * LADOS * 3];
            int v = 0;
            int t = 0;
            for (int i = desde; i < hasta; i++) {
                float cx = g.linea()[2 * i];
                float cy = g.linea()[2 * i + 1];
                float r = radio(g, i, capa);
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
            bloques.add(bloque(vertices, triangulos));
        }
        return bloques;
    }

    private Bloque bloque(float[] vertices, short[] triangulos) {
        float izquierda = Float.MAX_VALUE;
        float abajo = Float.MAX_VALUE;
        float derecha = -Float.MAX_VALUE;
        float arriba = -Float.MAX_VALUE;
        for (int v = 0; v < vertices.length; v += 2) {
            izquierda = Math.min(izquierda, vertices[v]);
            derecha = Math.max(derecha, vertices[v]);
            abajo = Math.min(abajo, vertices[v + 1]);
            arriba = Math.max(arriba, vertices[v + 1]);
        }
        return new Bloque(new PolygonRegion(blanco, vertices, triangulos), izquierda, abajo, derecha, arriba);
    }

    private static Color colorDeCapa(int capa) {
        return capa == 0 ? Paleta.PISTA_MURO : capa == 1 ? Paleta.PISTA_PASTO : Paleta.PISTA_ASFALTO;
    }

    /** Dibuja los bloques que entran en la cámara. */
    private void dibujarBloques(List<Bloque> bloques, Color color, OrthographicCamera camara) {
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;
        float izquierda = camara.position.x - mitadAncho;
        float derecha = camara.position.x + mitadAncho;
        float abajo = camara.position.y - mitadAlto;
        float arriba = camara.position.y + mitadAlto;
        batch.setColor(color);
        for (Bloque bloque : bloques) {
            if (bloque.derecha() >= izquierda && bloque.izquierda() <= derecha
                && bloque.arriba() >= abajo && bloque.abajo() <= arriba) {
                batch.draw(bloque.region(), 0f, 0f);
            }
        }
    }

    /** La pista, sin los puentes. */
    public void dibujar(OrthographicCamera camara) {
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;

        batch.setProjectionMatrix(camara.combined);
        batch.begin();
        batch.setColor(Paleta.PISTA_FONDO);
        batch.draw(blanco, camara.position.x - mitadAncho, camara.position.y - mitadAlto, mitadAncho * 2f, mitadAlto * 2f);
        for (int capa = 0; capa < CAPAS; capa++) {
            dibujarBloques(capas.get(capa), colorDeCapa(capa), camara);
        }
        dibujarBloques(pianosRojos, Paleta.PISTA_PIANO_ROJO, camara);
        dibujarBloques(pianosBlancos, Paleta.PISTA_PIANO_BLANCO, camara);
        meta();
        batch.end();
    }

    /** Los puentes: van después de dibujar los autos que pasan por debajo y antes de los demás. */
    public void dibujarPuentes(OrthographicCamera camara) {
        if (circuito.zonasCruce().isEmpty()) {
            return;
        }
        batch.setProjectionMatrix(camara.combined);
        batch.begin();
        dibujarBloques(murosPuentes, Paleta.PISTA_MURO, camara);
        dibujarBloques(asfaltoPuentes, Paleta.PISTA_ASFALTO, camara);
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
