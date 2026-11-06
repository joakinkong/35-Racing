package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dibuja lo que hay alrededor de la pista: el suelo que se repite y las piezas que ubica {@link DisposicionEntorno}. Las
 * piezas se agrupan por zonas para mirar solo las que entran en la cámara: en los circuitos grandes hay miles.
 */
public class VistaEntorno implements Disposable {
    private static final String CARPETA = "entorno/";
    /** Lado de una zona (px): más que la pieza más grande, así alcanza con mirar una zona más a cada lado de la cámara. */
    private static final float ZONA = 512f;
    /** Lado con que se dibuja una baldosa de 48 px, a la misma escala que los sprites (px de mundo). */
    private static final float BALDOSA = 48f * DisposicionEntorno.ESCALA / 2f;
    private static final float LARGO_LINEA_CALLE = 20f;
    private static final float ESPACIO_LINEA_CALLE = 20f;
    private static final float GROSOR_LINEA_CALLE = 2f;
    private static final Color COLOR_LINEA_CALLE = new Color(0.85f, 0.83f, 0.76f, 1f);

    /** Colores de la ropa del público. */
    private static final Color[] ROPA = {
        Color.valueOf("E8002D"), Color.valueOf("FFEB00"), Color.valueOf("3671C6"), Color.valueOf("FF8700"),
        Color.valueOf("27F4D2"), Color.valueOf("F2F2F2"), Color.valueOf("23262B"), Color.valueOf("C94BCB")};
    /** Distancia entre personas y fracción de lugares con alguien sentado. */
    private static final float ESPACIO_PUBLICO = 5f;
    private static final float TAMANO_PERSONA = 3f;
    private static final float OCUPACION_TRIBUNA = 0.8f;
    private static final int FILAS_TRIBUNA = 4;

    private record Dibujo(TextureRegion region, Color color, float x, float y, float ancho, float alto) {
    }

    private final TextureAtlas atlas;
    private final Texture suelo;
    private final Texture asfalto;
    private final TextureRegion blanco;
    private final List<DisposicionEntorno.Calle> calles;
    private final List<List<Dibujo>> zonas = new ArrayList<>();
    private final int columnas;
    private final int filas;

    /** @param blanco región de una textura blanca de 1 x 1; no la libera esta clase */
    public VistaEntorno(Circuito circuito, TextureRegion blanco) {
        this.blanco = blanco;
        atlas = new TextureAtlas(Gdx.files.internal(CARPETA + "entorno.atlas"));
        suelo = baldosa(circuito.geometria().callejero() ? "fondo_vereda.png" : "fondo_pasto.png");
        asfalto = baldosa("fondo_calle.png");

        Map<String, float[]> tamanos = new HashMap<>();
        Map<String, TextureRegion> regiones = new HashMap<>();
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            tamanos.put(region.name, new float[] {region.getRegionWidth(), region.getRegionHeight()});
            regiones.put(region.name, region);
        }
        DisposicionEntorno disposicion = new DisposicionEntorno(circuito, tamanos);
        calles = disposicion.calles();

        columnas = (int) (circuito.ancho() / ZONA) + 1;
        filas = (int) (circuito.alto() / ZONA) + 1;
        for (int i = 0; i < columnas * filas; i++) {
            zonas.add(new ArrayList<>());
        }
        for (DisposicionEntorno.Pieza p : disposicion.piezas()) {
            int zona = (int) (p.y() / ZONA) * columnas + (int) (p.x() / ZONA);
            zonas.get(zona).add(new Dibujo(regiones.get(p.nombre()), Color.WHITE, p.x(), p.y(), p.ancho(), p.alto()));
            if (p.nombre().startsWith("tribuna_")) {
                agregarPublico(zonas.get(zona), p);
            }
        }
    }

    /** La gente sentada en una tribuna: filas a lo largo de los bancos, con la misma gente en cada carrera. */
    private void agregarPublico(List<Dibujo> zona, DisposicionEntorno.Pieza p) {
        Random azar = new Random(Float.floatToIntBits(p.x()) * 31L + Float.floatToIntBits(p.y()));
        boolean acostada = p.ancho() > p.alto();
        float largo = acostada ? p.ancho() : p.alto();
        float ancho = acostada ? p.alto() : p.ancho();
        for (int fila = 0; fila < FILAS_TRIBUNA; fila++) {
            float enAncho = ancho * (fila + 0.5f) / FILAS_TRIBUNA;
            for (float enLargo = ESPACIO_PUBLICO / 2f; enLargo < largo - TAMANO_PERSONA; enLargo += ESPACIO_PUBLICO) {
                if (azar.nextFloat() < OCUPACION_TRIBUNA) {
                    float x = p.x() + (acostada ? enLargo : enAncho) - TAMANO_PERSONA / 2f;
                    float y = p.y() + (acostada ? enAncho : enLargo) - TAMANO_PERSONA / 2f;
                    zona.add(new Dibujo(blanco, ROPA[azar.nextInt(ROPA.length)], x, y, TAMANO_PERSONA, TAMANO_PERSONA));
                }
            }
        }
    }

    private static Texture baldosa(String archivo) {
        Texture textura = new Texture(Gdx.files.internal(CARPETA + archivo));
        textura.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        textura.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return textura;
    }

    /** Dibuja lo que entra en la cámara. El batch ya tiene que estar abierto. */
    public void dibujar(PolygonSpriteBatch batch, OrthographicCamera camara) {
        float mitadAncho = camara.viewportWidth * camara.zoom / 2f;
        float mitadAlto = camara.viewportHeight * camara.zoom / 2f;
        float izquierda = camara.position.x - mitadAncho;
        float abajo = camara.position.y - mitadAlto;
        float derecha = camara.position.x + mitadAncho;
        float arriba = camara.position.y + mitadAlto;

        batch.setColor(Color.WHITE);
        repetir(batch, suelo, izquierda, abajo, derecha, arriba);
        for (DisposicionEntorno.Calle c : calles) {
            float x1 = Math.max(c.x(), izquierda);
            float y1 = Math.max(c.y(), abajo);
            float x2 = Math.min(c.x() + c.ancho(), derecha);
            float y2 = Math.min(c.y() + c.alto(), arriba);
            if (x1 < x2 && y1 < y2) {
                repetir(batch, asfalto, x1, y1, x2, y2);
                lineaDeCalle(batch, c, c.horizontal() ? x1 : y1, c.horizontal() ? x2 : y2);
            }
        }
        batch.setColor(Color.WHITE);
        int desdeColumna = Math.max(0, (int) (izquierda / ZONA) - 1);
        int hastaColumna = Math.min(columnas - 1, (int) (derecha / ZONA));
        int desdeFila = Math.max(0, (int) (abajo / ZONA) - 1);
        int hastaFila = Math.min(filas - 1, (int) (arriba / ZONA));
        for (int fila = desdeFila; fila <= hastaFila; fila++) {
            for (int columna = desdeColumna; columna <= hastaColumna; columna++) {
                for (Dibujo d : zonas.get(fila * columnas + columna)) {
                    if (d.x() < derecha && d.x() + d.ancho() > izquierda && d.y() < arriba && d.y() + d.alto() > abajo) {
                        batch.setColor(d.color());
                        batch.draw(d.region(), d.x(), d.y(), d.ancho(), d.alto());
                    }
                }
            }
        }
    }

    /** Llena un rectángulo con una baldosa, alineada con el mundo para que no se mueva con la cámara. */
    private static void repetir(PolygonSpriteBatch batch, Texture textura, float x1, float y1, float x2, float y2) {
        batch.draw(textura, x1, y1, x2 - x1, y2 - y1, x1 / BALDOSA, y1 / BALDOSA, x2 / BALDOSA, y2 / BALDOSA);
    }

    private void lineaDeCalle(PolygonSpriteBatch batch, DisposicionEntorno.Calle c, float desde, float hasta) {
        float paso = LARGO_LINEA_CALLE + ESPACIO_LINEA_CALLE;
        batch.setColor(COLOR_LINEA_CALLE);
        for (float p = (float) Math.floor(desde / paso) * paso; p < hasta; p += paso) {
            if (c.horizontal()) {
                batch.draw(blanco, p, c.y() + c.alto() / 2f - GROSOR_LINEA_CALLE / 2f, LARGO_LINEA_CALLE, GROSOR_LINEA_CALLE);
            } else {
                batch.draw(blanco, c.x() + c.ancho() / 2f - GROSOR_LINEA_CALLE / 2f, p, GROSOR_LINEA_CALLE, LARGO_LINEA_CALLE);
            }
        }
        batch.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        atlas.dispose();
        suelo.dispose();
        asfalto.dispose();
    }
}
