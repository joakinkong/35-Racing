import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.PartidaLocal;
import ar.edu.et35.racing.juego.SimulacionLocal;
import ar.edu.et35.racing.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Programa de verificación (fuera del juego): una partida local con 2 personas y 20 bots (los 22 autos) en cada circuito,
 * sin ventana y más rápido que el tiempo real. Comprueba que los 22 autos largan sobre la pista y que los bots avanzan.
 * Ver tools/bots/LEEME.md.
 */
public class PruebaLocal22 {
    private static final float SEGUNDOS = 25f;
    private static final float DELTA = 1f / 30f;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        int fallas = 0;
        try (var archivos = Files.list(carpeta)) {
            for (Path archivo : (Iterable<Path>) archivos.filter(p -> p.toString().endsWith(".json")).sorted()::iterator) {
                Circuito circuito = CargadorCircuito.cargarDesdeTexto(Files.readString(archivo), Config.MAX_JUGADORES);
                SimulacionLocal simulacion = new SimulacionLocal(circuito, new PartidaLocal(List.of(0, 2), 20, NivelBot.DIFICIL));
                Map<Integer, EntradaAuto> entradas = new HashMap<>();
                entradas.put(0, new EntradaAuto());
                entradas.put(1, new EntradaAuto());
                for (float t = 0; t < SEGUNDOS; t += DELTA) {
                    simulacion.avanzar(DELTA, entradas);
                }
                FotoCarrera foto = simulacion.foto();
                int autos = foto.autos().size();
                int bots22Movidos = 0;
                for (FotoAuto auto : foto.autos()) {
                    var lugar = circuito.posicionesLargada().get(auto.id());
                    if (auto.id() >= 2 && Math.hypot(auto.x() - lugar.x, auto.y() - lugar.y) > 100) {
                        bots22Movidos++;
                    }
                }
                boolean bien = autos == 22 && bots22Movidos == 20;
                System.out.println((bien ? "OK    " : "FALLA ") + archivo.getFileName() + ": " + autos + " autos, " + bots22Movidos + " bots corriendo");
                if (!bien) {
                    fallas++;
                }
            }
        }
        System.exit(fallas == 0 ? 0 : 1);
    }
}
