package ar.edu.et35.racing.red.servidor;

import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.ModoGrilla;
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
import java.util.Collections;
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

public class ServidorPartida {
    private static final String ORIGEN = "SERVIDOR";
    private static final long NANOS_POR_TICK = 1_000_000_000L / Config.TICKS_POR_SEGUNDO;
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    private static final int TICKS_POR_ESTADO = Math.max(1, Config.TICKS_POR_SEGUNDO / Config.ENVIOS_ESTADO_POR_SEGUNDO);
    private static final long MAX_ATRASO_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long TIMEOUT_ENTRADA_NANOS = TimeUnit.MILLISECONDS.toNanos(Config.TIMEOUT_ENTRADA_UDP_MS);

    private enum Fase { LOBBY, CUENTA_REGRESIVA, CARRERA, GRILLA, RESULTADOS }

    private static final class Cliente {
        final ConexionTcp conexion;
        volatile Jugador jugador;

        Cliente(ConexionTcp conexion) {
            this.conexion = conexion;
        }

        String nombreParaLog() {
            Jugador j = jugador;
            return j != null ? "#" + j.id + " " + j.nombre : conexion.direccionRemota();
        }
    }

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

        void cambiarPiloto(int nuevo) {
            piloto = nuevo;
            if (esBot()) {
                nombre = Catalogo.piloto(nuevo).apellido();
            }
        }
    }

    private static final class EntradaRemota {
        final int token;
        volatile int botones;
        volatile long ultimaLlegadaNanos;
        volatile SocketAddress direccion;
        boolean recibioAlguna;
        int ultimaSecuencia;
        int descartados;

        EntradaRemota(int token) {
            this.token = token;
        }
    }

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
    private final ProveedorCircuitos proveedor;
    private String archivoCargado;
    private Circuito circuitoCargado;
    private final BlockingQueue<Evento> eventos = new LinkedBlockingQueue<>();
    private final List<Cliente> conexiones = new CopyOnWriteArrayList<>();
    private final AtomicReferenceArray<EntradaRemota> remotas = new AtomicReferenceArray<>(Config.MAX_JUGADORES);
    private final AtomicBoolean activo = new AtomicBoolean(true);
    private final SecureRandom azar = new SecureRandom();

    private final Map<Integer, Jugador> jugadores = new LinkedHashMap<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();
    private final Map<Integer, PilotoBot> pilotosBot = new HashMap<>();
    private Fase fase = Fase.LOBBY;
    private int idHost = -1;
    private int circuitoElegido;
    private int vueltasElegidas = Config.VUELTAS;
    private ModoGrilla modoGrilla = ModoGrilla.MANUAL;
    private final List<Integer> ordenGrilla = new ArrayList<>();
    private boolean modoTorneo;
    private final List<Integer> calendario = new ArrayList<>();
    private Torneo torneo;
    private int circuitoEnCurso;
    private boolean enClasificacion;
    private List<Integer> ordenClasificacion = List.of();
    private int ticksDeGrilla;
    private Carrera carrera;
    private int tickInicioCarrera;
    private int tick;

    private final int capacidad;

    private ServidorPartida(ServerSocket socketTcp, DatagramSocket socketUdp, ProveedorCircuitos proveedor, int capacidad) {
        this.capacidad = capacidad;
        this.socketTcp = socketTcp;
        this.socketUdp = socketUdp;
        this.proveedor = proveedor;
        int inicial = Catalogo.indiceDeCircuito(Config.CIRCUITO);
        this.circuitoElegido = inicial >= 0 ? inicial : 0;
    }

    public static ServidorPartida crear(int puertoTcp, int puertoUdp, int capacidad, ProveedorCircuitos proveedor) throws IOException {
        ServerSocket tcp = new ServerSocket(puertoTcp);
        DatagramSocket udp;
        try {
            udp = new DatagramSocket(puertoUdp);
        } catch (IOException e) {
            tcp.close();
            throw e;
        }
        ServidorPartida servidor = new ServidorPartida(tcp, udp, proveedor,
            Math.max(Config.MIN_JUGADORES, Math.min(Config.MAX_JUGADORES, capacidad)));
        servidor.iniciarHilos();
        RegistroRed.log(ORIGEN, "Escuchando TCP en el puerto " + servidor.puerto() + " y UDP en el " + servidor.puertoUdp());
        return servidor;
    }

    public static ServidorPartida crear(int puertoTcp, int puertoUdp, ProveedorCircuitos proveedor) throws IOException {
        return crear(puertoTcp, puertoUdp, Config.MAX_JUGADORES, proveedor);
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
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

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
            RegistroRed.log(ORIGEN, "Error inesperado con " + cliente.nombreParaLog() + ": " + e);
            motivo = "error interno";
        } finally {
            cliente.conexion.cerrar();
            eventos.add(new Desconectado(cliente, motivo));
        }
    }

    private void recibirUdp() {
        byte[] bufer = new byte[PaquetesUdp.LARGO_MAXIMO + 64];
        DatagramPacket paquete = new DatagramPacket(bufer, bufer.length);
        while (activo.get()) {
            try {
                paquete.setLength(bufer.length);
                socketUdp.receive(paquete);
            } catch (IOException e) {
                return;
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
            if (torneo != null) {
                torneo.sumarCarrera(carrera.resultados(), abandonaron());
                difundir(EstadoTorneo.armar(torneo));
            }
            difundir(Protocolo.armarResultados(carrera.resultados()));
        }
    }

    private Set<Integer> abandonaron() {
        Set<Integer> ids = new HashSet<>();
        for (ResultadoJugador resultado : carrera.resultados()) {
            if (!jugadores.containsKey(resultado.id()) && Float.isNaN(resultado.tiempoTotal())) {
                ids.add(resultado.id());
            }
        }
        return ids;
    }

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

    private Map<Integer, EntradaAuto> armarEntradas() {
        long ahora = System.nanoTime();
        for (Jugador jugador : jugadores.values()) {
            EntradaAuto entrada = entradas.computeIfAbsent(jugador.id, id -> new EntradaAuto());
            if (jugador.esBot()) {
                Participante participante = carrera.participante(jugador.id);
                if (carrera.estado() == EstadoCarrera.EN_CURSO && !participante.terminado()) {
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

    private void enviarEstado() {
        byte[] datos = PaquetesUdp.armarEstado(tick, carrera.foto(1f));
        for (Jugador jugador : jugadores.values()) {
            if (jugador.esBot()) {
                continue;
            }
            SocketAddress direccion = jugador.udp.direccion;
            if (direccion == null) {
                continue;
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
            case Protocolo.ELEGIR_GRILLA:
                elegirGrilla(jugador, mensaje);
                break;
            case Protocolo.MOVER_GRILLA:
                moverEnGrilla(jugador, mensaje);
                break;
            case Protocolo.GRILLA_AL_AZAR:
                sortearGrilla(jugador);
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
        } else if (cantidadDePersonas() >= Math.min(capacidad, Config.MAX_PERSONAS)) {
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
        if (jugadores.size() >= capacidad) {
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
        Jugador duenio = duenioDelPiloto(piloto);
        if (duenio == jugador) {
            return;
        }
        if (duenio != null && !duenio.esBot()) {
            error(jugador.cliente, Protocolo.PILOTO_OCUPADO, "Ese piloto ya lo eligió otro jugador");
            return;
        }
        int anterior = jugador.piloto;
        jugador.piloto = piloto;
        if (duenio != null) {
            duenio.cambiarPiloto(anterior);
        }
        difundirLobby();
    }

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

    private void elegirGrilla(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "elegir cómo se arma la grilla")) {
            return;
        }
        int numero = mensaje.entero(0, -1);
        if (numero < 0 || numero >= ModoGrilla.values().length) {
            RegistroRed.log(ORIGEN, "Modo de grilla inválido de " + jugador.nombre + ": " + mensaje.campo(0));
            return;
        }
        modoGrilla = ModoGrilla.desde(numero);
        difundirLobby();
    }

    private void moverEnGrilla(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "mover la grilla") || !grillaAMano(jugador)) {
            return;
        }
        sincronizarGrilla();
        int desde = ordenGrilla.indexOf(mensaje.entero(0, -1));
        int lugares = mensaje.entero(1, 0);
        int hasta = desde + lugares;
        if (desde < 0 || Math.abs(lugares) != 1 || hasta < 0 || hasta >= ordenGrilla.size()) {
            RegistroRed.log(ORIGEN, "MOVER_GRILLA inválido de " + jugador.nombre + ": " + mensaje.campo(0) + ", " + mensaje.campo(1));
            return;
        }
        Collections.swap(ordenGrilla, desde, hasta);
        difundirLobby();
    }

    private void sortearGrilla(Jugador jugador) {
        if (!esHostEnLobby(jugador, "sortear la grilla") || !grillaAMano(jugador)) {
            return;
        }
        sincronizarGrilla();
        Collections.shuffle(ordenGrilla, azar);
        difundirLobby();
    }

    private boolean grillaAMano(Jugador jugador) {
        if (modoGrilla != ModoGrilla.MANUAL) {
            error(jugador.cliente, Protocolo.NO_PERMITIDO, "La grilla solo se arma a mano cuando no hay clasificación ni sorteo");
            return false;
        }
        return true;
    }

    private void sincronizarGrilla() {
        ordenGrilla.removeIf(id -> !jugadores.containsKey(id));
        for (Jugador j : jugadores.values()) {
            if (ordenGrilla.contains(j.id)) {
                continue;
            }
            int lugar = ordenGrilla.size();
            if (!j.esBot()) {
                lugar = 0;
                for (int i = 0; i < ordenGrilla.size(); i++) {
                    lugar = jugadores.get(ordenGrilla.get(i)).esBot() ? lugar : i + 1;
                }
            }
            ordenGrilla.add(lugar, j.id);
        }
    }

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
        if (jugadores.size() >= capacidad) {
            error(jugador.cliente, Protocolo.PARTIDA_LLENA, "La sala es de " + capacidad + " autos y ya están todos");
            return;
        }
        int piloto = mensaje.entero(1, -1);
        if (piloto == -1) {
            piloto = primerPilotoLibre();
        } else if (!Catalogo.pilotoValido(piloto) || duenioDelPiloto(piloto) != null) {
            error(jugador.cliente, Protocolo.PILOTO_OCUPADO, "Ese piloto ya está en la sala");
            return;
        }
        Jugador bot = Jugador.bot(primerIdLibre(), piloto, nivel);
        jugadores.put(bot.id, bot);
        difundirLobby();
    }

    private void quitarBot(Jugador jugador, Mensaje mensaje) {
        if (!esHostEnLobby(jugador, "quitar bots")) {
            return;
        }
        Jugador bot = jugadores.get(mensaje.entero(0, -1));
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

    private List<Jugador> enOrdenDeGrilla() {
        sincronizarGrilla();
        List<Jugador> orden = new ArrayList<>();
        for (int id : ordenGrilla) {
            orden.add(jugadores.get(id));
        }
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

    private void empezarCarreraEn(int circuito) {
        circuitoEnCurso = circuito;
        ordenClasificacion = List.of();
        if (modoGrilla == ModoGrilla.AL_AZAR) {
            sincronizarGrilla();
            Collections.shuffle(ordenGrilla, azar);
        }
        empezar(modoGrilla == ModoGrilla.CLASIFICACION);
    }

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
                RecorridoBot recorrido = recorridos.computeIfAbsent(j.nivelBot, nivel -> new RecorridoBot(circuito, nivel));
                pilotosBot.put(j.id, new PilotoBot(recorrido, j.nivelBot, azar.nextLong()));
            }
        }
        entradas.clear();
        tickInicioCarrera = tick;
        fase = Fase.CUENTA_REGRESIVA;
        difundir(Protocolo.armarCuenta(Catalogo.circuito(circuitoEnCurso).archivo(), vueltas, Config.SEGUNDOS_CUENTA_REGRESIVA,
            clasificacion, clasificacion ? List.of() : grilla));
    }

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

    private EstadoLobby estadoLobby() {
        List<JugadorLobby> lista = new ArrayList<>();
        for (Jugador j : enOrdenDeGrilla()) {
            lista.add(new JugadorLobby(j.id, j.nombre, j.piloto, j.listo, j.esBot() ? j.nivelBot.ordinal() : -1));
        }
        return new EstadoLobby(idHost, circuitoElegido, vueltasElegidas, modoGrilla, modoTorneo, List.copyOf(calendario), capacidad, lista);
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

    private void rechazar(Cliente cliente, String codigo, String detalle) {
        error(cliente, codigo, detalle);
        cliente.conexion.cerrar();
    }

    private void enviar(Cliente cliente, String linea) {
        try {
            cliente.conexion.enviar(linea);
            RegistroRed.log(ORIGEN, "-> " + cliente.nombreParaLog() + ": " + linea);
        } catch (IOException e) {
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

    private Jugador duenioDelPiloto(int piloto) {
        for (Jugador j : jugadores.values()) {
            if (j.piloto == piloto) {
                return j;
            }
        }
        return null;
    }

    private int primerPilotoLibre() {
        for (int piloto = 0; piloto < Catalogo.PILOTOS.size(); piloto += 2) {
            if (duenioDelPiloto(piloto) == null && duenioDelPiloto(piloto + 1) == null) {
                return piloto;
            }
        }
        for (int piloto = 0; piloto < Catalogo.PILOTOS.size(); piloto++) {
            if (duenioDelPiloto(piloto) == null) {
                return piloto;
            }
        }
        return 0;
    }

    private boolean nombreEnUso(String nombre) {
        for (Jugador j : jugadores.values()) {
            if (!j.esBot() && j.nombre.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    public void cerrar() {
        cerrar("El anfitrión cerró la partida");
    }

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
            }
            cliente.conexion.cerrar();
        }
        try {
            socketTcp.close();
        } catch (IOException e) {
        }
        socketUdp.close();
    }
}
