package ar.edu.et35.racing.juego;

import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * Lee un circuito en formato JSON (docs/FORMATO_CIRCUITO.md) y lo convierte en un {@link Circuito}: guarda la
 * geometría para dibujarla y rasteriza una grilla fina de superficies para la colisión. Solo lee datos (no crea
 * texturas), pero igual se lo llama desde el hilo de render para no tocar LibGDX desde los hilos de red.
 */
public final class CargadorCircuito {
    /** Versión del formato que entiende este cargador. */
    private static final int VERSION = 1;

    private CargadorCircuito() {
    }

    public static Circuito cargar(FileHandle archivo, int cantidadLargadas) {
        return cargarDesdeTexto(archivo.readString("UTF-8"), cantidadLargadas);
    }

    public static Circuito cargarDesdeTexto(String texto, int cantidadLargadas) {
        JsonValue raiz = new JsonReader().parse(texto);
        int version = raiz.getInt("version", -1);
        if (version != VERSION) {
            throw new IllegalArgumentException("Versión de circuito no soportada: " + version + " (se espera " + VERSION + ")");
        }
        float ancho = raiz.getFloat("ancho");
        float alto = raiz.getFloat("alto");
        float anguloLargada = raiz.getFloat("anguloLargada");

        Circuito.Geometria geometria = leerGeometria(raiz);
        byte[] celdas = rasterizar(ancho, alto, Config.CELDA_COLISION, geometria);
        return new Circuito(ancho, alto, Config.CELDA_COLISION, celdas, geometria, leerCheckpoints(raiz),
            leerLargada(raiz, cantidadLargadas), anguloLargada);
    }

    private static Circuito.Geometria leerGeometria(JsonValue raiz) {
        JsonValue lineaJson = campo(raiz, "linea");
        int muestras = lineaJson.size;
        if (muestras < 3) {
            throw new IllegalArgumentException("La línea central del circuito necesita al menos 3 muestras");
        }
        float[] linea = new float[muestras * 2];
        int i = 0;
        for (JsonValue punto = lineaJson.child; punto != null; punto = punto.next) {
            linea[i++] = punto.getFloat(0);
            linea[i++] = punto.getFloat(1);
        }
        float[] anchos = leerNumeros(campo(raiz, "anchos"), muestras, "anchos");
        float[] escapes = leerNumeros(campo(raiz, "escapes"), muestras, "escapes");

        List<Circuito.Cruce> cruces = new ArrayList<>();
        for (JsonValue cruce = campo(raiz, "cruces").child; cruce != null; cruce = cruce.next) {
            int tunel = cruce.getInt("tunel");
            int puente = cruce.getInt("puente");
            if (tunel < 0 || tunel >= muestras || puente < 0 || puente >= muestras) {
                throw new IllegalArgumentException("Un cruce apunta a una muestra que no existe");
            }
            cruces.add(new Circuito.Cruce(tunel, puente));
        }
        return new Circuito.Geometria(linea, anchos, escapes, raiz.getFloat("muro"), List.copyOf(cruces),
            raiz.getBoolean("callejero", false));
    }

    private static JsonValue campo(JsonValue raiz, String nombre) {
        JsonValue valor = raiz.get(nombre);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el campo '" + nombre + "' en el circuito");
        }
        return valor;
    }

    private static float[] leerNumeros(JsonValue arreglo, int esperados, String nombre) {
        if (arreglo.size != esperados) {
            throw new IllegalArgumentException("'" + nombre + "' tiene " + arreglo.size + " valores y la línea tiene " + esperados);
        }
        float[] numeros = new float[esperados];
        int i = 0;
        for (JsonValue valor = arreglo.child; valor != null; valor = valor.next) {
            numeros[i++] = valor.asFloat();
        }
        return numeros;
    }

    /**
     * Marca en una grilla de celdas de {@code tam} px qué es pista, pasto y muro. Cada muestra de la línea central
     * pinta un círculo: de radio la mitad del ancho es pista, y hasta sumarle el escape es pasto. Todo lo demás es
     * muro, también hacia afuera: la línea de muro que se dibuja es fina (unos 6 px) y un auto rápido la
     * atravesaría de un salto, así que lo sólido es todo lo que queda más allá del pasto. Donde dos tramos de pista
     * están cerca, el generador ya acortó el pasto para que quede muro entre los dos.
     */
    private static byte[] rasterizar(float ancho, float alto, float tam, Circuito.Geometria g) {
        int columnas = (int) Math.ceil(ancho / tam);
        int filas = (int) Math.ceil(alto / tam);
        byte[] celdas = new byte[columnas * filas];
        Arrays.fill(celdas, (byte) Superficie.MURO.ordinal());
        byte pista = (byte) Superficie.PISTA.ordinal();
        byte pasto = (byte) Superficie.PASTO.ordinal();

        for (int i = 0; i < g.muestras(); i++) {
            float x = g.linea()[2 * i];
            float y = g.linea()[2 * i + 1];
            float medio = g.anchos()[i] / 2f;
            float alcance = medio + g.escapes()[i];
            int desdeColumna = Math.max(0, (int) Math.floor((x - alcance) / tam));
            int hastaColumna = Math.min(columnas - 1, (int) Math.floor((x + alcance) / tam));
            int desdeFila = Math.max(0, (int) Math.floor((y - alcance) / tam));
            int hastaFila = Math.min(filas - 1, (int) Math.floor((y + alcance) / tam));
            for (int fila = desdeFila; fila <= hastaFila; fila++) {
                float dy = (fila + 0.5f) * tam - y;
                for (int columna = desdeColumna; columna <= hastaColumna; columna++) {
                    float dx = (columna + 0.5f) * tam - x;
                    float d2 = dx * dx + dy * dy;
                    int indice = fila * columnas + columna;
                    if (d2 <= medio * medio) {
                        celdas[indice] = pista;
                    } else if (d2 <= alcance * alcance && celdas[indice] != pista) {
                        celdas[indice] = pasto;
                    }
                }
            }
        }
        return celdas;
    }

    private static List<Rectangle> leerCheckpoints(JsonValue raiz) {
        TreeMap<Integer, Rectangle> porOrden = new TreeMap<>();
        for (JsonValue punto = campo(raiz, "checkpoints").child; punto != null; punto = punto.next) {
            int orden = punto.getInt("orden");
            Rectangle rectangulo = new Rectangle(punto.getFloat("x"), punto.getFloat("y"),
                punto.getFloat("ancho"), punto.getFloat("alto"));
            if (porOrden.put(orden, rectangulo) != null) {
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

    private static List<Vector2> leerLargada(JsonValue raiz, int cantidad) {
        Vector2[] posiciones = new Vector2[cantidad];
        for (JsonValue lugar = campo(raiz, "largada").child; lugar != null; lugar = lugar.next) {
            int posicion = lugar.getInt("posicion");
            if (posicion < 1 || posicion > cantidad) {
                throw new IllegalArgumentException("Cada lugar de largada debe tener 'posicion' de 1 a " + cantidad);
            }
            posiciones[posicion - 1] = new Vector2(lugar.getFloat("x"), lugar.getFloat("y"));
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
