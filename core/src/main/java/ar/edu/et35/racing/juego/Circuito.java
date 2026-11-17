package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.List;

public class Circuito {
    public record Cruce(int tunel, int puente) {
    }

    public record Geometria(float[] linea, float[] anchos, float[] escapes, float muro, List<Cruce> cruces, boolean callejero) {
        public int muestras() {
            return anchos.length;
        }
    }

    private final float ancho;
    private final float alto;
    private final float tamCelda;
    private final int columnas;
    private final int filas;
    private final byte[] celdas;
    private final Geometria geometria;
    private final List<Rectangle> checkpoints;
    private final List<Vector2> posicionesLargada;
    private final float anguloLargada;
    private final Vector2[] sentidos;
    private final List<ZonaCruce> zonasCruce;

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

    private static Vector2[] calcularSentidos(List<Rectangle> checkpoints) {
        Vector2[] sentidos = new Vector2[checkpoints.size()];
        for (int i = 0; i < sentidos.length; i++) {
            Vector2 actual = checkpoints.get(i).getCenter(new Vector2());
            Vector2 siguiente = checkpoints.get((i + 1) % sentidos.length).getCenter(new Vector2());
            sentidos[i] = siguiente.sub(actual).nor();
        }
        return sentidos;
    }

    public Vector2 sentidoCheckpoint(int indice) {
        return sentidos[indice];
    }

    public float ancho() {
        return ancho;
    }

    public float alto() {
        return alto;
    }

    public Geometria geometria() {
        return geometria;
    }

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

    public Superficie superficieEn(float x, float y) {
        return superficieDeCelda(MathUtils.floor(x / tamCelda), MathUtils.floor(y / tamCelda));
    }

    private Superficie superficieDeCelda(int columna, int fila) {
        if (columna < 0 || fila < 0 || columna >= columnas || fila >= filas) {
            return Superficie.MURO;
        }
        return Superficie.values()[celdas[fila * columnas + columna]];
    }

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
