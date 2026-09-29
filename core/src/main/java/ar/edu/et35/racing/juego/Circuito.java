package ar.edu.et35.racing.juego;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.List;

/**
 * Datos puros de un circuito: grilla de superficies, checkpoints, posiciones y ángulo de largada.
 * No usa clases gráficas porque lo simula también el servidor. Las coordenadas son de mundo (píxeles)
 * con el eje Y hacia arriba; el origen es la esquina inferior izquierda del mapa.
 */
public class Circuito {
    private final int columnas;
    private final int filas;
    private final float tamTile;
    private final Superficie[][] superficies; // [columna][fila]
    private final List<Rectangle> checkpoints; // el índice es el orden; el 0 es la meta
    private final List<Vector2> posicionesLargada; // el índice 0 es la posición 1
    private final float anguloLargada; // grados: 0 = derecha, 90 = arriba

    public Circuito(float tamTile, Superficie[][] superficies, List<Rectangle> checkpoints,
                    List<Vector2> posicionesLargada, float anguloLargada) {
        this.tamTile = tamTile;
        this.superficies = superficies;
        this.columnas = superficies.length;
        this.filas = superficies[0].length;
        this.checkpoints = List.copyOf(checkpoints);
        this.posicionesLargada = List.copyOf(posicionesLargada);
        this.anguloLargada = anguloLargada;
    }

    public float ancho() {
        return columnas * tamTile;
    }

    public float alto() {
        return filas * tamTile;
    }

    public float tamTile() {
        return tamTile;
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
        return superficieDeTile(MathUtils.floor(x / tamTile), MathUtils.floor(y / tamTile));
    }

    private Superficie superficieDeTile(int columna, int fila) {
        if (columna < 0 || fila < 0 || columna >= columnas || fila >= filas) {
            return Superficie.MURO;
        }
        return superficies[columna][fila];
    }

    /** Indica si un círculo (centro y radio) toca algún tile de muro. */
    public boolean chocaConMuro(float x, float y, float radio) {
        int desdeColumna = MathUtils.floor((x - radio) / tamTile);
        int hastaColumna = MathUtils.floor((x + radio) / tamTile);
        int desdeFila = MathUtils.floor((y - radio) / tamTile);
        int hastaFila = MathUtils.floor((y + radio) / tamTile);
        for (int columna = desdeColumna; columna <= hastaColumna; columna++) {
            for (int fila = desdeFila; fila <= hastaFila; fila++) {
                if (superficieDeTile(columna, fila) != Superficie.MURO) {
                    continue;
                }
                // Punto del tile más cercano al centro del círculo
                float cercanoX = MathUtils.clamp(x, columna * tamTile, (columna + 1) * tamTile);
                float cercanoY = MathUtils.clamp(y, fila * tamTile, (fila + 1) * tamTile);
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
