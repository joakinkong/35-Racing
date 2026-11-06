package ar.edu.et35.racing.red.servidor;

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
import ar.edu.et35.racing.juego.Torneo;
import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.EstadoTorneo;
import ar.edu.et35.racing.red.PaquetesUdp;
import ar.edu.et35.racing.red.PerdidaSimulada;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.RegistroRed;
import ar.edu.et35.racing.util.Config;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Servidor de la partida, embebido en el proceso del host (docs/PROTOCOLO.md). Es autoritativo: tiene la única
 * {@link Carrera} que se simula de verdad.
 *
 * <p>Hilos: uno que acepta conexiones TCP, uno lector TCP por cliente, uno receptor UDP y uno de simulación.
 * <b>Solo el hilo de simulación toca el estado de la partida</b> (jugadores, fase, carrera). Los demás le pasan datos
 * por dos vías, igual que la diferencia entre TCP y UDP:
 * <ul>
 * <li>lo que llega por TCP (no se puede perder) va a una cola de eventos que se procesa en orden;</li>
 * <li>lo que llega por UDP (se reemplaza constantemente) se guarda como "última entrada" de cada jugador, en campos
 * volatile que el receptor sobrescribe y la simulación lee.</li>
 * </ul>
 *
 * <p>El primer jugador que se une es el host: es el que creó la partida y se conecta enseguida a su propio servidor.
 */
public class ServidorPartida {
    private static final String ORIGEN = "SERVIDOR";
    private static final long NANOS_POR_TICK = 1_000_000_000L / Config.TICKS_POR_SEGUNDO;
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    private static final int TICKS_POR_ESTADO = Math.max(1, Config.TICKS_POR_SEGUNDO / Config.ENVIOS_ESTADO_POR_SEGUNDO);
    /** Si la simulación se atrasa más que esto (la PC se trabó), no intenta recuperar todos los ticks de golpe. */
    private static final long MAX_ATRASO_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long TIMEOUT_ENTRADA_NANOS = TimeUnit.MILLISECONDS.toNanos(Config.TIMEOUT_ENTRADA_UDP_MS);

    /** Fase de la partida (docs/PROTOCOLO.md, sección 2). */
    private enum Fase { LOBBY, CUENTA_REGRESIVA, CARRERA, GRILLA, RESULTADOS }

    /** Una conexión aceptada. Todavía no es un jugador hasta que manda UNIRSE y se lo acepta. */
    private static final class Cliente {
        final ConexionTcp conexion;
        volatile Jugador jugador; // lo asigna el hilo de simulación

        Cliente(ConexionTcp conexion) {
            this.conexion = conexion;
        }

        String nombreParaLog() {
            Jugador j = jugador;
            return j != null ? "#" + j.id + " " + j.nombre : conexion.direccionRemota();
        }
    }

    /**
     * Un auto de la partida: un jugador aceptado o un bot. Un bot no tiene conexión (cliente y udp en null) porque no
     * hay nadie del otro lado: sus entradas las calcula acá mismo el hilo de simulación, y por eso un bot no agrega nada
     * de tráfico. Solo lo modifica el hilo de simulación.
     */
    private static final class Jugador {
        final int id;
        final Cliente cliente;
        final EntradaRemota udp;
        String nombre;
        int piloto;
        boolean listo;
        boolean avisoUdpDado;
        NivelBot nivelBot;

        Jugador(int id, String nombre, Cliente cliente, EntradaRemota udp, int piloto) {
            this.id = id;
            this.nombre = nombre;
            this.cliente = cliente;
            this.udp = udp;
            this.piloto = piloto;
        }

        static Jugador bot(int id, int piloto, NivelBot nivel) {
            Jugador bot = new Jugador(id, Catalogo.piloto(piloto).apellido(), null, null, piloto);
            bot.nivelBot = nivel;
            bot.listo = true;
            return bot;
        }

        boolean esBot() {
            return nivelBot != null;
        }

        /** Le cambia el piloto y, si es un bot, el nombre, que es el apellido del piloto. */
        void cambiarPiloto(int nuevo) {
            piloto = nuevo;
            if (esBot()) {
                nombre = Catalogo.piloto(nuevo).apellido();
            }
        }
    }

    /**
     * Lo que llega por UDP de un jugador. El receptor UDP es el único que escribe (los volatile para que la
     * simulación vea el último valor) y la simulación solo lee.
     */
    private static final class EntradaRemota {
        final int token;
        volatile int botones;
        volatile long ultimaLlegadaNanos;
        volatile SocketAddress direccion;
        // Solo los usa el hilo receptor UDP.
        boolean recibioAlguna;
        int ultimaSecuencia;
        int descartados;

        EntradaRemota(int token) {
            this.token = token;
        }
    }

    /**
     * Carga un circuito por el nombre de su archivo. Lo implementa el host con su hilo de render, porque leer el archivo
     * usa Gdx.files y no se toca LibGDX desde los hilos del servidor; se lo llama desde el hilo de
     * simulación, que espera la respuesta (no hay carrera en curso, así que no se atrasa nada).
     */
    @FunctionalInterface
    public interface ProveedorCircuitos {
        Circuito cargar(String archivo) throws Exception;
    }

    private interface Evento {
    }

    private record Recibido(Cliente cliente, Mensaje mensaje) implements Evento {
    }

    private record Desconectado(Cliente cliente, String motivo) implements Evento {
    }

    private final ServerSocket socketTcp;
    private final DatagramSocket socketUdp;
    /** Cómo conseguir el circuito elegido al iniciar la carrera (lo carga el host en su hilo de render). */
    private final ProveedorCircuitos proveedor;
    /** El último circuito cargado, para que una revancha en el mismo circuito no lo cargue de nuevo. */
    private String archivoCargado;
    private Circuito circuitoCargado;
    private final BlockingQueue<Evento> eventos = new LinkedBlockingQueue<>();
    private final List<Cliente> conexiones = new CopyOnWriteArrayList<>();
    /** Por id de jugador: lo comparten el receptor UDP (busca el token) y la simulación (crea y borra). */
    private final AtomicReferenceArray<EntradaRemota> remotas = new AtomicReferenceArray<>(Config.MAX_JUGADORES);
    private final AtomicBoolean activo = new AtomicBoolean(true);
    private final SecureRandom azar = new SecureRandom();

    // Estado de la partida: solo lo toca el hilo de simulación
    private final Map<Integer, Jugador> jugadores = new LinkedHashMap<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();
    /** Los pilotos de los bots de la carrera en curso, por id. */
    private final Map<Integer, PilotoBot> pilotosBot = new HashMap<>();
    private Fase fase = Fase.LOBBY;
    private int idHost = -1;
    /** Índice en el catálogo del circuito elegido; lo cambia el host en el lobby. */
    private int circuitoElegido;
    /** Vueltas elegidas por el host en el lobby. */
    private int vueltasElegidas = Config.VUELTAS;
    /** Si el host pidió clasificación antes de la carrera. */
    private boolean hayClasificacion;
    /** Si el host eligió torneo en lugar de carrera única. */
    private boolean modoTorneo;
    /** Los circuitos del torneo en orden. Se conserva si el host vuelve a carrera única o después de una revancha. */
    private final List<Integer> calendario = new ArrayList<>();
    /** El torneo en curso; null en una carrera única o en el lobby. */
    private Torneo torneo;
    /** Índice en el catálogo del circuito que se está corriendo: el elegido o el que toca en el torneo. */
    private int circuitoEnCurso;
    /** Si lo que se está corriendo ahora es la clasificación y no la carrera. */
    private boolean enClasificacion;
    /** Ids en el orden en que terminó la clasificación; vacía si no hubo. */
    private List<Integer> ordenClasificacion = List.of();
    /** Ticks que faltan para que arranque la carrera, mientras se muestra la grilla. */
    private int ticksDeGrilla;
    private Carrera carrera;
    private int tickInicioCarrera;
    /** Número de tick de la simulación: nunca vuelve atrás, ni entre carreras. Es la secuencia del ESTADO. */
    private int tick;

    private ServidorPartida(ServerSocket socketTcp, DatagramSocket socketUdp, ProveedorCircuitos proveedor) {
        this.socketTcp = socketTcp;
        this.socketUdp = socketUdp;
        this.proveedor = proveedor;
        int inicial = Catalogo.indiceDeCircuito(Config.CIRCUITO);
        this.circuitoElegido = inicial >= 0 ? inicial : 0;
    }

    /**
     * Crea el servidor y lo deja escuchando.
     *
     * @param puertoTcp puerto TCP; 0 deja que el sistema elija uno libre (se usa en las pruebas)
     * @param puertoUdp puerto UDP; 0 igual
     * @param proveedor carga el circuito elegido cuando el host inicia la carrera. Se pide recién ahí y no al crear la
     *                  partida porque los mapas son grandes y el host puede elegir cualquiera de los 24
     * @throws IOException si alguno de los puertos ya está ocupado (por ejemplo, otra partida creada en esta PC)
     */
    public static ServidorPartida crear(int puertoTcp, int puertoUdp, ProveedorCircuitos proveedor) throws IOException {
        ServerSocket tcp = new ServerSocket(puertoTcp);
        DatagramSocket udp;
        try {
            udp = new DatagramSocket(puertoUdp);
        } catch (IOException e) {
            tcp.close();
            throw e;
        }
        ServidorPartida servidor = new ServidorPartida(tcp, udp, proveedor);
        servidor.iniciarHilos();
        RegistroRed.log(ORIGEN, "Escuchando TCP en el puerto " + servidor.puerto() + " y UDP en el " + servidor.puertoUdp());
        return servidor;
    }

    public int puerto() {
        return socketTcp.getLocalPort();
    }

    public int puertoUdp() {
        return socketUdp.getLocalPort();
    }

    public boolean activo() {
        return activo.get();
    }

    private void iniciarHilos() {
        hilo("servidor-aceptacion", this::aceptar);
        hilo("servidor-udp", this::recibirUdp);
        hilo("servidor-simulacion", this::correrSimulacion);
    }

    private static Thread hilo(String nombre, Runnable tarea) {
        Thread hilo = new Thread(tarea, nombre);
        // Daemon: si el programa termina, estos hilos no lo mantienen vivo.
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    // ------------------------------------------------------------------ hilo de aceptación y lectores TCP

    private void aceptar() {
        while (activo.get()) {
            try {
                Socket socket = socketTcp.accept();
                Cliente cliente = new Cliente(new ConexionTcp(socket));
                conexiones.add(cliente);
                RegistroRed.log(ORIGEN, "Conexión entrante de " + cliente.conexion.direccionRemota());
                hilo("servidor-lector-" + cliente.conexion.direccionRemota(), () -> leer(cliente));
            } catch (IOException e) {
                if (activo.get() && !socketTcp.isClosed()) {
                    RegistroRed.log(ORIGEN, "Error al aceptar una conexión: " + e.getMessage());
                } else {
                    return;
                }
            } catch (RuntimeException e) {
                RegistroRed.log(ORIGEN, "Error inesperado al aceptar una conexión: " + e);
            }
        }
    }

    /** Hilo lector de un cliente: convierte lo que llega en eventos para el hilo de simulación. */
    private void leer(Cliente cliente) {
        String motivo = "cerró la conexión";
        try {
            String linea;
            while ((linea = cliente.conexion.leerLinea()) != null) {
                Mensaje mensaje = Protocolo.leer(linea);
                if (mensaje == null) {
                    continue;
                }
                RegistroRed.log(ORIGEN, "<- " + cliente.nombreParaLog() + ": " + linea);
                if (mensaje.es(Protocolo.PING)) {
                    enviar(cliente, Protocolo.armar(Protocolo.PONG, mensaje.campo(0)));
                } else {
                    eventos.add(new Recibido(cliente, mensaje));
                }
            }
        } catch (SocketTimeoutException e) {
            motivo = "sin respuesta durante " + Config.TIMEOUT_TCP_MS / 1000 + " s";
        } catch (IOException e) {
            motivo = cliente.conexion.cerrada() ? "conexión cerrada" : "error de red: " + e.getMessage();
        } catch (RuntimeException e) {
            // Un mensaje que rompa el procesamiento da de baja a ese cliente, pero no al servidor.
            RegistroRed.log(ORIGEN, "Error inesperado con " + cliente.nombreParaLog() + ": " + e);
            motivo = "error interno";
        } finally {
            cliente.conexion.cerrar();
            eventos.add(new Desconectado(cliente, motivo));
        }
    }

    // ------------------------------------------------------------------ hilo receptor UDP

    /**
     * Recibe las ENTRADA. Descarta lo que no tenga el tamaño y el tipo correctos, un id o un token que no
     * correspondan, o una secuencia menor o igual a la última (atrasado o duplicado). Con la primera ENTRADA válida
     * de cada jugador aprende a qué dirección y puerto mandarle los ESTADO.
     */
    private void recibirUdp() {
        byte[] bufer = new byte[PaquetesUdp.LARGO_MAXIMO + 64];
        DatagramPacket paquete = new DatagramPacket(bufer, bufer.length);
        while (activo.get()) {
            try {
                paquete.setLength(bufer.length);
                socketUdp.receive(paquete);
            } catch (IOException e) {
                return; // el socket se cerró: el servidor se está apagando
            }
            try {
                procesarPaqueteUdp(bufer, paquete);
            } catch (RuntimeException e) {
                RegistroRed.log(ORIGEN, "Paquete UDP ignorado por error: " + e);
            }
        }
    }

    private void procesarPaqueteUdp(byte[] bufer, DatagramPacket paquete) {
        if (PerdidaSimulada.descartar()) {
            return;
        }
        PaquetesUdp.Entrada entrada = PaquetesUdp.leerEntrada(bufer, paquete.getLength());
        if (entrada == null || entrada.id() >= Config.MAX_JUGADORES) {
            return;
        }
        EntradaRemota remota = remotas.get(entrada.id());
        if (remota == null || remota.token != entrada.token()) {
            return;
        }
        if (remota.recibioAlguna && entrada.secuencia() <= remota.ultimaSecuencia) {
            remota.descartados++;
            return;
        }
        remota.recibioAlguna = true;
        remota.ultimaSecuencia = entrada.secuencia();
        SocketAddress origen = paquete.getSocketAddress();
        if (!origen.equals(remota.direccion)) {
            RegistroRed.log(ORIGEN, "UDP del jugador #" + entrada.id() + " desde " + origen);
            remota.direccion = origen;
        }
        remota.botones = entrada.botones();
        remota.ultimaLlegadaNanos = System.nanoTime();
    }

    // ------------------------------------------------------------------ hilo de simulación

    /**
     * Ciclo a TICKS_POR_SEGUNDO: mientras espera el próximo tick procesa los eventos TCP que lleguen, y en cada
     * tick avanza la carrera. Los ticks se programan sobre un horario fijo, así el promedio se mantiene en 60 por
     * segundo aunque el sistema operativo despierte al hilo un poco tarde.
     */
    private void correrSimulacion() {
        long siguiente = System.nanoTime();
        try {
            while (activo.get()) {
                long espera;
                while ((espera = siguiente - System.nanoTime()) > 0 && activo.get()) {
                    Evento evento = eventos.poll(espera, TimeUnit.NANOSECONDS);
                    if (evento != null) {
                        procesar(evento);
                    }
                }
                Evento evento;
                while ((evento = eventos.poll()) != null) {
                    procesar(evento);
                }
                if (!activo.get()) {
                    return;
                }
                simularTick();
                siguiente += NANOS_POR_TICK;
                if (System.nanoTime() - siguiente > MAX_ATRASO_NANOS) {
                    siguiente = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error e) {
            // Una partida con la simulación muerta no se puede continuar: se avisa a todos y se cierra.
            RegistroRed.log(ORIGEN, "Error inesperado en la simulación: " + e);
            e.printStackTrace();
            cerrar("Error interno del servidor (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void simularTick() {
        tick++;
        if (fase == Fase.GRILLA) {
            if (--ticksDeGrilla <= 0) {
                empezar(false);
            }
            return;
        }
        if (carrera == null || (fase != Fase.CUENTA_REGRESIVA && fase != Fase.CARRERA)) {
            return;
        }
        EstadoCarrera antes = carrera.estado();
        carrera.actualizar(armarEntradas(), PASO);
        avisarSiNoLlegaUdp();
        EstadoCarrera despues = carrera.estado();

        if (tick % TICKS_POR_ESTADO == 0 || despues != antes) {
            enviarEstado();
        }
        if (antes == EstadoCarrera.CUENTA_REGRESIVA && despues == EstadoCarrera.EN_CURSO) {
            fase = Fase.CARRERA;
            difundir(Protocolo.armar(Protocolo.LARGADA));
        } else if (despues == EstadoCarrera.TERMINADA && enClasificacion) {
            terminarClasificacion();
        } else if (despues == EstadoCarrera.TERMINADA) {
            fase = Fase.RESULTADOS;
            // Primero la tabla: TCP entrega en orden, así el cliente ya la tiene cuando pasa a los resultados.
            if (torneo != null) {
                torneo.sumarCarrera(carrera.resultados(), abandonaron());
                difundir(EstadoTorneo.armar(torneo));
            }
            difundir(Protocolo.armarResultados(carrera.resultados()));
        }
    }

    /** Los que se fueron de la partida sin completar la carrera: esa carrera no les suma puntos. */
    private Set<Integer> abandonaron() {
        Set<Integer> ids = new HashSet<>();
        for (ResultadoJugador resultado : carrera.resultados()) {
            if (!jugadores.containsKey(resultado.id()) && Float.isNaN(resultado.tiempoTotal())) {
                ids.add(resultado.id());
            }
        }
        return ids;
    }

    /** La clasificación terminó: se avisa la grilla y queda a la vista hasta que arranca sola la carrera. */
    private void terminarClasificacion() {
        List<Integer> orden = new ArrayList<>();
        for (ResultadoJugador resultado : carrera.resultados()) {
            orden.add(resultado.id());
        }
        ordenClasificacion = orden;
        fase = Fase.GRILLA;
        ticksDeGrilla = Config.SEGUNDOS_GRILLA * Config.TICKS_POR_SEGUNDO;
        difundir(Protocolo.armarGrilla(carrera.resultados()));
    }

    /**
     * Si a los TIMEOUT_RED segundos de empezar un jugador no mandó ninguna ENTRADA, casi seguro es un firewall que
     * bloquea el UDP: se avisa una sola vez en la consola del host.
     */
    private void avisarSiNoLlegaUdp() {
        if (tick - tickInicioCarrera < Config.TIMEOUT_RED * Config.TICKS_POR_SEGUNDO) {
            return;
        }
        for (Jugador j : jugadores.values()) {
            if (!j.esBot() && !j.avisoUdpDado && j.udp.ultimaLlegadaNanos == 0) {
                j.avisoUdpDado = true;
                RegistroRed.log(ORIGEN, "AVISO: no llegó ninguna ENTRADA UDP de " + j.nombre
                    + ". Probablemente el firewall bloquea el UDP " + puertoUdp() + " en esta PC");
            }
        }
    }

    /**
     * La entrada de cada auto: la de una persona es la última que llegó por UDP (si no llegó nada en el último segundo,
     * controles neutros), y la de un bot la decide su piloto. Para la {@link Carrera} son iguales.
     */
    private Map<Integer, EntradaAuto> armarEntradas() {
        long ahora = System.nanoTime();
        for (Jugador jugador : jugadores.values()) {
            EntradaAuto entrada = entradas.computeIfAbsent(jugador.id, id -> new EntradaAuto());
            if (jugador.esBot()) {
                Participante participante = carrera.participante(jugador.id);
                if (carrera.estado() == EstadoCarrera.EN_CURSO && !participante.terminado()) {
                    // En la clasificación no hay otros autos en la pista, ni siquiera para esquivarlos.
                    pilotosBot.get(jugador.id).decidir(participante, enClasificacion ? List.of(participante) : carrera.participantes(), entrada, PASO);
                }
                continue;
            }
            long llegada = jugador.udp.ultimaLlegadaNanos;
            boolean reciente = llegada != 0 && ahora - llegada <= TIMEOUT_ENTRADA_NANOS;
            PaquetesUdp.aEntrada(reciente ? jugador.udp.botones : 0, entrada);
        }
        return entradas;
    }

    /** Manda el mismo ESTADO a cada jugador cuya dirección UDP ya se conoce. */
    private void enviarEstado() {
        byte[] datos = PaquetesUdp.armarEstado(tick, carrera.foto(1f));
        for (Jugador jugador : jugadores.values()) {
            if (jugador.esBot()) {
                continue;
            }
            SocketAddress direccion = jugador.udp.direccion;
            if (direccion == null) {
                continue; // todavía no mandó ninguna ENTRADA
            }
            try {
                socketUdp.send(new DatagramPacket(datos, datos.length, direccion));
            } catch (IOException e) {
                RegistroRed.log(ORIGEN, "No se pudo mandar el ESTADO a " + jugador.nombre + ": " + e.getMessage());
            }
        }
    }

    private void procesar(Evento evento) {
        if (evento instanceof Desconectado d) {
            Jugador jugador = d.cliente().jugador;
            if (jugador != null) {
                RegistroRed.log(ORIGEN, "Se desconectó " + jugador.nombre + " (" + d.motivo() + ")");
                quitar(jugador);
            }
            conexiones.remove(d.cliente());
        } else if (evento instanceof Recibido r) {
            procesarMensaje(r.cliente(), r.mensaje());
        }
    }

    private void procesarMensaje(Cliente cliente, Mensaje mensaje) {
        Jugador jugador = cliente.jugador;
        if (jugador == null) {
            // Antes de UNIRSE no se acepta ningún otro mensaje.
            if (mensaje.es(Protocolo.UNIRSE)) {
                unir(cliente, mensaje);
            } else {
                RegistroRed.log(ORIGEN, "Se ignora " + mensaje.nombre() + " de una conexión que todavía no se unió");
            }
            return;
        }
        switch (mensaje.nombre()) {
            case Protocolo.ELEGIR_PILOTO:
                elegirPiloto(jugador, mensaje);
                break;
            case Protocolo.ELEGIR_CIRCUITO:
                elegirCircuito(jugador, mensaje);
                break;
            case Protocolo.ELEGIR_VUELTAS:
                elegirVueltas(jugador, mensaje);
                break;
            case Protocolo.ELEGIR_CLASIFICACION:
                elegirClasificacion(jugador, mensaje);
                break;
            case Protocolo.ELEGIR_MODO:
                elegirModo(jugador, mensaje);
                break;
            case Protocolo.AGREGAR_CIRCUITO:
                agregarCircuito(jugador, mensaje);
                break;
            case Protocolo.QUITAR_CIRCUITO:
                quitarCircuito(jugador, mensaje);
                break;
            case Protocolo.SIGUIENTE_CARRERA:
                siguienteCarrera(jugador);
                break;
            case Protocolo.AGREGAR_BOT:
                agregarBot(jugador, mensaje);
                break;
            case Protocolo.QUITAR_BOT:
                quitarBot(jugador, mensaje);
                break;
            case Protocolo.NIVEL_BOT:
                cambiarNivelBot(jugador, mensaje);
                break;
            case Protocolo.LISTO:
                marcarListo(jugador, mensaje);
                break;
            case Protocolo.INICIAR:
                iniciar(jugador);
                break;
            case Protocolo.REVANCHA:
                revancha(jugador);
                break;
            case Protocolo.SALIR:
                quitar(jugador);
                break;
            default:
                RegistroRed.log(ORIGEN, "Mensaje desconocido de " + jugador.nombre + ": " + mensaje.nombre());
                break;
        }
    }

    private void unir(Cliente cliente, Mensaje mensaje) {
        int version = mensaje.entero(0, -1);
        String nombre = mensaje.campo(1);
        if (version != Config.VERSION_PROTOCOLO) {
            rechazar(cliente, Protocolo.VERSION_DISTINTA,
                "Tu juego es de otra versión (" + version + ", la de la partida es " + Config.VERSION_PROTOCOLO + ")");
        } else if (fase != Fase.LOBBY) {
            rechazar(cliente, Protocolo.CARRERA_EN_CURSO, "La carrera ya empezó");
        } else if (cantidadDePersonas() >= Config.MAX_JUGADORES) {
            // Los bots no cuentan: una persona siempre tiene prioridad y, si hace falta, le saca el lugar a uno.
            rechazar(cliente, Protocolo.PARTIDA_LLENA, "La partida está llena");
        } else if (!Protocolo.nombreValido(nombre)) {
            rechazar(cliente, Protocolo.NOMBRE_INVALIDO,
                "El nombre debe tener de 1 a " + Config.LARGO_MAXIMO_NOMBRE + " letras, números o espacios");
        } else if (nombreEnUso(nombre)) {
            rechazar(cliente, Protocolo.NOMBRE_REPETIDO, "Ya hay un jugador con ese nombre");
        } else {
            aceptar(cliente, nombre);
        }
    }

    private void aceptar(Cliente cliente, String nombre) {
        boolean esHost = jugadores.isEmpty();
        if (jugadores.size() >= Config.MAX_JUGADORES) {
            sacarUltimoBot();
        }
        int id = primerIdLibre();
        EntradaRemota udp = new EntradaRemota(azar.nextInt());
        Jugador jugador = new Jugador(id, nombre, cliente, udp, primerPilotoLibre());
        jugadores.put(id, jugador);
        remotas.set(id, udp);
        cliente.jugador = jugador;
        if (esHost) {
            idHost = id;
        }
        RegistroRed.log(ORIGEN, nombre + " se unió con el id " + id + (esHost ? " (anfitrión)" : ""));
        enviar(cliente, Protocolo.armar(Protocolo.BIENVENIDA, id, udp.token, esHost ? 1 : 0, puertoUdp()));
        difundirLobby();
    }

    /** Un piloto solo se puede elegir si nadie más corre para su escudería (cada escudería tiene un color). */
    private void elegirPiloto(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Ya no se puede cambiar de piloto");
            return;
        }
        int piloto = mensaje.entero(0, -1);
        if (!Catalogo.pilotoValido(piloto)) {
            RegistroRed.log(ORIGEN, "Piloto inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        int escuderia = Catalogo.piloto(piloto).escuderia();
        Jugador botEnEsaEscuderia = null;
        for (Jugador otro : jugadores.values()) {
            if (otro != jugador && Catalogo.piloto(otro.piloto).escuderia() == escuderia) {
                if (!otro.esBot()) {
                    error(jugador.cliente, Protocolo.ESCUDERIA_OCUPADA, "Esa escudería ya la eligió otro jugador");
                    return;
                }
                botEnEsaEscuderia = otro;
            }
        }
        jugador.piloto = piloto;
        // Una persona le gana a un bot: el bot se pasa a la primera escudería libre.
        if (botEnEsaEscuderia != null) {
            botEnEsaEscuderia.cambiarPiloto(primerPilotoLibre());
        }
        difundirLobby();
    }

    /** Solo el host cambia el circuito, y solo en el lobby. */
    private void elegirCircuito(Jugador jugador, Mensaje mensaje) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede elegir el circuito, y solo desde el lobby");
            return;
        }
        int circuito = mensaje.entero(0, -1);
        if (!Catalogo.circuitoValido(circuito)) {
            RegistroRed.log(ORIGEN, "Circuito inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        circuitoElegido = circuito;
        difundirLobby();
    }

    /** Solo el host cambia las vueltas, y solo en el lobby. */
    private void elegirVueltas(Jugador jugador, Mensaje mensaje) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede elegir las vueltas, y solo desde el lobby");
            return;
        }
        int vueltas = mensaje.entero(0, -1);
        if (vueltas < Config.MIN_VUELTAS || vueltas > Config.MAX_VUELTAS) {
            RegistroRed.log(ORIGEN, "Vueltas inválidas de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        vueltasElegidas = vueltas;
        difundirLobby();
    }

    private void elegirClasificacion(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "elegir si hay clasificación")) {
            return;
        }
        hayClasificacion = mensaje.campo(0).equals("1");
        difundirLobby();
    }

    /** Al pasar a torneo por primera vez, el calendario arranca con el circuito elegido: nunca queda vacío. */
    private void elegirModo(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "elegir el modo")) {
            return;
        }
        modoTorneo = mensaje.campo(0).equals("1");
        if (modoTorneo && calendario.isEmpty()) {
            calendario.add(circuitoElegido);
        }
        difundirLobby();
    }

    /** Se puede repetir un circuito. */
    private void agregarCircuito(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "armar el calendario")) {
            return;
        }
        int circuito = mensaje.entero(0, -1);
        if (!Catalogo.circuitoValido(circuito)) {
            RegistroRed.log(ORIGEN, "Circuito inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        if (calendario.size() >= Config.MAX_CARRERAS_TORNEO) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "El calendario admite hasta " + Config.MAX_CARRERAS_TORNEO + " carreras");
            return;
        }
        calendario.add(circuito);
        difundirLobby();
    }

    /** La última carrera no se puede sacar: un torneo necesita al menos una. */
    private void quitarCircuito(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "armar el calendario")) {
            return;
        }
        int lugar = mensaje.entero(0, -1);
        if (lugar < 0 || lugar >= calendario.size() || calendario.size() == 1) {
            RegistroRed.log(ORIGEN, "QUITAR_CIRCUITO inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        calendario.remove(lugar);
        difundirLobby();
    }

    private void agregarBot(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "agregar bots")) {
            return;
        }
        NivelBot nivel = NivelBot.desde(mensaje.entero(0, -1));
        if (nivel == null) {
            RegistroRed.log(ORIGEN, "Nivel de bot inválido: " + mensaje.campo(0));
            return;
        }
        if (jugadores.size() >= Config.MAX_JUGADORES) {
            error(jugador.cliente, Protocolo.PARTIDA_LLENA, "Ya están los " + Config.MAX_JUGADORES + " autos");
            return;
        }
        Jugador bot = Jugador.bot(primerIdLibre(), primerPilotoLibre(), nivel);
        jugadores.put(bot.id, bot);
        difundirLobby();
    }

    private void quitarBot(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "quitar bots")) {
            return;
        }
        Jugador bot = jugadores.get(mensaje.entero(0, -1));
        // El id viene de afuera: este mensaje nunca puede echar a una persona.
        if (bot == null || !bot.esBot()) {
            RegistroRed.log(ORIGEN, "QUITAR_BOT de algo que no es un bot: " + mensaje.campo(0));
            return;
        }
        jugadores.remove(bot.id);
        difundirLobby();
    }

    private void cambiarNivelBot(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "cambiar el nivel de los bots")) {
            return;
        }
        Jugador bot = jugadores.get(mensaje.entero(0, -1));
        NivelBot nivel = NivelBot.desde(mensaje.entero(1, -1));
        if (bot == null || !bot.esBot() || nivel == null) {
            RegistroRed.log(ORIGEN, "NIVEL_BOT inválido: " + mensaje.campo(0) + ", " + mensaje.campo(1));
            return;
        }
        bot.nivelBot = nivel;
        difundirLobby();
    }

    private boolean esHostEnLobby(Jugador jugador, String accion) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede " + accion + ", y solo desde el lobby");
            return false;
        }
        return true;
    }

    private void sacarUltimoBot() {
        Jugador ultimo = null;
        for (Jugador j : jugadores.values()) {
            if (j.esBot()) {
                ultimo = j;
            }
        }
        if (ultimo != null) {
            jugadores.remove(ultimo.id);
        }
    }

    private int cantidadDePersonas() {
        int personas = 0;
        for (Jugador j : jugadores.values()) {
            personas += j.esBot() ? 0 : 1;
        }
        return personas;
    }

    /** Primero las personas y después los bots: es el orden del lobby y, si no hay clasificación, el de la largada. */
    private List<Jugador> enOrdenDeGrilla() {
        List<Jugador> orden = new ArrayList<>(jugadores.values());
        orden.sort(Comparator.comparing(Jugador::esBot));
        return orden;
    }

    private void marcarListo(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "La partida ya empezó");
            return;
        }
        jugador.listo = mensaje.campo(0).equals("1");
        difundirLobby();
    }

    /** Valida el pedido del host y arranca la clasificación, si la hay, o la carrera. */
    private void iniciar(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede iniciar, y solo desde el lobby");
            return;
        }
        if (!estadoLobby().sePuedeIniciar()) {
            error(jugador.cliente, Protocolo.FALTAN_JUGADORES,
                "Hacen falta al menos " + Config.MIN_JUGADORES + " autos y que todos estén listos");
            return;
        }
        int primero = modoTorneo ? calendario.get(0) : circuitoElegido;
        if (!cargarCircuito(primero, jugador)) {
            return;
        }
        torneo = null;
        if (modoTorneo) {
            torneo = new Torneo(calendario);
            for (Jugador j : enOrdenDeGrilla()) {
                torneo.inscribir(j.id, j.nombre);
            }
            difundir(EstadoTorneo.armar(torneo));
        }
        empezarCarreraEn(primero);
    }

    /**
     * Pasa a la próxima carrera del torneo, con los que sigan en la partida. La pide el host desde los resultados, para que
     * todos tengan tiempo de ver la tabla. El circuito se carga antes de avanzar: si falla, se puede volver a pedir.
     */
    private void siguienteCarrera(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.RESULTADOS || torneo == null || !torneo.hayMasCarreras()) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede largar la carrera siguiente, y solo desde los resultados");
            return;
        }
        int siguiente = torneo.circuitoSiguiente();
        if (!cargarCircuito(siguiente, jugador)) {
            return;
        }
        torneo.pasarALaSiguiente();
        difundir(EstadoTorneo.armar(torneo));
        empezarCarreraEn(siguiente);
    }

    /** Arranca una carrera nueva, con su clasificación si la hay. La grilla de la anterior no cuenta. */
    private void empezarCarreraEn(int circuito) {
        circuitoEnCurso = circuito;
        ordenClasificacion = List.of();
        empezar(hayClasificacion);
    }

    /**
     * Arma la clasificación o la carrera con los jugadores que sigan en la partida. El circuito ya está cargado y
     * comprobado: se pidió antes de llegar acá, y una clasificación y su carrera usan el mismo.
     */
    private void empezar(boolean clasificacion) {
        Circuito circuito = circuitoCargado;
        enClasificacion = clasificacion;
        int vueltas = clasificacion ? Config.VUELTAS_CLASIFICACION : vueltasElegidas;
        carrera = new Carrera(circuito, vueltas, clasificacion);
        Map<NivelBot, RecorridoBot> recorridos = new EnumMap<>(NivelBot.class);
        pilotosBot.clear();
        List<Integer> grilla = new ArrayList<>();
        for (Jugador j : ordenDeLargada()) {
            carrera.agregarAuto(j.id, j.nombre);
            grilla.add(j.id);
            if (j.esBot()) {
                // El recorrido solo depende del circuito y del nivel: los bots del mismo nivel lo comparten.
                RecorridoBot recorrido = recorridos.computeIfAbsent(j.nivelBot, nivel -> new RecorridoBot(circuito, nivel));
                pilotosBot.put(j.id, new PilotoBot(recorrido, j.nivelBot, azar.nextLong()));
            }
        }
        entradas.clear();
        tickInicioCarrera = tick;
        fase = Fase.CUENTA_REGRESIVA;
        // La cuenta regresiva la lleva la Carrera: cuando termina, simularTick() manda la LARGADA.
        difundir(Protocolo.armarCuenta(Catalogo.circuito(circuitoEnCurso).archivo(), vueltas, Config.SEGUNDOS_CUENTA_REGRESIVA,
            clasificacion, clasificacion ? List.of() : grilla));
    }

    /** El orden de la clasificación, si la hubo, sin los que se fueron; si no, personas primero y después bots. */
    private List<Jugador> ordenDeLargada() {
        if (ordenClasificacion.isEmpty()) {
            return enOrdenDeGrilla();
        }
        List<Jugador> orden = new ArrayList<>();
        for (int id : ordenClasificacion) {
            Jugador jugador = jugadores.get(id);
            if (jugador != null) {
                orden.add(jugador);
            }
        }
        return orden;
    }

    /** Deja listo ese circuito. Si no se puede cargar, le avisa a quien lo pidió y devuelve false. */
    private boolean cargarCircuito(int indice, Jugador quienPide) {
        String archivo = Catalogo.circuito(indice).archivo();
        try {
            if (!archivo.equals(archivoCargado)) {
                circuitoCargado = proveedor.cargar(archivo);
                archivoCargado = archivo;
            }
            return true;
        } catch (Exception e) {
            RegistroRed.log(ORIGEN, "No se pudo cargar el circuito " + archivo + ": " + e);
            error(quienPide.cliente, Protocolo.CIRCUITO_NO_CARGO, "No se pudo cargar el circuito " + archivo);
            return false;
        }
    }

    /** Vuelve al lobby con los mismos jugadores (los que siguen conectados), todos sin "listo". En un torneo, solo al final. */
    private void revancha(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.RESULTADOS || (torneo != null && torneo.hayMasCarreras())) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede pedir revancha, y solo en los resultados finales");
            return;
        }
        carrera = null;
        torneo = null;
        enClasificacion = false;
        ordenClasificacion = List.of();
        fase = Fase.LOBBY;
        for (Jugador j : jugadores.values()) {
            j.listo = j.esBot();
        }
        difundirLobby();
    }

    /**
     * Saca a un jugador de la partida. Si era el host, la partida termina para todos. En el lobby se avisa con un
     * LOBBY nuevo; con la carrera empezada su auto sale de la pista y se avisa con SALIO.
     */
    private void quitar(Jugador jugador) {
        if (jugadores.remove(jugador.id) == null) {
            return;
        }
        remotas.set(jugador.id, null);
        jugador.cliente.jugador = null;
        jugador.cliente.conexion.cerrar();
        if (carrera != null) {
            carrera.marcarDesconectado(jugador.id);
        }
        if (jugador.id == idHost) {
            cerrar("El anfitrión abandonó la partida");
        } else if (fase == Fase.LOBBY) {
            difundirLobby();
        } else {
            difundir(Protocolo.armar(Protocolo.SALIO, jugador.id));
        }
    }

    // ------------------------------------------------------------------ envío TCP

    private EstadoLobby estadoLobby() {
        List<JugadorLobby> lista = new ArrayList<>();
        for (Jugador j : enOrdenDeGrilla()) {
            lista.add(new JugadorLobby(j.id, j.nombre, j.piloto, j.listo, j.esBot() ? j.nivelBot.ordinal() : -1));
        }
        return new EstadoLobby(idHost, circuitoElegido, vueltasElegidas, hayClasificacion, modoTorneo, List.copyOf(calendario), lista);
    }

    private void difundirLobby() {
        difundir(estadoLobby().aLinea());
    }

    private void difundir(String linea) {
        for (Jugador jugador : jugadores.values()) {
            if (!jugador.esBot()) {
                enviar(jugador.cliente, linea);
            }
        }
    }

    private void error(Cliente cliente, String codigo, String detalle) {
        enviar(cliente, Protocolo.armar(Protocolo.ERROR, codigo, Protocolo.limpiarTexto(detalle)));
    }

    /** Manda el ERROR y cierra la conexión, para que el cliente pueda leer el motivo antes de que se corte. */
    private void rechazar(Cliente cliente, String codigo, String detalle) {
        error(cliente, codigo, detalle);
        cliente.conexion.cerrar();
    }

    private void enviar(Cliente cliente, String linea) {
        try {
            cliente.conexion.enviar(linea);
            RegistroRed.log(ORIGEN, "-> " + cliente.nombreParaLog() + ": " + linea);
        } catch (IOException e) {
            // El lector de ese cliente va a ver la conexión rota y lo va a dar de baja.
            RegistroRed.log(ORIGEN, "No se pudo enviar a " + cliente.nombreParaLog() + ": " + e.getMessage());
            cliente.conexion.cerrar();
        }
    }

    private int primerIdLibre() {
        int id = 0;
        while (jugadores.containsKey(id)) {
            id++;
        }
        return id;
    }

    /** El primer piloto de la primera escudería que nadie eligió, así nadie queda sin piloto. */
    private int primerPilotoLibre() {
        for (int escuderia = 0; escuderia < Catalogo.ESCUDERIAS.size(); escuderia++) {
            boolean libre = true;
            for (Jugador j : jugadores.values()) {
                libre &= Catalogo.piloto(j.piloto).escuderia() != escuderia;
            }
            if (libre) {
                return escuderia * 2;
            }
        }
        return 0; // no pasa: hay más escuderías que jugadores máximos
    }

    private boolean nombreEnUso(String nombre) {
        for (Jugador j : jugadores.values()) {
            if (!j.esBot() && j.nombre.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ cierre

    /** Cierra la partida (lo llama el host al salir). Se puede llamar desde cualquier hilo y más de una vez. */
    public void cerrar() {
        cerrar("El anfitrión cerró la partida");
    }

    /**
     * Avisa con CERRADA, cierra todos los sockets (eso destraba los hilos que están esperando en accept, receive o
     * readLine) y detiene la simulación. Solo usa la lista de conexiones, que es segura entre hilos, y no el estado
     * de la partida.
     */
    private void cerrar(String motivo) {
        if (!activo.compareAndSet(true, false)) {
            return;
        }
        RegistroRed.log(ORIGEN, "Cerrando la partida: " + motivo);
        String linea = Protocolo.armar(Protocolo.CERRADA, Protocolo.limpiarTexto(motivo));
        for (Cliente cliente : conexiones) {
            try {
                cliente.conexion.enviar(linea);
            } catch (IOException e) {
                // Si no se puede avisar, igual se cierra.
            }
            cliente.conexion.cerrar();
        }
        try {
            socketTcp.close();
        } catch (IOException e) {
            // Ya estaba cerrado.
        }
        socketUdp.close();
    }
}
