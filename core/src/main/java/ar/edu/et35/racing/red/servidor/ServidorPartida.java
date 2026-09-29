package ar.edu.et35.racing.red.servidor;

import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.red.ConexionTcp;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private enum Fase { LOBBY, CUENTA_REGRESIVA, CARRERA, RESULTADOS }

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

    /** Un jugador aceptado. Solo lo modifica el hilo de simulación. */
    private static final class Jugador {
        final int id;
        final String nombre;
        final Cliente cliente;
        final EntradaRemota udp;
        int auto;
        boolean listo;
        boolean avisoUdpDado;

        Jugador(int id, String nombre, Cliente cliente, EntradaRemota udp, int auto) {
            this.id = id;
            this.nombre = nombre;
            this.cliente = cliente;
            this.udp = udp;
            this.auto = auto;
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

    private interface Evento {
    }

    private record Recibido(Cliente cliente, Mensaje mensaje) implements Evento {
    }

    private record Desconectado(Cliente cliente, String motivo) implements Evento {
    }

    private final ServerSocket socketTcp;
    private final DatagramSocket socketUdp;
    /** Se carga en el hilo de render del host y se pasa en el constructor: los hilos del servidor solo lo leen. */
    private final Circuito circuito;
    private final BlockingQueue<Evento> eventos = new LinkedBlockingQueue<>();
    private final List<Cliente> conexiones = new CopyOnWriteArrayList<>();
    /** Por id de jugador: lo comparten el receptor UDP (busca el token) y la simulación (crea y borra). */
    private final AtomicReferenceArray<EntradaRemota> remotas = new AtomicReferenceArray<>(Config.MAX_JUGADORES);
    private final AtomicBoolean activo = new AtomicBoolean(true);
    private final SecureRandom azar = new SecureRandom();

    // Estado de la partida: solo lo toca el hilo de simulación
    private final Map<Integer, Jugador> jugadores = new LinkedHashMap<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();
    private Fase fase = Fase.LOBBY;
    private int idHost = -1;
    private Carrera carrera;
    private int tickInicioCarrera;
    /** Número de tick de la simulación: nunca vuelve atrás, ni entre carreras. Es la secuencia del ESTADO. */
    private int tick;

    private ServidorPartida(ServerSocket socketTcp, DatagramSocket socketUdp, Circuito circuito) {
        this.socketTcp = socketTcp;
        this.socketUdp = socketUdp;
        this.circuito = circuito;
    }

    /**
     * Crea el servidor y lo deja escuchando.
     *
     * @param puertoTcp puerto TCP; 0 deja que el sistema elija uno libre (se usa en las pruebas)
     * @param puertoUdp puerto UDP; 0 igual
     * @param circuito  el circuito de la carrera, ya cargado (en el hilo de render, porque carga texturas)
     * @throws IOException si alguno de los puertos ya está ocupado (por ejemplo, otra partida creada en esta PC)
     */
    public static ServidorPartida crear(int puertoTcp, int puertoUdp, Circuito circuito) throws IOException {
        ServerSocket tcp = new ServerSocket(puertoTcp);
        DatagramSocket udp;
        try {
            udp = new DatagramSocket(puertoUdp);
        } catch (IOException e) {
            tcp.close();
            throw e;
        }
        ServidorPartida servidor = new ServidorPartida(tcp, udp, circuito);
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
        } else if (despues == EstadoCarrera.TERMINADA) {
            fase = Fase.RESULTADOS;
            difundir(Protocolo.armarResultados(carrera.resultados()));
        }
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
            if (!j.avisoUdpDado && j.udp.ultimaLlegadaNanos == 0) {
                j.avisoUdpDado = true;
                RegistroRed.log(ORIGEN, "AVISO: no llegó ninguna ENTRADA UDP de " + j.nombre
                    + ". Probablemente el firewall bloquea el UDP " + puertoUdp() + " en esta PC");
            }
        }
    }

    /** La última entrada de cada jugador; si no llegó nada en el último segundo, controles neutros. */
    private Map<Integer, EntradaAuto> armarEntradas() {
        long ahora = System.nanoTime();
        for (Jugador jugador : jugadores.values()) {
            EntradaAuto entrada = entradas.computeIfAbsent(jugador.id, id -> new EntradaAuto());
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
            case Protocolo.ELEGIR_AUTO:
                elegirAuto(jugador, mensaje);
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
        } else if (jugadores.size() >= Config.MAX_JUGADORES) {
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
        int id = primerIdLibre();
        EntradaRemota udp = new EntradaRemota(azar.nextInt());
        Jugador jugador = new Jugador(id, nombre, cliente, udp, primerAutoLibre());
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

    private void elegirAuto(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Ya no se puede cambiar de auto");
            return;
        }
        int auto = mensaje.entero(0, -1);
        if (auto < 0 || auto >= Config.MAX_JUGADORES) {
            RegistroRed.log(ORIGEN, "Auto inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        for (Jugador otro : jugadores.values()) {
            if (otro != jugador && otro.auto == auto) {
                error(jugador.cliente, Protocolo.AUTO_OCUPADO, "Ese auto ya lo eligió otro jugador");
                return;
            }
        }
        jugador.auto = auto;
        difundirLobby();
    }

    private void marcarListo(Jugador jugador, Mensaje mensaje) {
        if (fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "La partida ya empezó");
            return;
        }
        jugador.listo = mensaje.campo(0).equals("1");
        difundirLobby();
    }

    /** Arma la carrera con los jugadores del lobby, en el orden del lobby (que es el de la grilla de largada). */
    private void iniciar(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.LOBBY) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede iniciar, y solo desde el lobby");
            return;
        }
        if (!estadoLobby().sePuedeIniciar()) {
            error(jugador.cliente, Protocolo.FALTAN_JUGADORES,
                "Hacen falta al menos " + Config.MIN_JUGADORES + " jugadores y que todos estén listos");
            return;
        }
        carrera = new Carrera(circuito, Config.VUELTAS);
        for (Jugador j : jugadores.values()) {
            carrera.agregarAuto(j.id, j.nombre);
        }
        entradas.clear();
        tickInicioCarrera = tick;
        fase = Fase.CUENTA_REGRESIVA;
        // La cuenta regresiva la lleva la Carrera: cuando termina, simularTick() manda la LARGADA.
        difundir(Protocolo.armar(Protocolo.CUENTA_REGRESIVA, Config.CIRCUITO, Config.VUELTAS,
            Config.SEGUNDOS_CUENTA_REGRESIVA));
    }

    /** Vuelve al lobby con los mismos jugadores (los que siguen conectados), todos sin "listo". */
    private void revancha(Jugador jugador) {
        if (jugador.id != idHost || fase != Fase.RESULTADOS) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "Solo el anfitrión puede pedir revancha, y solo en los resultados");
            return;
        }
        carrera = null;
        fase = Fase.LOBBY;
        for (Jugador j : jugadores.values()) {
            j.listo = false;
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
        for (Jugador j : jugadores.values()) {
            lista.add(new JugadorLobby(j.id, j.nombre, j.auto, j.listo));
        }
        return new EstadoLobby(idHost, lista);
    }

    private void difundirLobby() {
        difundir(estadoLobby().aLinea());
    }

    private void difundir(String linea) {
        for (Jugador jugador : jugadores.values()) {
            enviar(jugador.cliente, linea);
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

    private int primerAutoLibre() {
        for (int auto = 0; auto < Config.MAX_JUGADORES; auto++) {
            boolean libre = true;
            for (Jugador j : jugadores.values()) {
                libre &= j.auto != auto;
            }
            if (libre) {
                return auto;
            }
        }
        return 0; // no pasa: hay tantos autos como jugadores máximos
    }

    private boolean nombreEnUso(String nombre) {
        for (Jugador j : jugadores.values()) {
            if (j.nombre.equalsIgnoreCase(nombre)) {
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
