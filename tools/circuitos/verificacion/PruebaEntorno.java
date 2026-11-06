import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.vista.DisposicionEntorno;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * En los 24 circuitos: ninguna decoración toca la pista ni su muro, ninguna se sale del mapa ni pisa a otra, y hay lo que
 * corresponde a cada tipo de circuito. Da además la disposición de dos corridas iguales. Sale con error si algo falla.
 */
public class PruebaEntorno {
    private static int fallas;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        File atlasArchivo = carpeta.resolveSibling("entorno").resolve("entorno.atlas").toFile();
        TextureAtlas.TextureAtlasData datos = new TextureAtlas.TextureAtlasData(new FileHandle(atlasArchivo), new FileHandle(atlasArchivo.getParentFile()), false);
        Map<String, float[]> tamanos = new HashMap<>();
        for (TextureAtlas.TextureAtlasData.Region r : datos.getRegions()) {
            tamanos.put(r.name, new float[] {r.width, r.height});
        }
        try (var archivos = Files.list(carpeta)) {
            for (Path p : (Iterable<Path>) archivos.sorted()::iterator) {
                if (p.toString().endsWith(".json")) {
                    revisar(p, tamanos);
                }
            }
        }
        System.out.println(fallas > 0 ? fallas + " FALLAS" : "OK");
        System.exit(fallas > 0 ? 1 : 0);
    }

    private static void revisar(Path p, Map<String, float[]> tamanos) throws Exception {
        Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(p), Config.MAX_JUGADORES);
        long inicio = System.nanoTime();
        DisposicionEntorno d = new DisposicionEntorno(c, tamanos);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        List<DisposicionEntorno.Pieza> piezas = d.piezas();
        Circuito.Geometria g = c.geometria();
        int tocan = 0;
        int afuera = 0;
        for (DisposicionEntorno.Pieza q : piezas) {
            if (q.x() < 0 || q.y() < 0 || q.x() + q.ancho() > c.ancho() || q.y() + q.alto() > c.alto()) {
                afuera++;
            }
            for (int i = 0; i < g.muestras(); i++) {
                float px = g.linea()[2 * i];
                float py = g.linea()[2 * i + 1];
                float dx = Math.max(Math.max(q.x() - px, px - q.x() - q.ancho()), 0f);
                float dy = Math.max(Math.max(q.y() - py, py - q.y() - q.alto()), 0f);
                float r = g.anchos()[i] / 2f + g.escapes()[i] + g.muro();
                if (dx * dx + dy * dy < r * r) {
                    tocan++;
                    break;
                }
            }
        }
        int pisadas = 0;
        for (int a = 0; a < piezas.size(); a++) {
            DisposicionEntorno.Pieza q = piezas.get(a);
            for (int b = a + 1; b < piezas.size(); b++) {
                DisposicionEntorno.Pieza o = piezas.get(b);
                // Las hileras de vallas y muros se tocan a propósito.
                if (q.nombre().matches("(valla|muro)_.") && o.nombre().matches("(valla|muro)_.")) {
                    continue;
                }
                if (q.x() < o.x() + o.ancho() && o.x() < q.x() + q.ancho() && q.y() < o.y() + o.alto() && o.y() < q.y() + q.alto()) {
                    pisadas++;
                }
            }
        }
        Map<String, Integer> cuenta = new TreeMap<>();
        for (DisposicionEntorno.Pieza q : piezas) {
            cuenta.merge(q.nombre().replaceAll("_.*", ""), 1, Integer::sum);
        }
        boolean igual = new DisposicionEntorno(c, tamanos).piezas().equals(piezas);
        boolean tipo = g.callejero() ? cuenta.containsKey("edificio") && cuenta.containsKey("auto") && !d.calles().isEmpty()
            : cuenta.containsKey("arbol") && cuenta.containsKey("tribuna") && d.calles().isEmpty();
        boolean bien = tocan == 0 && afuera == 0 && pisadas == 0 && igual && tipo && cuenta.containsKey("tribuna");
        fallas += bien ? 0 : 1;
        System.out.printf("%s %-18s %5d piezas %4d ms %s toca=%d afuera=%d pisadas=%d igual=%b%n", bien ? "ok   " : "FALLA",
            p.getFileName().toString().replace(".json", ""), piezas.size(), ms, cuenta, tocan, afuera, pisadas, igual);
    }
}
