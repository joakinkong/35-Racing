import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Stream;

/**
 * Prueba descartable de atajos: Dijkstra de un auto (círculo de RADIO) sobre una grilla de PASO px, desde la
 * largada, y compara la distancia más corta con la que hay a lo largo de la pista (en cualquiera de los dos sentidos).
 */
public class PruebaAtajos {
    static final float RADIO = 10f;
    static final float PASO = 8f;

    public static void main(String[] args) throws Exception {
        List<Path> archivos;
        try (Stream<Path> s = Files.list(Path.of(args[0]))) {
            archivos = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path p : archivos) {
            Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(p), 5);
            Circuito.Geometria g = c.geometria();
            int n = g.muestras();
            int cols = (int) (c.ancho() / PASO) + 1;
            int fils = (int) (c.alto() / PASO) + 1;
            float[] dist = new float[cols * fils];
            Arrays.fill(dist, Float.MAX_VALUE);
            boolean[] libre = new boolean[cols * fils];
            for (int f = 0; f < fils; f++) {
                for (int k = 0; k < cols; k++) {
                    libre[f * cols + k] = !c.chocaConMuro(k * PASO, f * PASO, RADIO);
                }
            }
            int inicio = celda(g.linea()[0], g.linea()[1], cols);
            PriorityQueue<float[]> cola = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
            dist[inicio] = 0;
            cola.add(new float[] {0, inicio});
            while (!cola.isEmpty()) {
                float[] actual = cola.poll();
                int idx = (int) actual[1];
                if (actual[0] > dist[idx]) {
                    continue;
                }
                int f = idx / cols;
                int k = idx % cols;
                for (int df = -1; df <= 1; df++) {
                    for (int dk = -1; dk <= 1; dk++) {
                        if (df == 0 && dk == 0) {
                            continue;
                        }
                        int nf = f + df;
                        int nk = k + dk;
                        if (nf < 0 || nk < 0 || nf >= fils || nk >= cols || !libre[nf * cols + nk]) {
                            continue;
                        }
                        float costo = actual[0] + (df != 0 && dk != 0 ? PASO * 1.414f : PASO);
                        if (costo < dist[nf * cols + nk]) {
                            dist[nf * cols + nk] = costo;
                            cola.add(new float[] {costo, nf * cols + nk});
                        }
                    }
                }
            }
            int sinLlegar = 0;
            int atajos = 0;
            float peor = 1f;
            int peorJ = -1;
            for (int j = 4; j < n; j += 4) {
                float arco = 8f * Math.min(j, n - j);
                int idx = celda(g.linea()[2 * j], g.linea()[2 * j + 1], cols);
                float d = dist[idx];
                if (d == Float.MAX_VALUE) {
                    sinLlegar++;
                    continue;
                }
                float razon = d / arco;
                if (razon < 0.6f && arco > 400f) {
                    atajos++;
                }
                if (razon < peor && arco > 400f) {
                    peor = razon;
                    peorJ = j;
                }
            }
            System.out.printf("%-18s muestras sin llegar: %3d   muestras con atajo (<60 %% del arco): %4d   peor razon: %.2f (muestra %d)%n",
                p.getFileName().toString().replace(".json", ""), sinLlegar, atajos, peor, peorJ);
        }
    }

    static int celda(float x, float y, int cols) {
        return Math.round(y / PASO) * cols + Math.round(x / PASO);
    }
}
