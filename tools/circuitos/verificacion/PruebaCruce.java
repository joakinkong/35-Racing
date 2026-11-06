import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.NivelCruce;
import ar.edu.et35.racing.juego.Participante;
import ar.edu.et35.racing.juego.ZonaCruce;
import ar.edu.et35.racing.util.Config;
import com.badlogic.gdx.math.Vector2;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Comprueba el cruce de Suzuka: un auto que recorre cada tramo queda en su nivel, dos autos en niveles distintos no se
 * chocan aunque estén en el mismo punto, y dos en el mismo nivel sí. Sale con error si algo falla.
 */
public class PruebaCruce {
    private static final float DT = 1f / 60f;
    private static int fallas;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        Circuito suzuka = CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve("suzuka.json")), Config.MAX_JUGADORES);
        Circuito.Cruce cruce = suzuka.geometria().cruces().get(0);

        comprobarTramo(suzuka, cruce.tunel(), NivelCruce.ABAJO);
        comprobarTramo(suzuka, cruce.puente(), NivelCruce.ARRIBA);

        // Autos en cada tramo, justo donde se tocan los dos: distinto nivel, no se separan.
        comprobarChoque(suzuka, cruce.tunel(), cruce.puente(), false);
        // Dos autos por el mismo tramo sí se chocan, también abajo y arriba.
        comprobarChoque(suzuka, cruce.tunel(), cruce.tunel(), true);
        comprobarChoque(suzuka, cruce.puente(), cruce.puente(), true);

        int sinZona = 0;
        try (var archivos = Files.list(carpeta)) {
            for (Path p : (Iterable<Path>) archivos::iterator) {
                if (p.toString().endsWith(".json")) {
                    Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(p), Config.MAX_JUGADORES);
                    sinZona += c.zonasCruce().isEmpty() ? 1 : 0;
                }
            }
        }
        verificar(sinZona == 23, "23 circuitos sin cruce (dio " + sinZona + ")");
        if (fallas > 0) {
            System.out.println(fallas + " FALLAS");
            System.exit(1);
        }
        System.out.println("OK");
    }

    /** Recorre 120 muestras de un tramo, centradas en el cruce: dentro de la zona, siempre el mismo nivel. */
    private static void comprobarTramo(Circuito c, int centro, NivelCruce esperado) {
        NivelCruce nivel = NivelCruce.FUERA;
        int adentro = 0;
        boolean bien = true;
        for (int k = -60; k <= 60; k++) {
            int i = Math.floorMod(centro + k, c.geometria().muestras());
            nivel = ZonaCruce.nivel(c.zonasCruce(), nivel, c.geometria().linea()[2 * i], c.geometria().linea()[2 * i + 1]);
            if (nivel != NivelCruce.FUERA) {
                adentro++;
                bien &= nivel == esperado;
            }
        }
        verificar(bien && adentro > 0 && nivel == NivelCruce.FUERA,
            "tramo " + esperado + ": " + adentro + " muestras en la zona (" + adentro * 8 + " px), siempre en su nivel");
    }

    /** Lleva a dos autos por su tramo hasta el punto del cruce y mira si la carrera los separa. */
    private static void comprobarChoque(Circuito c, int tramoA, int tramoB, boolean debenChocar) throws Exception {
        Carrera carrera = new Carrera(c, 1);
        Participante a = carrera.agregarAuto(0, "A");
        Participante b = carrera.agregarAuto(1, "B");
        Map<Integer, EntradaAuto> sinEntradas = Map.of();
        for (int i = 0; i < 60 * (Config.SEGUNDOS_CUENTA_REGRESIVA + 1); i++) {
            carrera.actualizar(sinEntradas, DT);
        }
        // Los autos entran a la zona por su tramo, un paso de simulación por muestra, para que tomen su nivel.
        for (int k = -60; k <= 0; k++) {
            ubicar(a, c, tramoA + k);
            ubicar(b, c, tramoB + k - (tramoA == tramoB ? 10 : 0));
            carrera.actualizar(sinEntradas, DT);
        }
        // Mismo lugar exacto (o casi, para que el choque tenga dirección).
        Vector2 punto = new Vector2(c.geometria().linea()[2 * tramoA], c.geometria().linea()[2 * tramoA + 1]);
        poner(a, punto.x, punto.y);
        poner(b, punto.x + 4f, punto.y);
        carrera.actualizar(sinEntradas, DT);
        float distancia = Vector2.dst(a.auto().x(), a.auto().y(), b.auto().x(), b.auto().y());
        boolean chocaron = distancia > 5f;
        verificar(chocaron == debenChocar, "niveles " + a.nivelCruce() + " y " + b.nivelCruce() + ": separados a "
            + distancia + " px, " + (chocaron ? "chocaron" : "no chocaron"));
    }

    private static void ubicar(Participante p, Circuito c, int muestra) throws Exception {
        int i = Math.floorMod(muestra, c.geometria().muestras());
        poner(p, c.geometria().linea()[2 * i], c.geometria().linea()[2 * i + 1]);
    }

    private static void poner(Participante p, float x, float y) throws Exception {
        Field campo = p.auto().getClass().getDeclaredField("posicion");
        campo.setAccessible(true);
        ((Vector2) campo.get(p.auto())).set(x, y);
    }

    private static void verificar(boolean bien, String texto) {
        System.out.println((bien ? "ok    " : "FALLA ") + texto);
        fallas += bien ? 0 : 1;
    }
}
