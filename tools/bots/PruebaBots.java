import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.ParametrosAuto;
import ar.edu.et35.racing.juego.Participante;
import ar.edu.et35.racing.juego.PilotoBot;
import ar.edu.et35.racing.juego.RecorridoBot;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Programa de verificación (fuera del juego): corre carreras con las reglas reales de {@link Carrera}, sin red ni gráficos
 * y más rápido que el tiempo real. Primero mide cada nivel solo en cada circuito y después hace una carrera de 8 bots
 * (tres de cada uno de los dos primeros niveles y dos difíciles). Ver tools/bots/LEEME.md.
 */
public class PruebaBots {
    private static final float DT = 1f / Config.TICKS_POR_SEGUNDO;
    private static final float TOPE_SEGUNDOS = 900f;
    private static final int VUELTAS_SOLO = 3;
    private static final int VUELTAS_GRUPO = 3;
    private static final NivelBot[] GRILLA = {
        NivelBot.FACIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.DIFICIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.DIFICIL, NivelBot.MEDIO,
    };

    private record Resultado(boolean termino, float mejorVuelta, float total, List<ResultadoJugador> tabla, Map<Integer, NivelBot> niveles, int golpes) {
    }

    public static void main(String[] args) throws Exception {
        List<Path> archivos;
        try (Stream<Path> s = Files.list(Path.of(args[0]))) {
            archivos = s.filter(p -> p.toString().endsWith(".json")).sorted()
                .filter(p -> args.length < 2 || p.toString().contains(args[1])).toList();
        }
        Map<NivelBot, Float> suma = new EnumMap<>(NivelBot.class);
        Map<NivelBot, Float> sumaTotal = new EnumMap<>(NivelBot.class);
        int[] sinTerminar = new int[NivelBot.values().length];
        int[] golpesPorNivel = new int[NivelBot.values().length];
        int gruposCompletos = 0;
        int ganoDificil = 0;
        int[] puestosPorNivel = new int[NivelBot.values().length];
        int[] autosPorNivel = new int[NivelBot.values().length];

        System.out.printf("%-18s %10s %10s %10s   carrera de 8: terminaron, ganador%n", "mejor vuelta", "FÁCIL", "MEDIO", "DIFÍCIL");
        for (Path p : archivos) {
            Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(p), Config.MAX_JUGADORES);
            StringBuilder fila = new StringBuilder(String.format("%-18s", p.getFileName().toString().replace(".json", "")));
            for (NivelBot nivel : NivelBot.values()) {
                Resultado r = correr(c, new NivelBot[] {nivel}, VUELTAS_SOLO);
                if (r.termino()) {
                    suma.merge(nivel, r.mejorVuelta(), Float::sum);
                    sumaTotal.merge(nivel, r.total() / VUELTAS_SOLO, Float::sum);
                    golpesPorNivel[nivel.ordinal()] += r.golpes();
                    fila.append(String.format(" %6.2f s/%2d", r.mejorVuelta(), r.golpes()));
                } else {
                    sinTerminar[nivel.ordinal()]++;
                    fila.append(String.format(" %10s", "NO TERMINÓ"));
                }
            }
            Resultado grupo = correr(c, GRILLA, VUELTAS_GRUPO);
            long terminaron = grupo.tabla().stream().filter(t -> !Float.isNaN(t.tiempoTotal())).count();
            NivelBot ganador = grupo.niveles().get(grupo.tabla().get(0).id());
            if (grupo.termino()) {
                gruposCompletos++;
            }
            ganoDificil += ganador == NivelBot.DIFICIL ? 1 : 0;
            for (ResultadoJugador t : grupo.tabla()) {
                int n = grupo.niveles().get(t.id()).ordinal();
                puestosPorNivel[n] += t.posicion();
                autosPorNivel[n]++;
            }
            fila.append(String.format("   %d/8, %s", terminaron, ganador.nombre()));
            System.out.println(fila);
        }
        int total = archivos.size();
        System.out.println();
        for (NivelBot nivel : NivelBot.values()) {
            int terminados = total - sinTerminar[nivel.ordinal()];
            System.out.printf("%-8s mejor vuelta media %6.2f s, vuelta media %6.2f s, sin terminar %d de %d, puesto medio en grupo %.2f, golpes al muro %d%n",
                nivel.nombre(), suma.getOrDefault(nivel, 0f) / Math.max(1, terminados),
                sumaTotal.getOrDefault(nivel, 0f) / Math.max(1, terminados), sinTerminar[nivel.ordinal()], total,
                puestosPorNivel[nivel.ordinal()] / (float) Math.max(1, autosPorNivel[nivel.ordinal()]), golpesPorNivel[nivel.ordinal()]);
        }
        System.out.printf("Carreras de 8 con todos en meta: %d de %d. Ganó un difícil: %d de %d%n", gruposCompletos, total, ganoDificil, total);
    }

    /** Una carrera con esos bots, en ese orden en la grilla. Termina cuando la carrera termina o se pasa del tope. */
    private static Resultado correr(Circuito circuito, NivelBot[] grilla, int vueltas) {
        Carrera carrera = new Carrera(circuito, vueltas);
        Map<NivelBot, RecorridoBot> recorridos = new EnumMap<>(NivelBot.class);
        Map<Integer, PilotoBot> pilotos = new HashMap<>();
        Map<Integer, EntradaAuto> entradas = new HashMap<>();
        Map<Integer, NivelBot> niveles = new HashMap<>();
        for (int id = 0; id < grilla.length; id++) {
            NivelBot nivel = grilla[id];
            carrera.agregarAuto(id, "bot" + id);
            RecorridoBot recorrido = recorridos.computeIfAbsent(nivel, n -> new RecorridoBot(circuito, n));
            pilotos.put(id, new PilotoBot(recorrido, nivel, id * 31L + 7L));
            entradas.put(id, new EntradaAuto());
            niveles.put(id, nivel);
        }
        float t = 0f;
        int golpes = 0;
        boolean alMuro = false;
        // En solitario no hay cierre que corte a un bot lento, pero en grupo sí: es la regla real.
        while (carrera.estado() != EstadoCarrera.TERMINADA && t < TOPE_SEGUNDOS) {
            if (carrera.estado() == EstadoCarrera.EN_CURSO) {
                for (Participante p : new ArrayList<>(carrera.participantes())) {
                    if (!p.terminado()) {
                        pilotos.get(p.id()).decidir(p, carrera.participantes(), entradas.get(p.id()), DT);
                    }
                }
            }
            carrera.actualizar(entradas, DT);
            t += DT;
            Participante uno = carrera.participante(0);
            boolean toca = circuito.chocaConMuro(uno.auto().x(), uno.auto().y(), ParametrosAuto.RADIO_COLISION + 1f);
            if (toca && !alMuro) {
                golpes++;
            }
            alMuro = toca;
        }
        List<ResultadoJugador> tabla = carrera.resultados();
        ResultadoJugador primero = tabla.get(0);
        boolean todos = tabla.stream().allMatch(r -> !Float.isNaN(r.tiempoTotal()));
        return new Resultado(todos, primero.mejorVuelta(), primero.tiempoTotal(), tabla, niveles, golpes);
    }

}
