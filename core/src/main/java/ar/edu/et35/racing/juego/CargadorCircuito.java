package ar.edu.et35.racing.juego;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Convierte un TiledMap en un {@link Circuito}. Espera: capa de tiles "suelo" (cada tile con la
 * propiedad "superficie"), capa de objetos "checkpoints" (rectángulos con "orden", 0 = meta), capa de
 * objetos "largada" (rectángulos con "posicion" de 1 a MAX_JUGADORES) y propiedad del mapa "anguloLargada".
 */
public final class CargadorCircuito {
    private static final String CAPA_SUELO = "suelo";
    private static final String CAPA_CHECKPOINTS = "checkpoints";
    private static final String CAPA_LARGADA = "largada";

    private CargadorCircuito() {
    }

    public static Circuito cargar(TiledMap mapa, int cantidadLargadas) {
        TiledMapTileLayer suelo = (TiledMapTileLayer) capa(mapa, CAPA_SUELO);
        Superficie[][] superficies = leerSuperficies(suelo);
        float tamTile = suelo.getTileWidth();

        List<Rectangle> checkpoints = leerCheckpoints(capa(mapa, CAPA_CHECKPOINTS));
        List<Vector2> largada = leerLargada(capa(mapa, CAPA_LARGADA), cantidadLargadas);

        Float angulo = mapa.getProperties().get("anguloLargada", Float.class);
        if (angulo == null) {
            throw new IllegalArgumentException("El mapa no tiene la propiedad 'anguloLargada'");
        }
        return new Circuito(tamTile, superficies, checkpoints, largada, angulo);
    }

    private static MapLayer capa(TiledMap mapa, String nombre) {
        MapLayer capa = mapa.getLayers().get(nombre);
        if (capa == null) {
            throw new IllegalArgumentException("Falta la capa '" + nombre + "' en el mapa");
        }
        return capa;
    }

    private static Superficie[][] leerSuperficies(TiledMapTileLayer suelo) {
        Superficie[][] superficies = new Superficie[suelo.getWidth()][suelo.getHeight()];
        for (int columna = 0; columna < suelo.getWidth(); columna++) {
            for (int fila = 0; fila < suelo.getHeight(); fila++) {
                TiledMapTileLayer.Cell celda = suelo.getCell(columna, fila);
                if (celda == null) {
                    // Una celda vacía es un hueco del mapa: se trata como muro para no dejar salir al auto.
                    superficies[columna][fila] = Superficie.MURO;
                } else {
                    String texto = celda.getTile().getProperties().get("superficie", String.class);
                    superficies[columna][fila] = Superficie.desdeTexto(texto);
                }
            }
        }
        return superficies;
    }

    private static List<Rectangle> leerCheckpoints(MapLayer capa) {
        TreeMap<Integer, Rectangle> porOrden = new TreeMap<>();
        for (MapObject objeto : capa.getObjects()) {
            Integer orden = objeto.getProperties().get("orden", Integer.class);
            if (!(objeto instanceof RectangleMapObject) || orden == null) {
                throw new IllegalArgumentException("Cada checkpoint debe ser un rectángulo con la propiedad 'orden'");
            }
            if (porOrden.put(orden, new Rectangle(((RectangleMapObject) objeto).getRectangle())) != null) {
                throw new IllegalArgumentException("Checkpoint repetido: orden " + orden);
            }
        }
        List<Rectangle> lista = new ArrayList<>(porOrden.values());
        for (int i = 0; i < lista.size(); i++) {
            if (!porOrden.containsKey(i)) {
                throw new IllegalArgumentException("Los checkpoints deben ir de 0 a N-1 sin saltos; falta el " + i);
            }
        }
        if (lista.isEmpty()) {
            throw new IllegalArgumentException("El circuito no tiene checkpoints");
        }
        return lista;
    }

    private static List<Vector2> leerLargada(MapLayer capa, int cantidad) {
        Vector2[] posiciones = new Vector2[cantidad];
        for (MapObject objeto : capa.getObjects()) {
            Integer posicion = objeto.getProperties().get("posicion", Integer.class);
            if (!(objeto instanceof RectangleMapObject) || posicion == null || posicion < 1 || posicion > cantidad) {
                throw new IllegalArgumentException("Cada lugar de largada debe ser un rectángulo con 'posicion' de 1 a " + cantidad);
            }
            Vector2 centro = ((RectangleMapObject) objeto).getRectangle().getCenter(new Vector2());
            posiciones[posicion - 1] = centro;
        }
        List<Vector2> lista = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            if (posiciones[i] == null) {
                throw new IllegalArgumentException("Falta el lugar de largada " + (i + 1));
            }
            lista.add(posiciones[i]);
        }
        return lista;
    }
}
