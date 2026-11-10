import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Programa de verificación (fuera del juego): prueba las reglas de los bots contra el servidor real, con clientes reales
 * por TCP y sin ventana. Termina corriendo una carrera de una vuelta y pidiendo revancha, así que tarda más de un minuto
 * (en tiempo real, como una partida). Si una regla falla, sale con error. Ver tools/bots/LEEME.md.
 */
public class PruebaSalaBots {
    private static final long ESPERA_MS = 3000;
    private static final long ESPERA_CARRERA_MS = 150_000;
    private static String ultimoError = "";
    private static boolean llegaronResultados;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        ServidorPartida servidor = ServidorPartida.crear(0, 0,
            archivo -> CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve(archivo + ".json")), Config.MAX_JUGADORES));
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperar(host, l -> l.jugadores().size() == 1, "el host entra");

        for (int i = 0; i < Config.MAX_JUGADORES - 1; i++) {
            host.agregarBot(NivelBot.values()[i % NivelBot.values().length]);
        }
        esperar(host, l -> l.jugadores().size() == Config.MAX_JUGADORES, "se agregan los bots hasta llenar la sala");
        comprobar(host.lobby().jugadores().stream().filter(JugadorLobby::esBot).allMatch(JugadorLobby::listo), "los bots están siempre listos");

        host.agregarBot(NivelBot.FACIL);
        esperarError(host, Protocolo.PARTIDA_LLENA, "un auto de más se rechaza");

        ClientePartida invitado = new ClientePartida("Ana");
        invitado.conectar("127.0.0.1", servidor.puerto());
        esperar(host, l -> l.jugadores().stream().filter(j -> !j.esBot()).count() == 2 && l.jugadores().size() == Config.MAX_JUGADORES,
            "una persona entra con la sala llena y sale un bot");
        comprobar(!host.lobby().jugadores().get(1).esBot(), "la persona queda antes que los bots en la grilla");
        esperar(invitado, l -> l.jugadores().size() == Config.MAX_JUGADORES, "el invitado ve los bots");

        JugadorLobby bot = primerBot(host.lobby());
        invitado.agregarBot(NivelBot.FACIL);
        esperarError(invitado, Protocolo.NO_PERMITIDO, "un invitado no puede agregar bots");
        invitado.quitarBot(bot.id());
        esperarError(invitado, Protocolo.NO_PERMITIDO, "un invitado no puede quitar bots");

        host.quitarBot(invitado.miId());
        host.cambiarNivelBot(bot.id(), NivelBot.DIFICIL);
        esperar(host, l -> l.jugador(bot.id()).nivelBot() == NivelBot.DIFICIL.ordinal(), "el host cambia el nivel de un bot");
        comprobar(host.lobby().jugador(invitado.miId()) != null, "QUITAR_BOT no echa a una persona");

        int pilotoDelBot = bot.piloto();
        int pilotoDelHost = host.lobby().jugador(host.miId()).piloto();
        host.elegirPiloto(pilotoDelBot);
        esperar(host, l -> l.jugador(host.miId()).piloto() == pilotoDelBot, "una persona elige el piloto de un bot");
        JugadorLobby movido = host.lobby().jugador(bot.id());
        comprobar(movido.piloto() == pilotoDelHost, "el bot toma el piloto que dejó la persona (intercambio)");
        comprobar(movido.nombre().equals(Catalogo.piloto(movido.piloto()).apellido()), "el bot cambia de nombre con el piloto");

        for (JugadorLobby j : host.lobby().jugadores()) {
            if (j.esBot()) {
                host.quitarBot(j.id());
            }
        }
        esperar(host, l -> l.jugadores().size() == 2, "se quitan todos los bots");
        invitado.cerrar();
        esperar(host, l -> l.jugadores().size() == 1, "el invitado se va");
        comprobar(!host.lobby().sePuedeIniciar(), "una persona sola no puede iniciar");

        host.agregarBot(NivelBot.DIFICIL);
        host.marcarListo(true);
        host.elegirVueltas(Config.MIN_VUELTAS);
        host.elegirCircuito(Catalogo.indiceDeCircuito("spielberg"));
        esperar(host, EstadoLobby::sePuedeIniciar, "una persona y un bot pueden iniciar");
        host.iniciar();
        esperarResultados(host);
        System.out.println("OK: una carrera de una persona contra un bot llega a los resultados");

        host.revancha();
        esperar(host, l -> l.jugadores().size() == 2 && !l.jugadores().get(0).listo() && l.jugadores().get(1).listo(),
            "en la revancha sigue el bot, y el único que tiene que marcar listo de nuevo es la persona");
        host.cerrar();
        servidor.cerrar();
        System.out.println("TODO OK");
        System.exit(0);
    }

    private static JugadorLobby primerBot(EstadoLobby lobby) {
        return lobby.jugadores().stream().filter(JugadorLobby::esBot).findFirst().orElseThrow();
    }

    private static void comprobar(boolean condicion, String regla) {
        if (!condicion) {
            fallar(regla);
        }
        System.out.println("OK: " + regla);
    }

    private static void fallar(String regla) {
        System.out.println("FALLA: " + regla);
        System.exit(1);
    }

    private static void atender(ClientePartida cliente) {
        cliente.actualizar();
        Mensaje mensaje;
        while ((mensaje = cliente.sondear()) != null) {
            if (mensaje.es(Protocolo.ERROR)) {
                ultimoError = mensaje.campo(0);
            } else if (mensaje.es(Protocolo.RESULTADOS)) {
                llegaronResultados = true;
            }
        }
    }

    private static void esperar(ClientePartida cliente, Predicate<EstadoLobby> condicion, String regla) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            atender(cliente);
            if (condicion.test(cliente.lobby())) {
                System.out.println("OK: " + regla);
                return;
            }
            Thread.sleep(10);
        }
        fallar(regla);
    }

    private static void esperarError(ClientePartida cliente, String codigo, String regla) throws InterruptedException {
        ultimoError = "";
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            atender(cliente);
            if (codigo.equals(ultimoError)) {
                System.out.println("OK: " + regla);
                return;
            }
            Thread.sleep(10);
        }
        fallar(regla);
    }

    private static void esperarResultados(ClientePartida cliente) throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_CARRERA_MS;
        while (System.currentTimeMillis() < limite && !llegaronResultados) {
            atender(cliente);
            Thread.sleep(10);
        }
        if (!llegaronResultados) {
            fallar("la carrera llega a los resultados");
        }
    }
}
