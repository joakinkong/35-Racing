import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.Superficie;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Prueba descartable: carga los 24 circuitos y revisa largada, checkpoints, pista y atajos entre tramos. */
public class PruebaCircuitos {
    static final float RADIO = 10f;

    public static void main(String[] args) throws Exception {
        List<Path> archivos;
        try (Stream<Path> s = Files.list(Path.of(args[0]))) {
            archivos = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        int fallas = 0;
        for (Path p : archivos) {
            String texto = Files.readString(p);
            long t0 = System.nanoTime();
            Circuito c = CargadorCircuito.cargarDesdeTexto(texto, 5);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Circuito.Geometria g = c.geometria();
            int n = g.muestras();
            StringBuilder problemas = new StringBuilder();

            for (int i = 0; i < 5; i++) {
                Vector2 l = c.posicionesLargada().get(i);
                if (c.superficieEn(l.x, l.y) != Superficie.PISTA || c.chocaConMuro(l.x, l.y, RADIO)) {
                    problemas.append(" largada").append(i + 1);
                }
            }
            int ckMalos = 0;
            for (Rectangle r : c.checkpoints()) {
                Vector2 centro = r.getCenter(new Vector2());
                if (c.superficieEn(centro.x, centro.y) != Superficie.PISTA) {
                    ckMalos++;
                }
            }
            if (ckMalos > 0) {
                problemas.append(" checkpointsFueraDePista=").append(ckMalos);
            }
            int centroMalo = 0;
            for (int i = 0; i < n; i++) {
                float x = g.linea()[2 * i];
                float y = g.linea()[2 * i + 1];
                if (c.superficieEn(x, y) != Superficie.PISTA || c.chocaConMuro(x, y, RADIO)) {
                    centroMalo++;
                }
            }
            if (centroMalo > 0) {
                problemas.append(" lineaCentralTocaMuro=").append(centroMalo);
            }

            // Atajos: dos muestras lejanas en arco pero cercanas en el plano no pueden unirse en línea recta sin pisar muro.
            boolean[] enCruce = new boolean[n];
            for (Circuito.Cruce cr : g.cruces()) {
                for (int d = -40; d <= 40; d++) {
                    enCruce[(cr.tunel() + d + n) % n] = true;
                    enCruce[(cr.puente() + d + n) % n] = true;
                }
            }
            int atajos = 0;
            int paso = 3;
            for (int a = 0; a < n; a += paso) {
                if (enCruce[a]) {
                    continue;
                }
                for (int b = a + 1; b < n; b += paso) {
                    int arco = Math.min(b - a, n - (b - a));
                    if (arco * 8 <= 700 || enCruce[b]) {
                        continue;
                    }
                    float ax = g.linea()[2 * a], ay = g.linea()[2 * a + 1];
                    float bx = g.linea()[2 * b], by = g.linea()[2 * b + 1];
                    float dist = (float) Math.hypot(bx - ax, by - ay);
                    if (dist > 320) {
                        continue;
                    }
                    boolean hayMuro = false;
                    int pasos = (int) (dist / 2) + 1;
                    for (int k = 0; k <= pasos && !hayMuro; k++) {
                        float t = k / (float) pasos;
                        // Un auto (círculo de RADIO) no puede ir de a hasta b sin que su círculo toque muro.
                        hayMuro = c.chocaConMuro(ax + (bx - ax) * t, ay + (by - ay) * t, RADIO);
                    }
                    if (!hayMuro) {
                        atajos++;
                        if (atajos == 1 && args.length > 1 && p.toString().contains(args[1])) {
                            System.out.printf("  primer atajo a=%d b=%d dist=%.1f anchoA=%.1f escA=%.1f anchoB=%.1f escB=%.1f arco=%d%n", a, b, dist,
                                g.anchos()[a], g.escapes()[a], g.anchos()[b], g.escapes()[b], arco);
                            for (int k = 0; k <= pasos; k += 4) {
                                float t = k / (float) pasos;
                                float px = ax + (bx - ax) * t, py = ay + (by - ay) * t;
                                System.out.printf("   t=%.2f sup=%s%n", t, c.superficieEn(px, py));
                            }
                        }
                    }
                }
            }
            if (atajos > 0) {
                problemas.append(" atajosSinMuro=").append(atajos);
            }
            if (problemas.length() > 0) {
                fallas++;
            }
            System.out.printf("%-18s carga %4d ms  %s%n", p.getFileName().toString().replace(".json", ""), ms,
                problemas.length() == 0 ? "ok" : "PROBLEMAS:" + problemas);
        }
        System.out.println(fallas == 0 ? "TODOS OK" : fallas + " circuitos con problemas");
    }
}
