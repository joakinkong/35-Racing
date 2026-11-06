package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.List;

/**
 * Datos puros de un circuito: la geometría de la pista (para dibujarla), una grilla fina de superficies (para la
 * colisión), checkpoints, posiciones y ángulo de largada. No usa clases gráficas porque lo simula también el
 * servidor. Las coordenadas son de mundo (píxeles) con el eje Y hacia arriba; el origen es la esquina inferior
 * izquierda del mapa. El formato de origen está en docs/FORMATO_CIRCUITO.md.
 */
public class Circuito {
    /** Un lugar donde la pista pasa por encima de sí misma: índices de {@link Geometria#linea()}. */
    public record Cruce(int tunel, int puente) {
    }

    /**
     * La pista como la describe el archivo: línea central cerrada ({@code linea} = x0, y0, x1, y1, ...), ancho total
     * de la pista y pasto de escape a cada lado en cada muestra, y grosor de la línea de muro (solo para dibujar).
     * Los arreglos no se deben modificar.
     */
    public record Geometria(float[] linea, float[] anchos, float[] escapes, float muro, List<Cruce> cruces) {
        public int muestras() {
            return anchos.length;
        }
    }

    private final float ancho;
    private final float alto;
    private final float tamCelda;
    private final int columnas;
    private final int filas;
    private final byte[] celdas; // ordinal de Superficie; índice = fila * columnas + columna
    private final Geometria geometria;
    private final List<Rectangle> checkpoints; // el índice es el orden; el 0 es la meta
    private final List<Vector2> posicionesLargada; // el índice 0 es la posición 1
    private final float anguloLargada; // grados: 0 = derecha, 90 = arriba
    private final Vector2[] sentidos;
    private final List<ZonaCruce> zonasCruce;

    /**
     * @param celdas superficie de cada celda de la grilla de colisión (ordinal de {@link Superficie}), por filas desde abajo;
     *               la grilla cubre el mapa entero con celdas de {@code tamCelda} px
     */
    public Circuito(float ancho, float alto, float tamCelda, byte[] celdas, Geometria geometria,
                    List<Rectangle> checkpoints, List<Vector2> posicionesLargada, float anguloLargada) {
        this.ancho = ancho;
        this.alto = alto;
        this.tamCelda = tamCelda;
        this.columnas = MathUtils.ceil(ancho / tamCelda);
        this.filas = MathUtils.ceil(alto / tamCelda);
        if (celdas.length != columnas * filas) {
            throw new IllegalArgumentException("La grilla de colisión no coincide con el tamaño del mapa");
        }
        this.celdas = celdas;
        this.geometria = geometria;
        this.checkpoints = List.copyOf(checkpoints);
        this.posicionesLargada = List.copyOf(posicionesLargada);
        this.anguloLargada = anguloLargada;
        this.sentidos = calcularSentidos(this.checkpoints);
        this.zonasCruce = ZonaCruce.de(geometria);
    }

    /** Sentido de la pista en cada checkpoint: apunta desde su centro hacia el centro del siguiente. */
    private static Vector2[] calcularSentidos(List<Rectangle> checkpoints) {
        Vector2[] sentidos = new Vector2[checkpoints.size()];
        for (int i = 0; i < sentidos.length; i++) {
            Vector2 actual = checkpoints.get(i).getCenter(new Vector2());
            Vector2 siguiente = checkpoints.get((i + 1) % sentidos.length).getCenter(new Vector2());
            sentidos[i] = siguiente.sub(actual).nor();
        }
        return sentidos;
    }

    /** Vector unitario con el sentido en que se recorre la pista al cruzar ese checkpoint. No modificarlo. */
    public Vector2 sentidoCheckpoint(int indice) {
        return sentidos[indice];
    }

    public float ancho() {
        return ancho;
    }

    public float alto() {
        return alto;
    }

    /** La pista como línea central con anchos, para dibujarla. */
    public Geometria geometria() {
        return geometria;
    }

    /** Una zona por cada lugar donde la pista pasa por encima de sí misma; vacía en casi todos los circuitos. */
    public List<ZonaCruce> zonasCruce() {
        return zonasCruce;
    }

    public List<Rectangle> checkpoints() {
        return checkpoints;
    }

    public List<Vector2> posicionesLargada() {
        return posicionesLargada;
    }

    public float anguloLargada() {
        return anguloLargada;
    }

    /** Superficie en un punto del mundo. Fuera del mapa cuenta como muro. */
    public Superficie superficieEn(float x, float y) {
        return superficieDeCelda(MathUtils.floor(x / tamCelda), MathUtils.floor(y / tamCelda));
    }

    private Superficie superficieDeCelda(int columna, int fila) {
        if (columna < 0 || fila < 0 || columna >= columnas || fila >= filas) {
            return Superficie.MURO;
        }
        return Superficie.values()[celdas[fila * columnas + columna]];
    }

    /** Indica si un círculo (centro y radio) toca alguna celda de muro. */
    public boolean chocaConMuro(float x, float y, float radio) {
        int desdeColumna = MathUtils.floor((x - radio) / tamCelda);
        int hastaColumna = MathUtils.floor((x + radio) / tamCelda);
        int desdeFila = MathUtils.floor((y - radio) / tamCelda);
        int hastaFila = MathUtils.floor((y + radio) / tamCelda);
        for (int columna = desdeColumna; columna <= hastaColumna; columna++) {
            for (int fila = desdeFila; fila <= hastaFila; fila++) {
                if (superficieDeCelda(columna, fila) != Superficie.MURO) {
                    continue;
                }
                // Punto de la celda más cercano al centro del círculo
                float cercanoX = MathUtils.clamp(x, columna * tamCelda, (columna + 1) * tamCelda);
                float cercanoY = MathUtils.clamp(y, fila * tamCelda, (fila + 1) * tamCelda);
                float dx = x - cercanoX;
                float dy = y - cercanoY;
                if (dx * dx + dy * dy < radio * radio) {
                    return true;
                }
            }
        }
        return false;
    }
}
