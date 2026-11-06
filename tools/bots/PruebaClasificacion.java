import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.Participante;
import ar.edu.et35.racing.juego.PilotoBot;
import ar.edu.et35.racing.juego.RecorridoBot;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Programa de verificación (fuera del juego) de la clasificación. Primero, sin red: que 8 autos en la misma pista no se
 * molesten, comparando cada uno con lo que hace solo. Después, contra el servidor real: el recorrido completo de mensajes,
 * de la sala a los resultados, en tiempo real (más de 3 minutos). Si algo falla, sale con error. Ver tools/bots/LEEME.md.
 */
public class PruebaClasificacion {
    private static final float DT = 1f / Config.TICKS_POR_SEGUNDO;
    /** La misma para todos: con el mismo ritmo, cualquier diferencia de tiempo vendría de que se molestaron. */
    private static final long SEMILLA = 7L;
    private static final long ESPERA_MS = 3000;
    private static final long ESPERA_FASE_MS = 200_000;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        probarQueNoSeMolestan(carpeta);
        probarRecorridoDeMensajes(carpeta);
        System.out.println("TODO OK");
        System.exit(0);
    }

    private static void probarQueNoSeMolestan(Path carpeta) throws Exception {
        for (String nombre : List.of("monaco", "spielberg", "suzuka")) {
            Circuito c = CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve(nombre + ".json")), Config.MAX_JUGADORES);
            float solo = correr(c, 1).get(0);
            List<Float> ocho = correr(c, Config.MAX_JUGADORES);
            for (float tiempo : ocho) {
                comprobar(tiempo == solo, nombre + ": con 8 en pista, cada bot hace el mismo tiempo que solo (" + tiempo + " y " + solo + ")");
            }
        }
    }

    /** Una clasificación de esa cantidad de bots difíciles, que no se equivocan: si no se molestan, hacen todos lo mismo. */
    private static List<Float> correr(Circuito c, int cantidad) {
        Carrera carrera = new Carrera(c, 1, true);
        RecorridoBot recorrido = new RecorridoBot(c, NivelBot.DIFICIL);
        Map<Integer, PilotoBot> pilotos = new HashMap<>();
        Map<Integer, EntradaAuto> entradas = new HashMap<>();
        for (int id = 0; id < cantidad; id++) {
            carrera.agregarAuto(id, "bot" + id);
            pilotos.put(id, new PilotoBot(recorrido, NivelBot.DIFICIL, SEMILLA));
            entradas.put(id, new EntradaAuto());
        }
        while (carrera.estado() != EstadoCarrera.TERMINADA) {
            if (carrera.estado() == EstadoCarrera.EN_CURSO) {
                for (Participante p : carrera.participantes()) {
                    if (!p.terminado()) {
                        pilotos.get(p.id()).decidir(p, List.of(p), entradas.get(p.id()), DT);
                    }
                }
            }
            carrera.actualizar(entradas, DT);
        }
        List<Float> tiempos = new ArrayList<>();
        for (ResultadoJugador r : carrera.resultados()) {
            tiempos.add(r.tiempoTotal());
        }
        return tiempos;
    }

    private static void probarRecorridoDeMensajes(Path carpeta) throws Exception {
        ServidorPartida servidor = ServidorPartida.crear(0, 0,
            archivo -> CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve(archivo + ".json")), Config.MAX_JUGADORES));
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperarLobby(host, 1, false);
        host.agregarBot(NivelBot.FACIL);
        host.agregarBot(NivelBot.DIFICIL);
        host.elegirCircuito(Catalogo.indiceDeCircuito("spielberg"));
        host.elegirVueltas(Config.MIN_VUELTAS);
        host.elegirClasificacion(true);
        host.marcarListo(true);
        esperarLobby(host, 3, true);
        comprobar(host.lobby().clasificacion(), "el LOBBY informa que hay clasificación");
        List<Integer> ordenLobby = host.lobby().jugadores().stream().map(j -> j.id()).toList();
        comprobar(ordenLobby.get(0) == host.miId(), "en el lobby la persona va primero");

        host.iniciar();
        Protocolo.Cuenta primera = Protocolo.leerCuenta(esperarMensaje(host, Protocolo.CUENTA_REGRESIVA));
        comprobar(primera.clasificacion(), "la primera cuenta regresiva es la de la clasificación");
        comprobar(primera.vueltas() == 1, "la clasificación es de una vuelta");
        comprobar(primera.grilla().isEmpty(), "en la clasificación todos salen del mismo lugar");

        List<ResultadoJugador> grilla = Protocolo.leerResultados(esperarMensaje(host, Protocolo.GRILLA));
        comprobar(grilla.size() == 3, "la grilla trae a los 3 autos");
        comprobar(grilla.get(grilla.size() - 1).id() == host.miId() && Float.isNaN(grilla.get(grilla.size() - 1).tiempoTotal()),
            "el que no completó la vuelta larga último");
        comprobar(grilla.get(0).tiempoTotal() <= grilla.get(1).tiempoTotal(), "el más rápido larga primero");
        List<Integer> ordenGrilla = grilla.stream().map(ResultadoJugador::id).toList();

        Protocolo.Cuenta segunda = Protocolo.leerCuenta(esperarMensaje(host, Protocolo.CUENTA_REGRESIVA));
        comprobar(!segunda.clasificacion(), "la segunda cuenta regresiva es la de la carrera");
        comprobar(segunda.grilla().equals(ordenGrilla), "la carrera larga en el orden de la clasificación");

        List<ResultadoJugador> resultados = Protocolo.leerResultados(esperarMensaje(host, Protocolo.RESULTADOS));
        comprobar(resultados.size() == 3, "la carrera llega a los resultados");
        host.revancha();
        esperarLobby(host, 3, true);
        comprobar(host.lobby().clasificacion(), "la revancha conserva la clasificación");
        host.cerrar();
        servidor.cerrar();
    }

    private static void comprobar(boolean condicion, String regla) {
        if (!condicion) {
            System.out.println("FALLA: " + regla);
            System.exit(1);
        }
        System.out.println("OK: " + regla);
    }

    private static void esperarLobby(ClientePartida cliente, int autos, boolean clasificacion) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            while (cliente.sondear() != null) {
                // acá solo importa el lobby, que el cliente ya guarda solo
            }
            if (cliente.lobby().jugadores().size() == autos && cliente.lobby().clasificacion() == clasificacion) {
                return;
            }
            Thread.sleep(10);
        }
        comprobar(false, "el lobby tiene " + autos + " autos");
    }

    private static Mensaje esperarMensaje(ClientePartida cliente, String nombre) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_FASE_MS;
        while (System.currentTimeMillis() < limite) {
            cliente.actualizar();
            Mensaje mensaje;
            while ((mensaje = cliente.sondear()) != null) {
                if (mensaje.es(nombre)) {
                    return mensaje;
                }
            }
            Thread.sleep(10);
        }
        comprobar(false, "llega " + nombre);
        return null;
    }
}
