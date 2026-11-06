import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.ParametrosAuto;
import com.badlogic.gdx.math.Vector2;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Prueba descartable: un bot sigue la línea central con la física real de Auto (pura persecución para doblar, y
 * velocidad según el radio de las curvas que vienen) y se mide cuánto tarda una vuelta. Sirve para calibrar el largo
 * de los circuitos, no para decir cuánto va a tardar una persona.
 */
public class PruebaVuelta {
    static final float DT = 1f / 60f;
    static final float MARGEN_AGARRE = 0.85f;
    static final float MARGEN_FRENO = 0.8f;

    public static void main(String[] args) throws Exception {
        List<Path> archivos;
        try (Stream<Path> s = Files.list(Path.of(args[0]))) {
            archivos = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        float suma = 0;
        int contados = 0;
        for (Path p : archivos) {
            Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(p), Config.MAX_JUGADORES);
            Circuito.Geometria g = c.geometria();
            int n = g.muestras();
            Vector2 l = c.posicionesLargada().get(0);
            Auto auto = new Auto(l.x, l.y, c.anguloLargada());
            EntradaAuto entrada = new EntradaAuto();
            int indice = nearest(g, l.x, l.y, 0, n);
            int recorridas = 0;
            int primero = indice;
            float t = 0;
            int golpes = 0;
            boolean alMuro = false;
            float sinMov = 0f;
            float reversa = 0f;
            while (recorridas < n && t < 400f) {
                int nuevo = nearest(g, auto.x(), auto.y(), indice, 40);
                int avance = Math.floorMod(nuevo - indice, n);
                if (avance < 40) {
                    recorridas += avance;
                }
                indice = nuevo;
                float v = auto.velocidad().len();

                // Persecución: apunta a un punto adelante en la línea central.
                float mira = 60f + 0.3f * v;
                float rCerca = 1e9f;
                for (int k = 0; k <= 10; k++) {
                    rCerca = Math.min(rCerca, radio(g, (indice + k) % n));
                }
                mira = Math.min(mira, Math.max(35f, 0.8f * rCerca));
                int j = (indice + Math.round(mira / 8f)) % n;
                float dx = g.linea()[2 * j] - auto.x();
                float dy = g.linea()[2 * j + 1] - auto.y();
                float objetivo = (float) Math.toDegrees(Math.atan2(dy, dx));
                float diff = ((objetivo - auto.angulo() + 540f) % 360f) - 180f;
                entrada.giro = diff > 1f ? 1 : diff < -1f ? -1 : 0;

                // Velocidad permitida: la de cada curva que viene, más lo que alcanza a frenar hasta ahí.
                float permitida = ParametrosAuto.VELOCIDAD_MAX;
                float distancia = 0f;
                for (int k = 1; k <= 90; k++) {
                    int m = (indice + k) % n;
                    distancia += 8f;
                    float r = radio(g, m);
                    float vCurva = (float) Math.sqrt(MARGEN_AGARRE * ParametrosAuto.ACELERACION_LATERAL * r);
                    float vAqui = (float) Math.sqrt(vCurva * vCurva + 2f * MARGEN_FRENO * ParametrosAuto.FRENADO * distancia);
                    permitida = Math.min(permitida, vAqui);
                }
                float vAdelante = v;
                entrada.acelerar = vAdelante < permitida;
                entrada.frenar = vAdelante > permitida * 1.03f;
                sinMov = v < 15f ? sinMov + DT : 0f;
                if (sinMov > 1.2f) {
                    reversa = 0.9f;
                    sinMov = 0f;
                }
                if (reversa > 0f) {
                    entrada.acelerar = false;
                    entrada.frenar = true;
                    entrada.giro = 0;
                    reversa -= DT;
                }
                auto.actualizar(entrada, c, DT);
                boolean choca = c.chocaConMuro(auto.x(), auto.y(), ParametrosAuto.RADIO_COLISION + 1f);
                if (choca && !alMuro) {
                    golpes++;
                    if (args.length > 1 && p.toString().contains(args[1])) {
                        System.out.printf("   golpe %d en t=%.1f indice=%d v=%.0f permitida=%.0f radioLinea=%.0f ancho=%.0f%n", golpes, t, indice, v, permitida, radio(g, indice), g.anchos()[indice]);
                    }
                }
                alMuro = choca;
                t += DT;
            }
            boolean ok = recorridas >= n;
            System.out.printf("%-18s bot: %5.1f s %s  largo nominal %5.1f s  (golpes al muro: %d)%n",
                p.getFileName().toString().replace(".json", ""), t, ok ? "" : "(NO TERMINÓ)", n * 8f / 350f, golpes);
            if (ok) {
                suma += t;
                contados++;
            }
        }
        System.out.printf("PROMEDIO bot (%d circuitos): %.1f s%n", contados, suma / contados);
    }

    static float radio(Circuito.Geometria g, int i) {
        int n = g.muestras();
        int a = Math.floorMod(i - 5, n);
        int b = (i + 5) % n;
        float ax = g.linea()[2 * a], ay = g.linea()[2 * a + 1];
        float bx = g.linea()[2 * i], by = g.linea()[2 * i + 1];
        float cx = g.linea()[2 * b], cy = g.linea()[2 * b + 1];
        float doble = Math.abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax));
        if (doble < 1e-6f) {
            return 1e9f;
        }
        float ab = (float) Math.hypot(bx - ax, by - ay);
        float bc = (float) Math.hypot(cx - bx, cy - by);
        float ca = (float) Math.hypot(ax - cx, ay - cy);
        return ab * bc * ca / (2f * doble);
    }

    static int nearest(Circuito.Geometria g, float x, float y, int desde, int ventana) {
        int n = g.muestras();
        int mejor = desde;
        float mejorD = Float.MAX_VALUE;
        int total = ventana >= n ? n : ventana;
        for (int k = 0; k < total; k++) {
            int i = (desde + k) % n;
            float dx = g.linea()[2 * i] - x;
            float dy = g.linea()[2 * i + 1] - y;
            float d = dx * dx + dy * dy;
            if (d < mejorD) {
                mejorD = d;
                mejor = i;
            }
        }
        return mejor;
    }
}
