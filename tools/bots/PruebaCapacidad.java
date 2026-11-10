import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.ModoGrilla;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Programa de verificación (fuera del juego): la capacidad de la sala, el tope de 8 personas, elegir el piloto de cada bot y
 * que dos pilotos de la misma escudería corran juntos, contra el servidor real y con clientes reales. Termina largando una
 * carrera con los 22 autos y mirando que llegue la cuenta regresiva con los 22 en la grilla. Tarda unos segundos.
 * Ver tools/bots/LEEME.md.
 */
public class PruebaCapacidad {
    private static final long ESPERA_MS = 3000;
    private static String ultimoError = "";
    private static Mensaje ultimaCuenta;

    public static void main(String[] args) throws Exception {
        Path carpeta = Path.of(args[0]);
        probarCapacidadChica(carpeta);
        probarSalaCompleta(carpeta);
        probarGrillaAlAzar(carpeta);
        System.out.println("TODO OK");
        System.exit(0);
    }

    private static ServidorPartida servidor(Path carpeta, int capacidad) throws Exception {
        return ServidorPartida.crear(0, 0, capacidad,
            archivo -> CargadorCircuito.cargarDesdeTexto(Files.readString(carpeta.resolve(archivo + ".json")), Config.MAX_JUGADORES));
    }

    /** Una sala de 4 autos: el lobby informa la capacidad y no deja pasarse. */
    private static void probarCapacidadChica(Path carpeta) throws Exception {
        ServidorPartida servidor = servidor(carpeta, 4);
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperar(host, l -> l.jugadores().size() == 1, "el host entra");
        comprobar(host.lobby().capacidad() == 4, "el lobby informa la capacidad elegida (4)");
        host.agregarBot(NivelBot.FACIL);
        host.agregarBot(NivelBot.FACIL);
        host.agregarBot(NivelBot.FACIL);
        esperar(host, l -> l.jugadores().size() == 4, "se llena la sala de 4 autos");
        host.agregarBot(NivelBot.FACIL);
        esperarError(host, Protocolo.PARTIDA_LLENA, "un quinto auto se rechaza");
        host.cerrar();
        servidor.cerrar();
    }

    private static void probarSalaCompleta(Path carpeta) throws Exception {
        ServidorPartida servidor = servidor(carpeta, Config.MAX_JUGADORES);
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperar(host, l -> l.jugadores().size() == 1, "el host entra a la sala de 22");

        // Cada bot con el piloto que se pide, no en el orden del catálogo: primero el último.
        int ultimo = Catalogo.PILOTOS.size() - 1;
        host.agregarBot(NivelBot.MEDIO, ultimo);
        esperar(host, l -> l.duenioDelPiloto(ultimo) != null && l.duenioDelPiloto(ultimo).esBot(),
            "se suma un bot con el piloto elegido (el último)");
        host.agregarBot(NivelBot.MEDIO, ultimo);
        esperarError(host, Protocolo.PILOTO_OCUPADO, "no se suma un bot con un piloto que ya está");

        // Los dos pilotos de una misma escudería corren a la vez.
        int hermano = ultimo - 1;
        host.agregarBot(NivelBot.DIFICIL, hermano);
        esperar(host, l -> l.duenioDelPiloto(hermano) != null && l.duenioDelPiloto(ultimo) != null,
            "los dos pilotos de una escudería, a la vez");
        comprobar(Catalogo.piloto(hermano).escuderia() == Catalogo.piloto(ultimo).escuderia(), "son de la misma escudería");

        // Al elegir el piloto de un bot se intercambian.
        int mio = host.lobby().jugador(host.miId()).piloto();
        host.elegirPiloto(hermano);
        esperar(host, l -> l.jugador(host.miId()).piloto() == hermano, "una persona elige el piloto de un bot");
        comprobar(host.lobby().duenioDelPiloto(mio) != null && host.lobby().duenioDelPiloto(mio).esBot(),
            "el bot toma el piloto que dejó la persona");

        // Hasta 8 personas; la novena no entra aunque haya lugar en la sala.
        List<ClientePartida> invitados = new ArrayList<>();
        for (int i = 1; i < Config.MAX_PERSONAS; i++) {
            ClientePartida invitado = new ClientePartida("Ana" + i);
            invitado.conectar("127.0.0.1", servidor.puerto());
            invitados.add(invitado);
            final int esperadas = i + 1;
            esperar(host, l -> l.cantidadDePersonas() == esperadas, "entra la persona " + esperadas);
        }
        ClientePartida novena = new ClientePartida("Novena");
        novena.conectar("127.0.0.1", servidor.puerto());
        esperarError(novena, Protocolo.PARTIDA_LLENA, "la novena persona se rechaza (máximo " + Config.MAX_PERSONAS + ")");

        // Se completan los 22 autos y los pilotos son todos distintos.
        while (host.lobby().hayLugarParaBot()) {
            int antes = host.lobby().jugadores().size();
            host.agregarBot(NivelBot.FACIL, -1);
            esperar(host, l -> l.jugadores().size() == antes + 1, "se suma un bot (" + (antes + 1) + " autos)");
        }
        long distintos = host.lobby().jugadores().stream().map(JugadorLobby::piloto).distinct().count();
        comprobar(host.lobby().jugadores().size() == 22 && distintos == 22, "22 autos con 22 pilotos distintos");

        // La grilla sin clasificación: personas primero, y el host la mueve a mano.
        List<Integer> inicial = ids(host);
        comprobar(inicial.subList(0, Config.MAX_PERSONAS).stream().noneMatch(id -> host.lobby().jugador(id).esBot()),
            "por defecto, las personas largan antes que los bots");
        int ultimoId = inicial.get(21);
        host.moverEnGrilla(ultimoId, -1);
        esperar(host, l -> l.jugadores().get(20).id() == ultimoId, "el host sube un lugar al último");
        comprobar(ids(host).get(21).equals(inicial.get(20)), "el que estaba antepenúltimo baja");
        host.moverEnGrilla(ids(host).get(0), -1);
        host.moverEnGrilla(ids(host).get(21), 1);
        host.sortearGrilla();
        Thread.sleep(300);
        atender(host);
        comprobar(ids(host).stream().sorted().toList().equals(inicial.stream().sorted().toList()), "sortear la grilla no pierde a nadie");
        invitados.get(0).moverEnGrilla(ids(host).get(0), 1);
        esperarError(invitados.get(0), Protocolo.NO_PERMITIDO, "un invitado no puede mover la grilla");
        host.elegirGrilla(ModoGrilla.CLASIFICACION);
        esperar(host, l -> l.modoGrilla() == ModoGrilla.CLASIFICACION, "el host elige la clasificación");
        host.moverEnGrilla(ids(host).get(5), -1);
        esperarError(host, Protocolo.NO_PERMITIDO, "con clasificación no se mueve la grilla");
        host.elegirGrilla(ModoGrilla.MANUAL);
        esperar(host, l -> l.modoGrilla() == ModoGrilla.MANUAL, "el host vuelve a la grilla a mano");
        List<Integer> armada = ids(host);

        // Se larga con los 22, en el orden que armó el host.
        ultimaCuenta = null;
        host.marcarListo(true);
        for (ClientePartida invitado : invitados) {
            invitado.marcarListo(true);
        }
        host.elegirCircuito(Catalogo.indiceDeCircuito("monaco"));
        esperar(host, EstadoLobby::sePuedeIniciar, "todos listos con 22 autos");
        host.iniciar();
        long limite = System.currentTimeMillis() + 15_000;
        while (ultimaCuenta == null && System.currentTimeMillis() < limite) {
            atender(host);
            Thread.sleep(10);
        }
        comprobar(ultimaCuenta != null, "llega la cuenta regresiva");
        comprobar(Protocolo.leerCuenta(ultimaCuenta).grilla().size() == 22, "la grilla de largada tiene los 22 autos");
        comprobar(Protocolo.leerCuenta(ultimaCuenta).grilla().equals(armada), "se larga con la grilla que armó el host");
        host.cerrar();
        for (ClientePartida invitado : invitados) {
            invitado.cerrar();
        }
        novena.cerrar();
        servidor.cerrar();
    }

    /** Una sala con la grilla al azar: la largada es un sorteo del mismo conjunto de autos, y no el orden del lobby. */
    private static void probarGrillaAlAzar(Path carpeta) throws Exception {
        ServidorPartida servidor = servidor(carpeta, 12);
        ClientePartida host = new ClientePartida("Host");
        host.conectar("127.0.0.1", servidor.puerto());
        esperar(host, l -> l.jugadores().size() == 1, "el host entra a la sala de 12");
        for (int i = 0; i < 11; i++) {
            host.agregarBot(NivelBot.FACIL, -1);
        }
        esperar(host, l -> l.jugadores().size() == 12, "se completan los 12 autos");
        host.elegirGrilla(ModoGrilla.AL_AZAR);
        esperar(host, l -> l.modoGrilla() == ModoGrilla.AL_AZAR, "el host elige la grilla al azar");
        host.sortearGrilla();
        esperarError(host, Protocolo.NO_PERMITIDO, "con la grilla al azar no se sortea a mano");
        List<Integer> enLobby = ids(host);
        ultimaCuenta = null;
        host.marcarListo(true);
        esperar(host, EstadoLobby::sePuedeIniciar, "la sala al azar puede iniciar");
        host.iniciar();
        long limite = System.currentTimeMillis() + 15_000;
        while (ultimaCuenta == null && System.currentTimeMillis() < limite) {
            atender(host);
            Thread.sleep(10);
        }
        comprobar(ultimaCuenta != null, "llega la cuenta regresiva de la sala al azar");
        List<Integer> largada = Protocolo.leerCuenta(ultimaCuenta).grilla();
        comprobar(largada.stream().sorted().toList().equals(enLobby.stream().sorted().toList()), "el sorteo largó a los mismos 12 autos");
        comprobar(!largada.equals(enLobby), "la largada es un sorteo, no el orden del lobby");
        host.cerrar();
        servidor.cerrar();
    }

    private static List<Integer> ids(ClientePartida cliente) {
        return cliente.lobby().jugadores().stream().map(JugadorLobby::id).toList();
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
            } else if (mensaje.es(Protocolo.CUENTA_REGRESIVA)) {
                ultimaCuenta = mensaje;
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
            if (ultimoError.equals(codigo)) {
                System.out.println("OK: " + regla);
                return;
            }
            Thread.sleep(10);
        }
        fallar(regla + " (error esperado " + codigo + ", llegó '" + ultimoError + "')");
    }
}
