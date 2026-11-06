package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Circuito;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Decide qué decoración va alrededor de un circuito y dónde. Es solo un cálculo, sin clases gráficas, y nunca pone nada a
 * menos de {@link #MARGEN_PISTA} px del muro, así que no cambia lo que se ve de la pista ni cómo se maneja. Depende solo de
 * la línea central: en todas las PC y en todas las carreras el mismo circuito queda igual.
 *
 * Los permanentes llevan pasto, tribunas en todos los bordes, ambulancias, neumáticos, vallas, muros y árboles. Los callejeros, una grilla de
 * calles con edificios, autos estacionados, faroles, carteles, barreras y tribunas donde entren.
 */
public final class DisposicionEntorno {
    /** Una decoración: esquina inferior izquierda y tamaño en px de mundo. */
    public record Pieza(String nombre, float x, float y, float ancho, float alto) {
    }

    /** Un rectángulo de calle, con la línea punteada a lo largo si {@code horizontal}. */
    public record Calle(float x, float y, float ancho, float alto, boolean horizontal) {
    }

    /** Px de mundo por px del atlas: el atlas está a la mitad del pack y el pack, a 0,35 de escala con el auto. */
    public static final float ESCALA = 0.7f;
    /** Lo que se deja libre afuera del muro (px). */
    public static final float MARGEN_PISTA = 8f;

    private static final float CELDA = 8f;
    private static final int PASO_CURVAS = 6;
    /** Cada cuántas muestras se prueba poner una tribuna: si no entra por la anterior, se sigue probando, y quedan seguidas. */
    private static final int PASO_TRIBUNAS = 1;
    private static final float SEPARACION_PROTECCION = 10f;
    private static final float SEPARACION_TRIBUNA = 36f;
    private static final float SEPARACION_AMBULANCIA = 40f;
    /** Una curva de cada tantas lleva ambulancia. */
    private static final int CURVAS_POR_AMBULANCIA = 4;
    private static final int NEUMATICOS_POR_CURVA = 4;
    /** Una recta con menos muestras que estas no lleva valla: quedaría un pedazo suelto. */
    private static final int RECTA_MINIMA = 24;
    /** Coseno del ángulo con un eje a partir del cual un tramo se puede decorar con piezas rectas (unos 14 grados). */
    private static final float CASI_RECTO = 0.97f;
    private static final float PASO_VEGETACION = 110f;
    private static final float ANCHO_CALLE = 64f;
    private static final float SEPARACION_CALLES_X = 420f;
    private static final float SEPARACION_CALLES_Y = 360f;
    private static final float PASO_AUTOS = 60f;
    private static final float PROBABILIDAD_AUTO = 0.4f;
    private static final float MARGEN_MANZANA = 6f;
    private static final int INTENTOS_EDIFICIO = 4;
    private static final float PASO_SIN_EDIFICIO = 16f;

    private final Circuito circuito;
    private final Circuito.Geometria g;
    private final Map<String, float[]> tamanos;
    private final Map<String, List<String>> grupos = new TreeMap<>();
    private final Random azar;
    private final int columnas;
    private final int filas;
    private final boolean[] ocupado;
    private final boolean[] enCalle;
    private final int[] curvas;
    private final List<Pieza> piezas = new ArrayList<>();
    private final List<Calle> calles = new ArrayList<>();

    /** @param tamanosAtlas ancho y alto de cada sprite en el atlas, en px */
    public DisposicionEntorno(Circuito circuito, Map<String, float[]> tamanosAtlas) {
        this.circuito = circuito;
        this.g = circuito.geometria();
        this.tamanos = tamanosAtlas;
        for (String nombre : new TreeMap<>(tamanosAtlas).keySet()) {
            grupos.computeIfAbsent(nombre.substring(0, nombre.lastIndexOf('_') + 1), k -> new ArrayList<>()).add(nombre);
        }
        this.azar = new Random(Arrays.hashCode(g.linea()));
        this.columnas = (int) Math.ceil(circuito.ancho() / CELDA);
        this.filas = (int) Math.ceil(circuito.alto() / CELDA);
        this.ocupado = new boolean[columnas * filas];
        this.enCalle = new boolean[columnas * filas];
        this.curvas = Pianos.curvas(circuito);
        marcarPista();
        if (g.callejero()) {
            armarCalles();
            autosEstacionados();
            detallesDeCurvas("barrera_", "cartel_");
            tribunasEnLosBordes();
            manzanas();
            vegetacion(0.12f, 0.2f);
        } else {
            detallesDeCurvas("neumatico_", null);
            tribunasEnLosBordes();
            vallasYMuros();
            vegetacion(0.5f, 0.3f);
        }
    }

    public List<Pieza> piezas() {
        return piezas;
    }

    public List<Calle> calles() {
        return calles;
    }

    private static float radioExterior(Circuito.Geometria g, int i) {
        return g.anchos()[i] / 2f + g.escapes()[i] + g.muro();
    }

    private void marcarPista() {
        for (int i = 0; i < g.muestras(); i += 2) {
            float r = radioExterior(g, i) + MARGEN_PISTA;
            float x = g.linea()[2 * i];
            float y = g.linea()[2 * i + 1];
            for (int fila = Math.max(0, (int) ((y - r) / CELDA) - 1); fila <= Math.min(filas - 1, (int) ((y + r) / CELDA) + 1); fila++) {
                for (int col = Math.max(0, (int) ((x - r) / CELDA) - 1); col <= Math.min(columnas - 1, (int) ((x + r) / CELDA) + 1); col++) {
                    // Se mide a la esquina más lejana de la celda: una pieza puede ocupar cualquier parte de ella.
                    float dx = Math.abs((col + 0.5f) * CELDA - x) + CELDA / 2f;
                    float dy = Math.abs((fila + 0.5f) * CELDA - y) + CELDA / 2f;
                    float cercaX = Math.max(0f, dx - CELDA);
                    float cercaY = Math.max(0f, dy - CELDA);
                    if (cercaX * cercaX + cercaY * cercaY < r * r) {
                        ocupado[fila * columnas + col] = true;
                    }
                }
            }
        }
    }

    private boolean libre(boolean[] grilla, float x, float y, float ancho, float alto) {
        if (x < 0f || y < 0f || x + ancho > circuito.ancho() || y + alto > circuito.alto()) {
            return false;
        }
        for (int fila = (int) (y / CELDA); fila <= Math.min(filas - 1, (int) ((y + alto) / CELDA)); fila++) {
            for (int col = (int) (x / CELDA); col <= Math.min(columnas - 1, (int) ((x + ancho) / CELDA)); col++) {
                if (grilla[fila * columnas + col]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Pone una pieza si entra, sin pisar la pista ni otra pieza. Con {@code sobreCalle} en false, tampoco una calle. */
    private boolean poner(String nombre, float x, float y, boolean sobreCalle) {
        float[] tam = tamanos.get(nombre);
        if (tam == null) {
            return false;
        }
        float ancho = tam[0] * ESCALA;
        float alto = tam[1] * ESCALA;
        if (!libre(ocupado, x, y, ancho, alto) || (!sobreCalle && !libre(enCalle, x, y, ancho, alto))) {
            return false;
        }
        marcar(x, y, ancho, alto);
        piezas.add(new Pieza(nombre, x, y, ancho, alto));
        return true;
    }

    private String alAzar(String grupo) {
        List<String> nombres = grupos.get(grupo);
        return nombres == null ? null : nombres.get(azar.nextInt(nombres.size()));
    }

    private float[] tangente(int muestra) {
        int n = g.muestras();
        int a = Math.floorMod(muestra - 2, n);
        int b = Math.floorMod(muestra + 2, n);
        float tx = g.linea()[2 * b] - g.linea()[2 * a];
        float ty = g.linea()[2 * b + 1] - g.linea()[2 * a + 1];
        float largo = (float) Math.hypot(tx, ty);
        return new float[] {tx / largo, ty / largo};
    }

    /** Pone una pieza del lado indicado de una muestra (1 = izquierda de la carrera), a esa distancia del muro. */
    private boolean ponerAlLado(String nombre, int muestra, int lado, float separacion) {
        float[] tam = tamanos.get(nombre);
        if (tam == null) {
            return false;
        }
        int i = Math.floorMod(muestra, g.muestras());
        float[] t = tangente(i);
        float nx = -t[1] * lado;
        float ny = t[0] * lado;
        float ancho = tam[0] * ESCALA;
        float alto = tam[1] * ESCALA;
        // Hasta el centro: la distancia al muro más la mitad de lo que mide la pieza en esa dirección.
        float distancia = radioExterior(g, i) + MARGEN_PISTA + separacion + (Math.abs(nx) * ancho + Math.abs(ny) * alto) / 2f;
        return poner(nombre, g.linea()[2 * i] + nx * distancia - ancho / 2f, g.linea()[2 * i + 1] + ny * distancia - alto / 2f, false);
    }

    /** La tribuna que mira a la pista desde ese lado de la muestra: la gente siempre queda de frente a los autos. */
    private String tribunaPara(int muestra, int lado) {
        float[] t = tangente(muestra);
        if (Math.abs(t[0]) > Math.abs(t[1])) {
            return t[0] * lado > 0 ? "tribuna_abajo" : "tribuna_arriba";
        }
        return -t[1] * lado > 0 ? "tribuna_izq" : "tribuna_der";
    }

    /** Tribunas a ambos lados de toda la pista, donde entren. Se prueba en cada muestra para que queden seguidas. */
    private void tribunasEnLosBordes() {
        for (int lado : new int[] {1, -1}) {
            for (int i = 0; i < g.muestras(); i += PASO_TRIBUNAS) {
                ponerAlLado(tribunaPara(i, lado), i, lado, SEPARACION_TRIBUNA);
            }
        }
    }

    /** Las curvas en orden, como {muestra del medio, signo}. */
    private List<int[]> listaDeCurvas() {
        List<int[]> lista = new ArrayList<>();
        int n = g.muestras();
        for (int i = 0; i < n; i++) {
            if (curvas[i] != 0 && curvas[Math.floorMod(i - 1, n)] != curvas[i]) {
                int largo = 0;
                while (largo < n && curvas[(i + largo) % n] == curvas[i]) {
                    largo++;
                }
                if (largo >= PASO_CURVAS) {
                    lista.add(new int[] {(i + largo / 2) % n, curvas[i]});
                }
            }
        }
        return lista;
    }

    /** Del lado de afuera de cada curva: una pila de protecciones contra el muro y, cada tanto, una ambulancia. */
    private void detallesDeCurvas(String proteccion, String cartel) {
        List<int[]> lista = listaDeCurvas();
        for (int c = 0; c < lista.size(); c++) {
            int medio = lista.get(c)[0];
            int afuera = -lista.get(c)[1];
            for (int k = 0; k < NEUMATICOS_POR_CURVA; k++) {
                String nombre = alAzar(proteccion);
                if (nombre != null) {
                    ponerAlLado(nombre, medio + (k - NEUMATICOS_POR_CURVA / 2) * 2, afuera, SEPARACION_PROTECCION);
                }
            }
            if (c % CURVAS_POR_AMBULANCIA == 0) {
                float[] t = tangente(medio);
                String sentido = Math.abs(t[0]) > Math.abs(t[1]) ? (t[0] > 0 ? "der" : "izq") : (t[1] > 0 ? "arr" : "aba");
                ponerAlLado("ambulancia_" + sentido, medio, afuera, SEPARACION_AMBULANCIA);
            } else if (cartel != null) {
                String nombre = alAzar(cartel);
                if (nombre != null) {
                    ponerAlLado(nombre, medio, afuera, SEPARACION_AMBULANCIA);
                }
            }
        }
    }

    /** En las rectas casi paralelas a un eje: valla o muro, una recta de cada cosa. Afuera del muro rojo, que es el que choca. */
    private void vallasYMuros() {
        int n = g.muestras();
        int recta = 0;
        for (int lado : new int[] {1, -1}) {
            int desde = -1;
            for (int i = 0; i <= n; i++) {
                float[] t = tangente(i);
                boolean casiRecto = i < n && (Math.abs(t[0]) > CASI_RECTO || Math.abs(t[1]) > CASI_RECTO);
                if (casiRecto && desde < 0) {
                    desde = i;
                } else if (!casiRecto && desde >= 0) {
                    if (i - desde >= RECTA_MINIMA) {
                        tiraDePiezas(recta % 2 == 0 ? "valla_" : "muro_", desde, i, lado);
                        recta++;
                    }
                    desde = -1;
                }
            }
        }
    }

    /**
     * Una hilera continua de piezas a lo largo de un tramo. Las piezas se tocan entre sí, así que la grilla se marca al
     * final: si se marcara a medida que se ponen, cada una le quitaría el lugar a la siguiente.
     */
    private void tiraDePiezas(String tipo, int desde, int hasta, int lado) {
        List<Pieza> tira = new ArrayList<>();
        float paso = (float) Math.hypot(g.linea()[2] - g.linea()[0], g.linea()[3] - g.linea()[1]);
        float k = desde;
        while (k < hasta) {
            int i = Math.floorMod((int) k, g.muestras());
            float[] t = tangente(i);
            boolean horizontal = Math.abs(t[0]) > Math.abs(t[1]);
            String nombre = tipo + (horizontal ? "h" : "v");
            float[] tam = tamanos.get(nombre);
            if (tam == null) {
                return;
            }
            float ancho = tam[0] * ESCALA;
            float alto = tam[1] * ESCALA;
            float nx = -t[1] * lado;
            float ny = t[0] * lado;
            float distancia = radioExterior(g, i) + MARGEN_PISTA + SEPARACION_PROTECCION + (Math.abs(nx) * ancho + Math.abs(ny) * alto) / 2f;
            float x = g.linea()[2 * i] + nx * distancia - ancho / 2f;
            float y = g.linea()[2 * i + 1] + ny * distancia - alto / 2f;
            if (libre(ocupado, x, y, ancho, alto)) {
                tira.add(new Pieza(nombre, x, y, ancho, alto));
            }
            k += (horizontal ? ancho / Math.abs(t[0]) : alto / Math.abs(t[1])) / paso;
        }
        for (Pieza pieza : tira) {
            marcar(pieza.x(), pieza.y(), pieza.ancho(), pieza.alto());
            piezas.add(pieza);
        }
    }

    private void marcar(float x, float y, float ancho, float alto) {
        for (int fila = (int) (y / CELDA); fila <= Math.min(filas - 1, (int) ((y + alto) / CELDA)); fila++) {
            for (int col = (int) (x / CELDA); col <= Math.min(columnas - 1, (int) ((x + ancho) / CELDA)); col++) {
                ocupado[fila * columnas + col] = true;
            }
        }
    }

    private void vegetacion(float probabilidadArbol, float probabilidadArbusto) {
        for (float y = 0f; y < circuito.alto(); y += PASO_VEGETACION) {
            for (float x = 0f; x < circuito.ancho(); x += PASO_VEGETACION) {
                float sorteo = azar.nextFloat();
                float px = x + azar.nextFloat() * PASO_VEGETACION * 0.6f;
                float py = y + azar.nextFloat() * PASO_VEGETACION * 0.6f;
                String nombre = sorteo < probabilidadArbol ? alAzar("arbol_")
                    : sorteo < probabilidadArbol + probabilidadArbusto ? alAzar("arbusto_") : null;
                if (nombre != null) {
                    poner(nombre, px, py, false);
                }
            }
        }
    }

    private void armarCalles() {
        for (float x = SEPARACION_CALLES_X / 2f; x < circuito.ancho(); x += SEPARACION_CALLES_X) {
            calles.add(new Calle(x, 0f, ANCHO_CALLE, circuito.alto(), false));
        }
        for (float y = SEPARACION_CALLES_Y / 2f; y < circuito.alto(); y += SEPARACION_CALLES_Y) {
            calles.add(new Calle(0f, y, circuito.ancho(), ANCHO_CALLE, true));
        }
        for (Calle c : calles) {
            for (int fila = (int) (c.y() / CELDA); fila <= Math.min(filas - 1, (int) ((c.y() + c.alto()) / CELDA)); fila++) {
                for (int col = (int) (c.x() / CELDA); col <= Math.min(columnas - 1, (int) ((c.x() + c.ancho()) / CELDA)); col++) {
                    enCalle[fila * columnas + col] = true;
                }
            }
        }
    }

    private void autosEstacionados() {
        for (Calle c : calles) {
            float largo = c.horizontal() ? c.ancho() : c.alto();
            for (float p = 0f; p < largo; p += PASO_AUTOS) {
                if (azar.nextFloat() >= PROBABILIDAD_AUTO) {
                    continue;
                }
                boolean primerCarril = azar.nextBoolean();
                String grupo = c.horizontal() ? (primerCarril ? "auto_der_" : "auto_izq_") : (primerCarril ? "auto_arr_" : "auto_aba_");
                String nombre = alAzar(grupo);
                if (nombre == null) {
                    continue;
                }
                float[] tam = tamanos.get(nombre);
                float x = c.horizontal() ? p : (primerCarril ? c.x() + c.ancho() - tam[0] * ESCALA - 2f : c.x() + 2f);
                float y = c.horizontal() ? (primerCarril ? c.y() + 2f : c.y() + c.alto() - tam[1] * ESCALA - 2f) : p;
                poner(nombre, x, y, true);
            }
        }
    }

    /** Llena cada manzana entre calles con una fila de edificios abajo y otra arriba, y un farol en la esquina. */
    private void manzanas() {
        for (float y = SEPARACION_CALLES_Y / 2f + ANCHO_CALLE - SEPARACION_CALLES_Y; y < circuito.alto(); y += SEPARACION_CALLES_Y) {
            for (float x = SEPARACION_CALLES_X / 2f + ANCHO_CALLE - SEPARACION_CALLES_X; x < circuito.ancho(); x += SEPARACION_CALLES_X) {
                float derecha = x + SEPARACION_CALLES_X - ANCHO_CALLE;
                float techo = y + SEPARACION_CALLES_Y - ANCHO_CALLE;
                poner("farol_1", x + 2f, y + 2f, false);
                filaDeEdificios(Math.max(0f, x) + 20f, derecha, Math.max(0f, y) + MARGEN_MANZANA, techo, false);
                filaDeEdificios(Math.max(0f, x) + 20f, derecha, Math.max(0f, y) + MARGEN_MANZANA, techo, true);
            }
        }
    }

    /** Una fila de edificios de izquierda a derecha, apoyada abajo o colgada arriba de la manzana. */
    private void filaDeEdificios(float desde, float derecha, float piso, float techo, boolean arriba) {
        float px = desde;
        while (px < derecha) {
            boolean puesto = false;
            for (int intento = 0; intento < INTENTOS_EDIFICIO && !puesto; intento++) {
                String nombre = alAzar("edificio_");
                if (nombre == null) {
                    return;
                }
                float ancho = tamanos.get(nombre)[0] * ESCALA;
                float alto = tamanos.get(nombre)[1] * ESCALA;
                if (px + ancho <= derecha && piso + alto <= techo) {
                    puesto = poner(nombre, px, arriba ? techo - alto - MARGEN_MANZANA : piso, false);
                    if (puesto) {
                        px += ancho + MARGEN_MANZANA;
                    }
                }
            }
            if (!puesto) {
                px += PASO_SIN_EDIFICIO;
            }
        }
    }
}
